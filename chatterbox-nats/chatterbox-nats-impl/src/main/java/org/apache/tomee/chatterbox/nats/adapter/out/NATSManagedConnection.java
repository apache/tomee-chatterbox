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
package org.apache.tomee.chatterbox.nats.adapter.out;

import org.apache.tomee.chatterbox.nats.adapter.NATSResourceAdapter;
import org.apache.tomee.chatterbox.nats.api.NATSConnection;

import javax.resource.NotSupportedException;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionEvent;
import javax.resource.spi.ConnectionEventListener;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.LocalTransaction;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionMetaData;
import javax.security.auth.Subject;
import javax.transaction.xa.XAResource;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class NATSManagedConnection implements ManagedConnection {

    private static Logger log = Logger.getLogger(NATSManagedConnection.class.getName());

    private PrintWriter logwriter;

    private NATSManagedConnectionFactory mcf;

    private List<ConnectionEventListener> listeners;

    private NATSConnectionImpl connection;

    public NATSManagedConnection(NATSManagedConnectionFactory mcf) {
        this.mcf = mcf;
        this.logwriter = null;
        this.listeners = Collections.synchronizedList(new ArrayList<ConnectionEventListener>(1));
        this.connection = null;
    }

    public Object getConnection(Subject subject,
                                ConnectionRequestInfo cxRequestInfo) throws ResourceException {
        log.finest("getConnection()");
        connection = new NATSConnectionImpl(this, mcf);
        return connection;
    }

    public void associateConnection(Object connection) throws ResourceException {
        log.finest("associateConnection()");

        if (connection == null)
            throw new ResourceException("Null connection handle");

        if (!(connection instanceof NATSConnectionImpl))
            throw new ResourceException("Wrong connection handle");

        this.connection = (NATSConnectionImpl) connection;
    }

    public void cleanup() throws ResourceException {
        log.finest("cleanup()");
    }

    public void destroy() throws ResourceException {
        log.finest("destroy()");
    }

    public void addConnectionEventListener(ConnectionEventListener listener) {
        log.finest("addConnectionEventListener()");

        if (listener == null) {
            throw new IllegalArgumentException("Listener is null");
        }

        listeners.add(listener);
    }

    public void removeConnectionEventListener(ConnectionEventListener listener) {
        log.finest("removeConnectionEventListener()");
        if (listener == null)
            throw new IllegalArgumentException("Listener is null");
        listeners.remove(listener);
    }

    void closeHandle(NATSConnection handle) {
        ConnectionEvent event = new ConnectionEvent(this, ConnectionEvent.CONNECTION_CLOSED);
        event.setConnectionHandle(handle);
        for (ConnectionEventListener cel : listeners) {
            cel.connectionClosed(event);
        }
    }

    public PrintWriter getLogWriter() throws ResourceException {
        log.finest("getLogWriter()");
        return logwriter;
    }

    public void setLogWriter(PrintWriter out) throws ResourceException {
        log.finest("setLogWriter()");
        logwriter = out;
    }

    public LocalTransaction getLocalTransaction() throws ResourceException {
        throw new NotSupportedException("getLocalTransaction() not supported");
    }

    public XAResource getXAResource() throws ResourceException {
        throw new NotSupportedException("getXAResource() not supported");
    }

    public ManagedConnectionMetaData getMetaData() throws ResourceException {
        log.finest("getMetaData()");
        return new NATSManagedConnectionMetaData();
    }

    void sendMessage(final String channel, final String message) {
        log.finest("sendMessage()");

        final NATSResourceAdapter resourceAdapter = (NATSResourceAdapter) mcf.getResourceAdapter();
        resourceAdapter.sendMessage(channel, message);
    }
}
