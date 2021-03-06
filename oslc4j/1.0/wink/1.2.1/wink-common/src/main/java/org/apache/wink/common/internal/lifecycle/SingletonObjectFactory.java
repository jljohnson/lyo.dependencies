/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *  
 *   http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *  
 *******************************************************************************/
package org.apache.wink.common.internal.lifecycle;

import org.apache.wink.common.RuntimeContext;

/**
 * Creates a ObjectFactory that always returns a same instance.
 * 
 * @param <T>
 */
class SingletonObjectFactory<T> implements ObjectFactory<T> {

    protected final T        object;
    protected final Class<T> objectClass;

    @SuppressWarnings("unchecked")
    public SingletonObjectFactory(T object) {
        this.object = object;
        this.objectClass = (Class<T>)object.getClass();
    }

    public T getInstance(RuntimeContext context) {
        return object;
    }

    public Class<T> getInstanceClass() {
        return objectClass;
    }

    @Override
    public String toString() {
        return String.format("SingletonOF: %s", objectClass); //$NON-NLS-1$
    }

    public void releaseInstance(T instance, RuntimeContext context) {
        /* do nothing */
    }

    public void releaseAll(RuntimeContext context) {
        /* do nothing */
    }
}
