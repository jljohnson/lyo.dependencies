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

import static com.hp.hpl.jena.tdb.sys.SystemTDB.syslog ;
import static com.hp.hpl.jena.tdb.transaction.TransactionManager.TxnPoint.BEGIN ;
import static com.hp.hpl.jena.tdb.transaction.TransactionManager.TxnPoint.CLOSE ;
import static java.lang.String.format ;

import java.io.File ;
import java.util.ArrayList ;
import java.util.HashSet ;
import java.util.List ;
import java.util.Set ;
import java.util.concurrent.BlockingQueue ;
import java.util.concurrent.LinkedBlockingDeque ;
import java.util.concurrent.Semaphore ;
import java.util.concurrent.atomic.AtomicLong ;

import org.openjena.atlas.lib.Pair ;
import org.openjena.atlas.logging.Log ;
import org.slf4j.Logger ;
import org.slf4j.LoggerFactory ;

import com.hp.hpl.jena.query.ReadWrite ;
import com.hp.hpl.jena.shared.Lock ;
import com.hp.hpl.jena.tdb.store.DatasetGraphTDB ;
import com.hp.hpl.jena.tdb.sys.SystemTDB ;

public class TransactionManager
{
    // TODO Don't keep counters, keep lists.
    // TODO Useful logging.
    
    private static boolean checking = true ;
    
    private static Logger log = LoggerFactory.getLogger(TransactionManager.class) ;
    private Set<Transaction> activeTransactions = new HashSet<Transaction>() ;
    synchronized public boolean activeTransactions() { return !activeTransactions.isEmpty() ; }
    
    // Setting this true cause the TransactionManager to keep lists of transactions
    // and what has happened.  Nothing is thrown away, but eventually it will
    // consume too much memory.
    
    // Record happenings.
    private boolean recordHistory = false ;
    
    enum TxnPoint { BEGIN, COMMIT, ABORT, CLOSE, QUEUE, UNQUEUE }
    private List<Pair<Transaction, TxnPoint>> transactionStateTransition ;
    
    private void record(Transaction txn, TxnPoint state)
    {
        if ( ! recordHistory ) return ;
        initRecordingState() ;
        transactionStateTransition.add(new Pair<Transaction, TxnPoint>(txn, state)) ;
    }
    
    // Transactions that have commited (and the journal is written) but haven't
    // writted back to the main database. 
    
    int maxQueue = 0 ;
    List<Transaction> commitedAwaitingFlush = new ArrayList<Transaction>() ;    
    
    static AtomicLong transactionId = new AtomicLong(1) ;
    
    AtomicLong activeReaders = new AtomicLong(0) ; 
    AtomicLong activeWriters = new AtomicLong(0) ; // 0 or 1
    
    // Misc stats
    AtomicLong finishedReaders = new AtomicLong(0) ;
    AtomicLong committedWriters = new AtomicLong(0) ;
    AtomicLong abortedWriters = new AtomicLong(0) ;
    
    // Ensure single writer.
    private Semaphore writersWaiting = new Semaphore(1, true) ;
    // Delayes enacting transactions.
    private BlockingQueue<Transaction> queue = new LinkedBlockingDeque<Transaction>() ;

    private Thread committerThread ;    // Later

    private DatasetGraphTDB baseDataset ;
    private Journal journal ;
    
    // TODO Tidy up - more to end-of-file.
    
    /* Various policies:
     * + MRSW : writer locks to write back; blocks until let trhough.  Every reader takes an read lock.
     * + Writers write if free, else queue for a reader or writer to clearup.
     * + Async: there is a thread whose job it is to flush tot he base dataset (with an MRSW lock). 
     */
    
    // Add queue unqueue?
    /*
     * The order of calls is: 
     * 1/ transactionStarts
     * 2/ readerStarts or writerStarts
     * 3/ readerFinishes or writerCommits or writerAborts
     * 4/ transactionFinishes
     * 5/ transactionCloses
     */
    
    private interface TSM
    {
        // Quert unqueue?
        void transactionStarts(Transaction txn) ;
        void transactionFinishes(Transaction txn) ;
        void transactionCloses(Transaction txn) ;
        void readerStarts(Transaction txn) ;
        void readerFinishes(Transaction txn) ;
        void writerStarts(Transaction txn) ;
        void writerCommits(Transaction txn) ;
        void writerAborts(Transaction txn) ;
    }
    
