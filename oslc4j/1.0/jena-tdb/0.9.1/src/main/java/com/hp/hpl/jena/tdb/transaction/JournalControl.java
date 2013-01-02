/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hp.hpl.jena.tdb.transaction;

import static com.hp.hpl.jena.tdb.sys.SystemTDB.errlog ;
import static com.hp.hpl.jena.tdb.sys.SystemTDB.syslog ;
import static java.lang.String.format ;

import java.io.File ;
import java.nio.ByteBuffer ;
import java.util.Collection ;
import java.util.Iterator ;
import java.util.Map ;

import org.openjena.atlas.iterator.Iter ;
import org.openjena.atlas.lib.FileOps ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.hp.hpl.jena.shared.Lock ;
import com.hp.hpl.jena.tdb.base.block.Block ;
import com.hp.hpl.jena.tdb.base.block.BlockMgr ;
import com.hp.hpl.jena.tdb.base.file.BufferChannel ;
import com.hp.hpl.jena.tdb.base.file.FileFactory ;
import com.hp.hpl.jena.tdb.base.file.Location ;
import com.hp.hpl.jena.tdb.base.objectfile.ObjectFile ;
import com.hp.hpl.jena.tdb.base.record.RecordFactory ;
import com.hp.hpl.jena.tdb.index.IndexMap ;
import com.hp.hpl.jena.tdb.nodetable.NodeTable ;
import com.hp.hpl.jena.tdb.store.DatasetGraphTDB ;
import com.hp.hpl.jena.tdb.sys.FileRef ;
import com.hp.hpl.jena.tdb.sys.Names ;
import com.hp.hpl.jena.tdb.sys.SystemTDB ;

public class JournalControl
{
    private static Logger log = LoggerFactory.getLogger(JournalControl.class) ;

    public static void print(Journal journal)
    {
        System.out.println("Size: "+journal.size()) ;
        
        for ( JournalEntry e : journal )
        {
            System.out.println(JournalEntry.format(e)) ;
            System.out.println("Posn: "+journal.position()+" : ("+(journal.size()-journal.position())+")") ;
            
        }
    }

    /** Recover a base storage DatasetGraph */
    public static void recovery(DatasetGraphTDB dsg)
    {
        if ( dsg instanceof DatasetGraphTxn )
            throw new TDBTransactionException("Recovery works on the base dataset, not a transactional one") ;
        // Later we may have ...
//        if ( dsg instanceof DatasetGraphTransaction )
//            throw new TDBTransactionException("Recovery works on the base dataset, not a transactional one") ; 
        
        if ( dsg.getLocation().isMem() )
            return ;
        
        // Do we need to recover?
        Journal journal = findJournal(dsg) ;
        if ( journal == null || journal.isEmpty() )
            return ;
        
        for ( FileRef fileRef : dsg.getConfig().nodeTables.keySet() )
            recoverNodeDat(dsg, fileRef) ;
        recoverFromJournal(dsg, journal) ;
        journal.close() ;
        // Recovery complete.  Tidy up.  Node journal files have already been handled.
        if ( journal.getFilename() != null )
        {
            if ( FileOps.exists(journal.getFilename()) )
                FileOps.delete(journal.getFilename()) ;
        }
    }
    
    private static Journal findJournal(DatasetGraphTDB dsg)
    {
        Location loc = dsg.getLocation() ;
        String journalFilename = loc.absolute(Names.journalFile) ;
        File f = new File(journalFilename) ;
        //if ( FileOps.exists(journalFilename)
        
        if ( f.exists() && f.isFile() && f.length() > 0 )
            return Journal.create(loc) ;
        else
            return null ;
    }

    // New recovery - scan to commit, enact, scan, ....
    
    /** Recovery from a journal.
     *  Find if there is a commit record; if so, reply the journal to that point.
     *  Try to see if there is another commit record ...
     */
    public static void recoverFromJournal(DatasetGraphTDB dsg, Journal jrnl )
    {
        if ( dsg instanceof DatasetGraphTxn )
            throw new TDBTransactionException("Recovery works on the base dataset, not a transactional one") ;

        if ( jrnl.isEmpty() )
            return ;
        
        long posn = 0 ;
        for ( ;; )
        {
            long x = scanForCommit(jrnl, posn) ;
            if ( x == -1 ) break ;
            recoverSegment(jrnl, posn, x, dsg) ;
            posn = x ;
        }

        // We have replayed the journals - clean up.
        jrnl.truncate(0) ;
        dsg.sync() ;
    }

    /** Scan to a commit entry, starting at a given position in the journal.
     * Return addrss of entry after commit if found, else -1.
     *  
     */
    private static long scanForCommit(Journal jrnl, long startPosn)
    {
        Iterator<JournalEntry> iter = jrnl.entries(startPosn) ;
        try {
            for ( ; iter.hasNext() ; )
            {
                JournalEntry e = iter.next() ;
                if ( e.getType() == JournalEntryType.Commit )
                    return e.getEndPosition() ;
            }
            return -1 ;
        } finally { Iter.close(iter) ; }
    }
    
