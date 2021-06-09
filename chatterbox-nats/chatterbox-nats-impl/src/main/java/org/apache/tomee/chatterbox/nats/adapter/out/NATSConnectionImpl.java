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

import org.apache.tomee.chatterbox.nats.api.NATSConnection;
import org.apache.tomee.chatterbox.nats.api.NATSException;

import java.util.logging.Logger;

public class NATSConnectionImpl implements NATSConnection {
    private static Logger log = Logger.getLogger(NATSConnectionImpl.class.getName());

    private NATSManagedConnection mc;

    private NATSManagedConnectionFactory mcf;

    public NATSConnectionImpl(NATSManagedConnection mc, NATSManagedConnectionFactory mcf) {
        this.mc = mc;
        this.mcf = mcf;
    }

    public void publish(final String subject, final byte[] data) throws NATSException {
        mc.publish(subject, data);
    }

    public void close() {
        mc.closeHandle(this);
    }
}