    class TSM_Base implements TSM
    {
        @Override public void transactionStarts(Transaction txn)    {}
        @Override public void transactionFinishes(Transaction txn)  {}
        @Override public void transactionCloses(Transaction txn)    {}
        @Override public void readerStarts(Transaction txn)         {}
        @Override public void readerFinishes(Transaction txn)       {}
        @Override public void writerStarts(Transaction txn)         {}
        @Override public void writerCommits(Transaction txn)        {}
        @Override public void writerAborts(Transaction txn)         {}
    }
    
    class TSM_Logger extends TSM_Base
    {
        @Override public void readerStarts(Transaction txn)         { log("start", txn) ; }
        @Override public void readerFinishes(Transaction txn)       { log("finish", txn) ; }
        @Override public void writerStarts(Transaction txn)         { log("begin", txn) ; }
        @Override public void writerCommits(Transaction txn)        { log("commit", txn) ; }
        @Override public void writerAborts(Transaction txn)         { log("abort", txn) ; }
    }

    /** More detailed */
    class TSM_LoggerDebug extends TSM_Base
    {
        @Override public void readerStarts(Transaction txn)         { logInternal("start",  txn) ; }
        @Override public void readerFinishes(Transaction txn)       { logInternal("finish", txn) ; }
        @Override public void writerStarts(Transaction txn)         { logInternal("begin",  txn) ; }
        @Override public void writerCommits(Transaction txn)        { logInternal("commit", txn) ; }
        @Override public void writerAborts(Transaction txn)         { logInternal("abort",  txn) ; }
    }

    
    class TSM_Counters implements TSM
    {
        @Override public void transactionStarts(Transaction txn)    { activeTransactions.add(txn) ; }
        @Override public void transactionFinishes(Transaction txn)  { activeTransactions.remove(txn) ; }
        @Override public void transactionCloses(Transaction txn)    { }
        @Override public void readerStarts(Transaction txn)         { inc(activeReaders) ; }
        @Override public void readerFinishes(Transaction txn)       { dec(activeReaders) ; inc(finishedReaders); }
        @Override public void writerStarts(Transaction txn)         { inc(activeWriters) ; }
        @Override public void writerCommits(Transaction txn)        { dec(activeWriters) ; inc(committedWriters) ; }
        @Override public void writerAborts(Transaction txn)         { dec(activeWriters) ; inc(abortedWriters) ; }
    }
    
    // Short name: x++
    static long inc(AtomicLong x)   { return x.getAndIncrement() ; }
    // Short name: --x
    static long dec(AtomicLong x)   { return x.decrementAndGet() ; }
    
    // Transaction policy:
    // TSM + WriterEnters, WriterLeaves which may use the semaphore. (+ReaderEnters, ReaderLeaves ??)
    
    // Policy for writing back journal'ed data to the base datasetgraph
    // Writes if no reader at end of writer, else queues.
    // Queue cleared at en dof any transaction finding itself the only transaction.
    class TSM_WriteBackEndTxn extends TSM_Base
    {
        // Safe mode.
        // Take a READ lock over the base dataset.
        // Write-back takes a WRITE lock.
        @Override public void readerStarts(Transaction txn)    { txn.getBaseDataset().getLock().enterCriticalSection(Lock.READ) ; }
        @Override public void writerStarts(Transaction txn)    { txn.getBaseDataset().getLock().enterCriticalSection(Lock.READ) ; }
        
        // Currently, the writer semaphore is managed explicitly in the main code.
        
        @Override public void readerFinishes(Transaction txn)       
        { 
            txn.getBaseDataset().getLock().leaveCriticalSection() ;
            processDelayedReplayQueue(txn) ;
        }
        
        @Override public void writerCommits(Transaction txn)
        {
            txn.getBaseDataset().getLock().leaveCriticalSection() ;

            // This is so important, it shouldn't be in a TSM.
            if ( activeReaders.get() == 0 )
            {
                // Can commit immediately.
                // Ensure the queue is empty though.
                // Could simply add txn to the commit queue and do it that way.  
                if ( log() ) log("Commit immediately", txn) ; 
                
                // Currently, all we need is 
                //    JournalControl.replay(txn) ;
                // because that plays queued transactions.
                // But for long term generallity, at the cost of one check of the journal size
                // we do this sequence.
                
                processDelayedReplayQueue(txn) ;
                enactTransaction(txn) ;
                JournalControl.replay(txn) ;
            }
            else
            {
                // Can't write back to the base database at the moment.
                commitedAwaitingFlush.add(txn) ;
                maxQueue = Math.max(commitedAwaitingFlush.size(), maxQueue) ;
                if ( log() ) log("Add to pending queue", txn) ; 
                queue.add(txn) ;
            }
        }
        
