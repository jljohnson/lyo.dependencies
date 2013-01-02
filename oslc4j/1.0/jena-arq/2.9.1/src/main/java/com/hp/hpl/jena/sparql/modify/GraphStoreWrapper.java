/**
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

package com.hp.hpl.jena.sparql.modify;

import com.hp.hpl.jena.query.Dataset ;
import com.hp.hpl.jena.sparql.core.DatasetGraphWrapper ;
import com.hp.hpl.jena.update.GraphStore ;
import com.hp.hpl.jena.update.UpdateRequest ;

public class GraphStoreWrapper extends DatasetGraphWrapper implements GraphStore
{
    protected final GraphStore graphStore ;

    public GraphStoreWrapper(GraphStore graphStore)
    { 
        super(graphStore) ;
        this.graphStore = graphStore ;
    } 
    
    @Override
    public Dataset toDataset()
    { return graphStore.toDataset() ; }

    @Override @Deprecated
    public void startRequest()
    { graphStore.startRequest() ; }

    @Override @Deprecated
    public void finishRequest()
    { graphStore.finishRequest() ; }

    @Override
    public void startRequest(UpdateRequest request)
    { graphStore.startRequest(request) ; }

    @Override
    public void finishRequest(UpdateRequest request)
    { graphStore.finishRequest(request) ; }
}

