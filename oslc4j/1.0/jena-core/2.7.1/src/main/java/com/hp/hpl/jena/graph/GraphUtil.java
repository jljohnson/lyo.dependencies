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

package com.hp.hpl.jena.graph;

import com.hp.hpl.jena.util.iterator.*;

/**
    An ad-hoc collection of useful code for graphs; starting with findAll.
 	@author kers
*/
public class GraphUtil
    {
    /**
        Only static methods here - the class cannot be instantiated.
    */
    private GraphUtil()
        {}

    /**
        Answer an iterator covering all the triples in the specified graph.
    	@param g the graph from which to extract triples
    	@return an iterator over all the graph's triples
    */
    public static ExtendedIterator<Triple> findAll( Graph g )
        { return g.find( Triple.ANY ); }
                
    }