        @Override public void writerAborts(Transaction txn)
        { 
            txn.getBaseDataset().getLock().leaveCriticalSection() ;
            processDelayedReplayQueue(txn) ;
        }
    }
    
    // Policy for writing back that always writes from the writer by using a
    // MRSW lock, with a write step at the end of the writer.
    // Always a read loc for any active transaction (reader or the writer)
    // Still use semaphore for writer entry control.
    class TSM_WriterWriteBack extends TSM_Base
    {
        // TODO
    }
    
    // Policy for writing where a transaction takes an  MRSW at the start.
    // Semaphore for writer entry unnecessary.
    class TSM_MRSW_Writer extends TSM_Base
    {
        // TODO
    }
    

    class TSM_Record extends TSM_Base
    {
        // Later - record on one list the state transition.
        @Override
        public void transactionStarts(Transaction txn)      { record(txn, BEGIN) ; }
        @Override
        public void transactionFinishes(Transaction txn)    { record(txn, CLOSE) ; }
    }
    
    private TSM[] actions = new TSM[] { 
        new TSM_Counters() ,           // Must be first.
        //new TSM_LoggerDebug() ,
        new TSM_Logger() ,
        (recordHistory ? new TSM_Record() : null ) ,
        new TSM_WriteBackEndTxn()        // Write back policy. Must be last.
    } ;
    
    public TransactionManager(DatasetGraphTDB dsg)
    {
        this.baseDataset = dsg ; 
        this.journal = Journal.create(dsg.getLocation()) ;
        // LATER
//        Committer c = new Committer() ;
//        this.committerThread = new Thread(c) ;
//        committerThread.setDaemon(true) ;
//        committerThread.start() ;
    }

    public void closedown()
    {
        processDelayedReplayQueue(null) ;
        journal.close() ;
    }
    
    private Transaction createTransaction(DatasetGraphTDB dsg, ReadWrite mode, String label)
    {
        Transaction txn = new Transaction(dsg, mode, transactionId.getAndIncrement(), label, this) ;
        return txn ;
    }

    public DatasetGraphTxn begin(ReadWrite mode)
    {
        return begin(mode, null) ;
    }
    
    public DatasetGraphTxn begin(ReadWrite mode, String label)
    {
        // Not synchronized (else blocking on semaphore will never wake up
        // because Semaphore.release is inside synchronized.
        // Allow only one active writer. 
        if ( mode == ReadWrite.WRITE )
        {
            // Writers take a WRITE permit from the semaphore to ensure there
            // is at most one active writer, else the attempt to start the
            // transaction blocks.
            try { writersWaiting.acquire() ; }
            catch (InterruptedException e)
            { 
                log.error(label, e) ;
                throw new TDBTransactionException(e) ;
            }
        }
        // entry synchronized part
        return begin$(mode, label) ;
    }
    
    public static final boolean DEBUG = false ; 
        
    // If DatasetGraphTransaction has a sync lock on sConn, this
    // does not need to be sync'ed. But it's possible to use some
    // of the low level object directly so we'll play safe.  
    
    synchronized
    private DatasetGraphTxn begin$(ReadWrite mode, String label)
    {
        if ( mode == ReadWrite.WRITE && activeWriters.get() > 0 )    // Guard
            throw new TDBTransactionException("Existing active write transaction") ;

        if ( DEBUG ) 
            switch ( mode )
            {
                case READ : System.out.print("r") ; break ;
                case WRITE : System.out.print("w") ; break ;
            }
        
        DatasetGraphTDB dsg = baseDataset ;
        // *** But, if there are pending, committed transactions, use latest.
        if ( ! commitedAwaitingFlush.isEmpty() )
        {  
            if ( DEBUG ) System.out.print(commitedAwaitingFlush.size()) ;
            dsg = commitedAwaitingFlush.get(commitedAwaitingFlush.size()-1).getActiveDataset() ;
        }
        else 
        {
            if ( DEBUG ) System.out.print('_') ;
        }
        Transaction txn = createTransaction(dsg, mode, label) ;
        
        log("begin$", txn) ;

        DatasetGraphTxn dsgTxn = (DatasetGraphTxn)new DatasetBuilderTxn(this).build(txn, mode, dsg) ;
        txn.setActiveDataset(dsgTxn) ;

        for ( TransactionLifecycle component : dsgTxn.getTransaction().components() )
            component.begin(dsgTxn.getTransaction()) ;

        noteStartTxn(txn) ;
        return dsgTxn ;
    }

