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

import io.nats.streaming.Message;
import io.nats.streaming.MessageHandler;
import io.nats.streaming.Options;
import io.nats.streaming.StreamingConnection;
import io.nats.streaming.StreamingConnectionFactory;
import io.nats.streaming.Subscription;
import org.apache.tomee.chatterbox.nats.api.InboundListener;
import org.apache.tomee.chatterbox.nats.api.NATSException;
import org.apache.tomee.chatterbox.nats.api.NATSMessage;

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
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.IllegalStateException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Connector(description = "Sample Resource Adapter", displayName = "Sample Resource Adapter", eisType = "Sample Resource Adapter", version = "1.0")
public class NATSResourceAdapter implements ResourceAdapter {
    private static final Logger LOGGER = Logger.getLogger(NATSResourceAdapter.class.getName());
    private final Map<NATSActivationSpec, EndpointTarget> targets = new ConcurrentHashMap<NATSActivationSpec, EndpointTarget>();

    private static final Method ONMESSAGE;

    static {
        try {
            ONMESSAGE = InboundListener.class.getMethod("onMessage", NATSMessage.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @ConfigProperty
    private String baseAddress;

    @ConfigProperty
    private String clientId;

    @ConfigProperty
    private String clusterId;

    private WorkManager workManager;
    private StreamingConnectionFactory cf;
    private StreamingConnection connection;

    public void start(BootstrapContext bootstrapContext) throws ResourceAdapterInternalException {
        workManager = bootstrapContext.getWorkManager();

        try {
            cf = new
                    StreamingConnectionFactory(new Options.Builder().natsUrl(baseAddress)
                    .clusterId(clusterId).clientId(clientId).build());

            connection = cf.createConnection();
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Error starting connection to NATS server", t);
        }
    }

    public void stop() {
        try {
            connection.close();
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Error closing connection to NATS server", t);
        }
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
                    targets.put(NATSActivationSpec, target);

                    final Subscription subscription = connection.subscribe(((NATSActivationSpec) activationSpec).getSubject(), target);
                    target.setSubscription(subscription);
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
        final NATSActivationSpec natsActivationSpec = (NATSActivationSpec) activationSpec;

        final EndpointTarget endpointTarget = targets.get(natsActivationSpec);
        if (endpointTarget == null) {
            throw new IllegalStateException("No EndpointTarget to undeploy for ActivationSpec " + activationSpec);
        }

        endpointTarget.close();
        endpointTarget.messageEndpoint.release();
    }

    public XAResource[] getXAResources(ActivationSpec[] activationSpecs) throws ResourceException {
        return new XAResource[0];
    }

    public void publish(final String subject, final byte[] data) throws NATSException {
        try {
            connection.publish(subject, data);
        } catch (Exception e) {
            throw new NATSException(e);
        }
    }


    private static class EndpointTarget implements MessageHandler {
        private final MessageEndpoint messageEndpoint;
        private Subscription subscription;

        public EndpointTarget(final MessageEndpoint messageEndpoint) {
            this.messageEndpoint = messageEndpoint;
        }

        @Override
        public void onMessage(final Message msg) {
            try {
                try {
                    messageEndpoint.beforeDelivery(ONMESSAGE);

                    final NATSMessage message = (NATSMessage) Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class[]{NATSMessage.class},
                            new InvocationHandler() {
                                @Override
                                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                                    final Method m = Message.class.getMethod(method.getName(), method.getParameterTypes());
                                    return m.invoke(msg, args);
                                }
                            }
                    );

                    ((InboundListener) messageEndpoint).onMessage(message);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    messageEndpoint.afterDelivery();
                }
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "Error dispatching message from NATS to MDB endpoint", t);
            }
        }

        public void setSubscription(final Subscription subscription) {
            this.subscription = subscription;
        }

        public Subscription getSubscription() {
            return subscription;
        }

        public void close() {
            try {
                if (subscription != null) {
                    subscription.close(true);
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error closing subscription to NATS subject", e);
            }
        }
    }
}
