/*
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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tomee.chatterbox.twitter.adapter;

import org.apache.tomee.chatterbox.twitter.api.TwitterConnection;
import org.apache.tomee.chatterbox.twitter.api.TwitterConnectionFactory;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;
import java.util.logging.Logger;

public class TwitterConnectionFactoryImpl implements TwitterConnectionFactory {
    private static final long serialVersionUID = 1L;

    private static Logger log = Logger.getLogger(TwitterConnectionFactoryImpl.class.getName());

    private Reference reference;

    private TwitterManagedConnectionFactory mcf;

    private ConnectionManager connectionManager;

    public TwitterConnectionFactoryImpl() {

    }

    public TwitterConnectionFactoryImpl(TwitterManagedConnectionFactory mcf, ConnectionManager cxManager) {
        this.mcf = mcf;
        this.connectionManager = cxManager;
    }

    @Override
    public TwitterConnection getConnection() throws ResourceException {
        log.finest("getConnection()");
        return (TwitterConnection) connectionManager.allocateConnection(mcf, null);
    }

    @Override
    public Reference getReference() throws NamingException {
        log.finest("getReference()");
        return reference;
    }

    @Override
    public void setReference(Reference reference) {
        log.finest("setReference()");
        this.reference = reference;
    }


}
