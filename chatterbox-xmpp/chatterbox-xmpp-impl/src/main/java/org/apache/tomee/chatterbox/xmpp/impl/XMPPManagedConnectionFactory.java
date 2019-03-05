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
package org.apache.tomee.chatterbox.xmpp.impl;

import org.apache.tomee.chatterbox.xmpp.api.XMPPConnection;
import org.apache.tomee.chatterbox.xmpp.api.XMPPConnectionFactory;

import javax.resource.ResourceException;
import javax.resource.spi.ConnectionDefinition;
import javax.resource.spi.ConnectionManager;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.ResourceAdapter;
import javax.resource.spi.ResourceAdapterAssociation;
import javax.security.auth.Subject;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

@ConnectionDefinition(connectionFactory = XMPPConnectionFactory.class,
        connectionFactoryImpl = XMPPConnectionFactoryImpl.class,
        connection = XMPPConnection.class,
        connectionImpl = XMPPConnectionImpl.class)
public class XMPPManagedConnectionFactory implements ManagedConnectionFactory, ResourceAdapterAssociation {

    private static final long serialVersionUID = 1L;

    private static Logger log = Logger.getLogger(XMPPManagedConnectionFactory.class.getName());

    private ResourceAdapter ra;

    private PrintWriter logwriter;

    public XMPPManagedConnectionFactory() {

    }

    public Object createConnectionFactory(ConnectionManager cxManager) throws ResourceException {
        log.finest("createConnectionFactory()");
        return new XMPPConnectionFactoryImpl(this, cxManager);
    }

    public Object createConnectionFactory() throws ResourceException {
        throw new ResourceException("This resource adapter doesn't support non-managed environments");
    }

    public ManagedConnection createManagedConnection(Subject subject,
                                                     ConnectionRequestInfo cxRequestInfo) throws ResourceException {
        log.finest("createManagedConnection()");
        return new XMPPManagedConnection(this);
    }

    public ManagedConnection matchManagedConnections(Set connectionSet,
                                                     Subject subject, ConnectionRequestInfo cxRequestInfo) throws ResourceException {
        log.finest("matchManagedConnections()");
        ManagedConnection result = null;
        Iterator it = connectionSet.iterator();
        while (result == null && it.hasNext()) {
            ManagedConnection mc = (ManagedConnection) it.next();
            if (mc instanceof XMPPManagedConnection) {
                result = mc;
            }

        }
        return result;
    }

    public PrintWriter getLogWriter() throws ResourceException {
        log.finest("getLogWriter()");
        return logwriter;
    }

    public void setLogWriter(PrintWriter out) throws ResourceException {
        log.finest("setLogWriter()");
        logwriter = out;
    }

    public ResourceAdapter getResourceAdapter() {
        log.finest("getResourceAdapter()");
        return ra;
    }

    public void setResourceAdapter(ResourceAdapter ra) {
        log.finest("setResourceAdapter()");
        this.ra = ra;
    }

    @Override
    public int hashCode() {
        int result = 17;
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null)
            return false;
        if (other == this)
            return true;
        if (!(other instanceof XMPPManagedConnectionFactory))
            return false;
        boolean result = true;
        return result;
    }

}
