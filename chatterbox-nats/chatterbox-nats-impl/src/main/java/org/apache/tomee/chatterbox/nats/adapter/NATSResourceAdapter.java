/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomee.chatterbox.nats.adapter;

import javax.resource.ResourceException;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.BootstrapContext;
import javax.resource.spi.ConfigProperty;
import javax.resource.spi.Connector;
import javax.resource.spi.ResourceAdapter;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.resource.spi.endpoint.MessageEndpoint;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.resource.spi.work.Work;
import javax.resource.spi.work.WorkManager;
import javax.transaction.xa.XAResource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.IllegalStateException;

@Connector(description = "Sample Resource Adapter", displayName = "Sample Resource Adapter", eisType = "Sample Resource Adapter", version = "1.0")
public class NATSResourceAdapter implements ResourceAdapter {
    final Map<NATSActivationSpec, EndpointTarget> targets = new ConcurrentHashMap<NATSActivationSpec, EndpointTarget>();

    @ConfigProperty
    private String token;

    private WorkManager workManager;
    private String user;
    private String userId;

    public void start(BootstrapContext bootstrapContext) throws ResourceAdapterInternalException {
        workManager = bootstrapContext.getWorkManager();
        // connect to NATS
    }

    public void stop() {
        // disconnect
    }

    public void endpointActivation(final MessageEndpointFactory messageEndpointFactory, final ActivationSpec activationSpec)
            throws ResourceException {
        final NATSActivationSpec NATSActivationSpec = (NATSActivationSpec) activationSpec;

        workManager.scheduleWork(new Work() {

            @Override
            public void run() {
                try {
                    final MessageEndpoint messageEndpoint = messageEndpointFactory.createEndpoint(null);

                    final EndpointTarget target = new EndpointTarget(messageEndpoint);
                    final Class<?> endpointClass = NATSActivationSpec.getBeanClass() != null ? NATSActivationSpec
                            .getBeanClass() : messageEndpointFactory.getEndpointClass();


                    targets.put(NATSActivationSpec, target);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void release() {
            }

        });

    }

    public void endpointDeactivation(MessageEndpointFactory messageEndpointFactory, ActivationSpec activationSpec) {
        final NATSActivationSpec telnetActivationSpec = (NATSActivationSpec) activationSpec;

        final EndpointTarget endpointTarget = targets.get(telnetActivationSpec);
        if (endpointTarget == null) {
            throw new IllegalStateException("No EndpointTarget to undeploy for ActivationSpec " + activationSpec);
        }

        // unsubscribe

        endpointTarget.messageEndpoint.release();
    }

    public XAResource[] getXAResources(ActivationSpec[] activationSpecs) throws ResourceException {
        return new XAResource[0];
    }

    public void sendMessage(final String channel, final String message) {
        // publish a message
    }


    private static class EndpointTarget {
        private final MessageEndpoint messageEndpoint;

        public EndpointTarget(MessageEndpoint messageEndpoint) {
            this.messageEndpoint = messageEndpoint;
        }

    }
}