    /** Recover one transaction from the start position given.
     *  Scan to see if theer is a commit; if found, play the
     *  journal from the start point to the commit.
     *  Return true is a commit was found.
     *  Leave journal positioned just after commit or at end if none found.
     */
    private static void recoverSegment(Journal jrnl, long startPosn, long endPosn, DatasetGraphTDB dsg)
    {
        Iterator<JournalEntry> iter = jrnl.entries(startPosn) ;
        iter = jrnl.entries(startPosn) ;
        try {
            for ( ; iter.hasNext() ; )
            {
                JournalEntry e = iter.next() ;
                if ( e.getType() == JournalEntryType.Commit )
                {
                    if ( e.getEndPosition() != endPosn )
                        log.warn(format("Inconsistent: end at %d; expected %d", e.getEndPosition(), endPosn)) ;
                    return ;
                }
                replay(e, dsg) ;
            }
        } finally { Iter.close(iter) ; }
    }
    
//    /** Recovery from the system journal.
//     *  Find is there is a commit record; if so, reply the journal.
//     */
//    private static void recoverSystemJournal_0(DatasetGraphTDB dsg)
//    {
//        Location loc = dsg.getLocation() ;
//        String journalFilename = loc.absolute(Names.journalFile) ;
//        File f = new File(journalFilename) ;
//        //if ( FileOps.exists(journalFilename)
//        if ( f.exists() && f.isFile() && f.length() > 0 )
//        {
//            Journal jrnl = Journal.create(loc) ;
//            // Scan for commit.
//            boolean committed = false ;
//            for ( JournalEntry e : jrnl )
//            {
//                if ( e.getType() == JournalEntryType.Commit )
//                    committed = true ;
//                else
//                {
//                    if ( committed )
//                    {
//                        errlog.warn("Extra journal entries ("+loc+")") ;
//                        break ;
//                    }
//                }
//            }
//            if ( committed )
//            {
//                syslog.info("Recovering committed transaction") ;
//                // The NodeTable Journal has already been done!
//                JournalControl.replay(jrnl, dsg) ;
//            }
//            jrnl.truncate(0) ;
//            jrnl.close();
//            dsg.sync() ;
//        }
//        
//        if ( f.exists() )
//            FileOps.delete(journalFilename) ;
//    }
    
    /** Recover a node data file (".dat").
     *  Node data files are append-only so recovering, then not using the data is safe.
     *  Node data file is a precursor for ful lrecovery that works from the master journal.
     */
    private static void recoverNodeDat(DatasetGraphTDB dsg, FileRef fileRef)
    {
        // See DatasetBuilderTxn - same name generation code.
        // [TxTDB:TODO]
        
        RecordFactory recordFactory = new RecordFactory(SystemTDB.LenNodeHash, SystemTDB.SizeOfNodeId) ;
        NodeTable baseNodeTable = dsg.getConfig().nodeTables.get(fileRef) ;
        String objFilename = fileRef.getFilename()+"-"+Names.extJournal ;
        objFilename = dsg.getLocation().absolute(objFilename) ;
        File jrnlFile = new File(objFilename) ;
        if ( jrnlFile.exists() && jrnlFile.length() > 0 )
        {
            syslog.info("Recovering node data: "+fileRef.getFilename()) ;
            ObjectFile dataJrnl = FileFactory.createObjectFileDisk(objFilename) ;
            NodeTableTrans ntt = new NodeTableTrans(null, objFilename, baseNodeTable, new IndexMap(recordFactory), dataJrnl) ;
            ntt.append() ;
            ntt.close() ;
            dataJrnl.close() ;
            baseNodeTable.sync() ;
        }
        if ( jrnlFile.exists() )
            FileOps.delete(objFilename) ;
    }
    
    public static void replay(Transaction transaction)
    {
        Journal journal = transaction.getJournal() ;
        DatasetGraphTDB dsg = transaction.getBaseDataset() ;
        replay(journal, dsg) ;
    }
    
    public static void replay(Journal journal, DatasetGraphTDB dsg)
    {
        if ( journal.size() == 0 )
            return ;
        
        journal.position(0) ;
        dsg.getLock().enterCriticalSection(Lock.WRITE) ;
        try {
            for ( JournalEntry e : journal )
                replay(e, dsg) ;
            // There is no point sync here.  
            // No writes via the DSG have been done 
            // so all internal flags "syncNeeded" are false.
            //dsg.sync() ;
        } 
        catch (RuntimeException ex)
        { 
            // Bad news travels fast.
            syslog.error("Exception during journal replay", ex) ;
            throw ex ;
        }
        finally { dsg.getLock().leaveCriticalSection() ; }
        
        Collection<BlockMgr> x = dsg.getConfig().blockMgrs.values() ;
        for ( BlockMgr blkMgr : x )
            blkMgr.syncForce() ;
        // Must do a hard sync before this.
        journal.truncate(0) ;
    }

    /** return true for "go on" */
    private static boolean replay(JournalEntry e, DatasetGraphTDB dsg)
    {
        Map<FileRef, BlockMgr> mgrs = dsg.getConfig().blockMgrs ;
    
        switch (e.getType())
        {
            case Block:
            {
                // All-purpose, works for direct and mapped mode, copy of a block.
                // [TxTDB:PATCH-UP]
                // Direct: blkMgr.write(e.getBlock()) would work.
                // Mapped: need to copy over the bytes.
                
                BlockMgr blkMgr = mgrs.get(e.getFileRef()) ;
                Block blk = e.getBlock() ;
                log.debug("Replay: {} {}",e.getFileRef(), blk) ;
                blk.setModified(true) ;
                blkMgr.overwrite(blk) ; 
                return true ;
            }   
            case Buffer:
            {
                BufferChannel chan = dsg.getConfig().bufferChannels.get(e.getFileRef()) ;
                ByteBuffer bb = e.getByteBuffer() ;
                log.debug("Replay: {} {}",e.getFileRef(), bb) ;
                chan.write(bb, 0) ; // YUK!
                return true ;
            }
                
            case Commit:
                return false ;
            case Abort:
            case Object:
            case Checkpoint:
                errlog.warn("Unexpected block type: "+e.getType()) ;
        }
        return false ;
    }
}