    /* Signal a transaction has commited.  The journal has a commit record
     * and a sync to disk. The code here manages the inter-transaction stage
     *  of deciding how to play the changes back to the base data. 
     */ 
    synchronized
    public void notifyCommit(Transaction transaction)
    {
        // Transaction has done the commitPrepare - can we enact it?
        
        if ( ! activeTransactions.contains(transaction) )
            SystemTDB.errlog.warn("Transaction not active: "+transaction.getTxnId()) ;
        
        noteTxnCommit(transaction) ;

        switch ( transaction.getMode() )
        {
            case READ: break ;
            case WRITE: writersWaiting.release() ;
        }
    }

    synchronized
    public void notifyAbort(Transaction transaction)
    {
        // Transaction has done the abort on all the transactional elements.
        if ( ! activeTransactions.contains(transaction) )
            SystemTDB.errlog.warn("Transaction not active: "+transaction.getTxnId()) ;
        
        noteTxnAbort(transaction) ;
        
        switch ( transaction.getMode() )
        {
            case READ: break ;
            case WRITE: writersWaiting.release() ;
        }
    }
    
    /** The stage in a commit after committing - make the changes permanent in the base data */ 
    private void enactTransaction(Transaction transaction)
    {
        // Really, really do it!
        for ( TransactionLifecycle x : transaction.components() )
        {
            x.commitEnact(transaction) ;
            x.commitClearup(transaction) ;
        }
        transaction.signalEnacted() ;
    }

    private void processDelayedReplayQueue(Transaction txn)
    {
        // Sync'ed by notifyCommit.
        // If we knew which version of the DB each was looking at, we could reduce more often here.
        // [TxTDB:TODO]
        if ( activeReaders.get() != 0 || activeWriters.get() != 0 )
        {
            if ( queue.size() > 0 && log() )
                log(format("Pending transactions: R=%s / W=%s", activeReaders, activeWriters), txn) ;
            return ;
        }

        if ( DEBUG )
        {
            if ( queue.size() > 0 ) 
                System.out.print("!"+queue.size()+"!") ;
        }
        
        if ( log() )
            log("Start flush delayed commits", txn) ;
        
        if ( DEBUG ) checkNodesDatJrnl("1", txn) ;
        
        if ( queue.size() == 0 && txn != null )
            // Nothing to do - journal should be empty. 
            return ;
        
        while ( queue.size() > 0 )
        {
            // Currently, replay is replay everything
            // so looping on a per-transaction basis is
            // pointless but harmless.  
            
            try {
                Transaction txn2 = queue.take() ;
                if ( txn2.getMode() == ReadWrite.READ )
                    continue ;
                if ( log() )
                    log("Flush delayed commit of "+txn2.getLabel(), txn) ;
                if ( DEBUG ) checkNodesDatJrnl("2", txn) ;
                checkReplaySafe() ;
                enactTransaction(txn2) ;
                commitedAwaitingFlush.remove(txn2) ;
            } catch (InterruptedException ex)
            { Log.fatal(this, "Interruped!", ex) ; }
        }

        checkReplaySafe() ;
        if ( DEBUG ) checkNodesDatJrnl("3", txn) ;

        // Whole journal to base database
        JournalControl.replay(journal, baseDataset) ;

        if ( DEBUG ) checkNodesDatJrnl("4", txn) ;
        
        checkReplaySafe() ;
        if ( log() )
            log("End flush delayed commits", txn) ;
        
        
        
    }

    private static void checkNodesDatJrnl(String label, Transaction txn)
    {
        if (txn != null)
        {
            String x = txn.getBaseDataset().getLocation().getPath(label+": nodes.dat-jrnl") ;
            long len = new File(x).length() ;
            if (len != 0)
                log("nodes.dat-jrnl: not empty", txn) ;
        }   
    }
    
    private void checkReplaySafe()
    {
        if ( ! checking ) return ;
        if ( activeReaders.get() != 0 || activeWriters.get() != 0 )
            log.error("There are now active transactions") ;
    }
    
    synchronized
    public void notifyClose(Transaction txn)
    {
        if ( txn.getState() == TxnState.ACTIVE )
        {
            String x = txn.getBaseDataset().getLocation().getDirectoryPath() ;
            syslog.warn("close: Transaction not commited or aborted: Transaction: "+txn.getTxnId()+" @ "+x) ;
            // Force abort then close
            txn.abort() ;
            txn.close() ;
            return ;
        }
        noteTxnClose(txn) ;
    }
        
    // TODO Collapse these.
    private void noteStartTxn(Transaction transaction)
    {
        switch (transaction.getMode())
        {
            case READ : readerStarts(transaction) ; break ;
            case WRITE : writerStarts(transaction) ; break ;
        }
        transactionStarts(transaction) ;
    }

    private void noteTxnCommit(Transaction transaction)
    {
        switch (transaction.getMode())
        {
            case READ : readerFinishes(transaction) ; break ;
            case WRITE : writerCommits(transaction) ; break ;
        }
        transactionFinishes(transaction) ;
    }
    
    private void noteTxnAbort(Transaction transaction)
    {
        switch (transaction.getMode())
        {
            case READ : readerFinishes(transaction) ; break ;
            case WRITE : writerAborts(transaction) ; break ;
        }
        transactionFinishes(transaction) ;
    }
    
    private void noteTxnClose(Transaction transaction)
    {
        transactionCloses(transaction) ;
    }
    
    // ---- Recording
    
    /** Get recording state */
    public boolean recording()              { return recordHistory ; }
    /** Set recording on or off */
    public void recording(boolean flag)
    {
        recordHistory = flag ;
        if ( recordHistory )
            initRecordingState() ;
    }
    /** Clear all recording state - does not clear stats */ 
    public void clearRecordingState()
    {
        initRecordingState() ;
        transactionStateTransition.clear() ;
    }
    
    private void initRecordingState()
    {
        if ( transactionStateTransition == null )
            transactionStateTransition = new ArrayList<Pair<Transaction, TxnPoint>>() ;
    }

    public Journal getJournal()
    {
        return journal ;
    }

    private static boolean log()
    {
        return syslog.isDebugEnabled() || log.isDebugEnabled() ;
    }
    
    private static void log(String msg, Transaction txn)
    {
        if ( ! log() )
            return ;
        if ( txn == null )
            logger().debug("<No txn>: "+msg) ;
        else
            logger().debug(txn.getLabel()+": "+msg) ;
    }
    
    private void logInternal(String action, Transaction txn)
    {
        if ( ! log() )
            return ;
        String txnStr = ( txn == null ) ? "<null>" : txn.getLabel() ;
        System.err.printf(format("%6s %s -- %s", action, txnStr, state())) ;
    }

    private static Logger logger()
    {
        if ( syslog.isDebugEnabled() )
            return syslog ;
        else
            return log ;
    }
    
    synchronized
    public SysTxnState state()
    { 
        return new SysTxnState(this) ;
    }
    
    // LATER.
    class Committer implements Runnable
    {
        @Override
        public void run()
        {
            for(;;)
            {
                // Wait until the reader count goes to zero.
                
                // This wakes up for every transation but maybe 
                // able to play several transactions at once (later).
                try {
                    Transaction txn = queue.take() ;
                    // This takes a Write lock on the  DSG - this is where it blocks.
                    JournalControl.replay(txn) ;
                    synchronized(TransactionManager.this)
                    {
                        commitedAwaitingFlush.remove(txn) ;
                    }
                } catch (InterruptedException ex)
                { Log.fatal(this, "Interruped!", ex) ; }
            }
        }
        
    }
    
    private void transactionStarts(Transaction txn)
    {
        for ( TSM tsm : actions )
            if ( tsm != null )
                tsm.transactionStarts(txn) ;
    }

    private void transactionFinishes(Transaction txn)
    {
        for ( TSM tsm : actions )
            if ( tsm != null )
                tsm.transactionFinishes(txn) ;
    }
    
    private void transactionCloses(Transaction txn)
    {
        for ( TSM tsm : actions )
            if ( tsm != null )
                tsm.transactionCloses(txn) ;
    }
    
    private void readerStarts(Transaction txn)
    {
        for ( TSM tsm : actions )
            if ( tsm != null )
                tsm.readerStarts(txn) ;
    }
    
    private void readerFinishes(Transaction txn)
    {
        for ( TSM tsm : actions )
            if ( tsm != null )
                tsm.readerFinishes(txn) ;
    }

    private void writerStarts(Transaction txn)
    {
        for ( TSM tsm : actions )
            if ( tsm != null )
                tsm.writerStarts(txn) ;
    }

    private void writerCommits(Transaction txn)
    {
        for ( TSM tsm : actions )
            if ( tsm != null )
                tsm.writerCommits(txn) ;
    }

    private void writerAborts(Transaction txn)
    {
        for ( TSM tsm : actions )
            if ( tsm != null )
                tsm.writerAborts(txn) ;
    }
}
