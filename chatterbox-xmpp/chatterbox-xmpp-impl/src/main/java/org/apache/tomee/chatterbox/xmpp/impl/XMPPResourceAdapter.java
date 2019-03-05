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

import org.apache.tomee.chatterbox.xmpp.api.MessageException;
import org.apache.tomee.chatterbox.xmpp.api.inflow.InvokeAllMatches;
import org.apache.tomee.chatterbox.xmpp.api.inflow.MessageText;
import org.apache.tomee.chatterbox.xmpp.api.inflow.MessageTextParam;
import org.apache.tomee.chatterbox.xmpp.api.inflow.Sender;
import org.apache.tomee.chatterbox.xmpp.api.inflow.SenderParam;
import org.apache.tomee.chatterbox.xmpp.impl.inflow.XMPPActivationSpec;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.tomitribe.util.editor.Converter;

import javax.resource.ResourceException;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.BootstrapContext;
import javax.resource.spi.ConfigProperty;
import javax.resource.spi.Connector;
import javax.resource.spi.ResourceAdapter;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.resource.spi.TransactionSupport;
import javax.resource.spi.endpoint.MessageEndpoint;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.transaction.xa.XAResource;
import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Connector(
        reauthenticationSupport = false,
        transactionSupport = TransactionSupport.TransactionSupportLevel.NoTransaction,
        displayName = "XMPPConnector", vendorName = "Apache Software Foundation", version = "1.0")
public class XMPPResourceAdapter implements ResourceAdapter, Serializable, MessageListener, ChatManagerListener {

    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(XMPPResourceAdapter.class.getName());

    final Map<XMPPActivationSpec, EndpointTarget> targets = new ConcurrentHashMap<XMPPActivationSpec, EndpointTarget>();

    @ConfigProperty(defaultValue = "localhost")
    private String host;

    @ConfigProperty(defaultValue = "5222")
    private Integer port;

    @ConfigProperty
    private String username;

    @ConfigProperty
    private String password;

    @ConfigProperty
    private String serviceName;

    private XMPPTCPConnection connection;
    private ChatManager chatmanager;
    private boolean connected = false;

    private static Object[] getValues(final Method method, final String sender, final String message) {

        if (method == null) {
            return null;
        }

        final Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) {
            return new Object[0];
        }

        final Template senderTemplate = getTemplate(method.getAnnotation(Sender.class));
        final Map<String, List<String>> senderParamValues = new HashMap<>();
        if (senderTemplate != null) {
            senderTemplate.match(sender, senderParamValues);
        }

        final Template messageTextTemplate = getTemplate(method.getAnnotation(MessageText.class));
        final Map<String, List<String>> messageTextParamValues = new HashMap<>();
        if (messageTextTemplate != null) {
            messageTextTemplate.match(message, messageTextParamValues);
        }

        final Object[] values = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            final Parameter parameter = parameters[i];

            values[i] = null;

            if (parameter.isAnnotationPresent(SenderParam.class)) {
                final SenderParam senderParam = parameter.getAnnotation(SenderParam.class);
                if (senderParam.value() == null || senderParam.value().length() == 0) {
                    values[i] = Converter.convert(sender, parameter.getType(), null);
                } else {
                    final List<String> paramValues = senderParamValues.get(senderParam.value());
                    final String paramValue = paramValues == null || paramValues.size() == 0 ? null : paramValues.get(0);
                    values[i] = Converter.convert(paramValue, parameter.getType(), null);
                }
            }

            if (parameter.isAnnotationPresent(MessageTextParam.class)) {
                final MessageTextParam messageTextParam = parameter.getAnnotation(MessageTextParam.class);
                if (messageTextParam.value() == null || messageTextParam.value().length() == 0) {
                    values[i] = Converter.convert(message, parameter.getType(), null);
                } else {
                    final List<String> paramValues = messageTextParamValues.get(messageTextParam.value());
                    final String paramValue = paramValues == null || paramValues.size() == 0 ? null : paramValues.get(0);
                    values[i] = Converter.convert(paramValue, parameter.getType(), null);
                }
            }
        }

        return values;
    }

    private static Template getTemplate(final Annotation annotation) {
        if (annotation == null) {
            return null;
        }

        try {

            final Method patternMethod = annotation.getClass().getMethod("value");
            if (patternMethod == null) {
                return null;
            }

            if (!String.class.equals(patternMethod.getReturnType())) {
                return null;
            }

            final String pattern = (String) patternMethod.invoke(annotation);
            return new Template(pattern);
        } catch (final Exception e) {
            // ignore
        }

        return null;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void endpointActivation(final MessageEndpointFactory messageEndpointFactory, final ActivationSpec activationSpec)
            throws ResourceException {
        final XMPPActivationSpec xmppActivationSpec = (XMPPActivationSpec) activationSpec;
        final MessageEndpoint messageEndpoint = messageEndpointFactory.createEndpoint(null);

        final Class<?> endpointClass = xmppActivationSpec.getBeanClass() != null ? xmppActivationSpec
                .getBeanClass() : messageEndpointFactory.getEndpointClass();

        final EndpointTarget target = new EndpointTarget(messageEndpoint, endpointClass);
        targets.put(xmppActivationSpec, target);

    }

    public void endpointDeactivation(final MessageEndpointFactory messageEndpointFactory, final ActivationSpec activationSpec) {
        final XMPPActivationSpec xmppActivationSpec = (XMPPActivationSpec) activationSpec;

        final EndpointTarget endpointTarget = targets.get(xmppActivationSpec);
        if (endpointTarget == null) {
            throw new IllegalStateException("No EndpointTarget to undeploy for ActivationSpec " + activationSpec);
        }

        endpointTarget.messageEndpoint.release();
    }

    public void start(BootstrapContext ctx)
            throws ResourceAdapterInternalException {
        LOGGER.info("Starting " + this);
        connect();
    }

    public void stop() {
        LOGGER.info("Stopping " + this);
        disconnect();
    }

    public void connect() {
        ConnectionConfiguration connConfig = new ConnectionConfiguration(host, port, serviceName);
        connection = new XMPPTCPConnection(connConfig);

        try {
            connection.connect();
            LOGGER.finest("Connected to " + host + ":" + port + "/" + serviceName);
        } catch (XMPPException | SmackException | IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to connect to " + host + ":" + port + "/" + serviceName, e);
        }

        try {
            connection.login(username, password);
            LOGGER.finest("Logged in as " + username);

            Presence presence = new Presence(Presence.Type.available);
            connection.sendPacket(presence);

        } catch (XMPPException | SmackException | IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to login as " + username, e);
        }

        connected = true;
        chatmanager = ChatManager.getInstanceFor(connection);
        chatmanager.addChatListener(this);
    }

    public void disconnect() {
        try {
            if (connected) {
                chatmanager.removeChatListener(this);
                connection.disconnect(new Presence(Presence.Type.unavailable));
            }
        } catch (SmackException.NotConnectedException e) {
            LOGGER.log(Level.SEVERE, "Unable to logout", e);
        }

        connection = null;
        chatmanager = null;
        connected = false;

    }

    public void sendXMPPMessage(String recipient, String message) throws MessageException {
        Chat newChat = chatmanager.createChat(recipient, this);

        try {
            newChat.sendMessage(message);
        } catch (XMPPException | SmackException.NotConnectedException e) {
            throw new MessageException(e);
        }
    }

    public XAResource[] getXAResources(ActivationSpec[] specs)
            throws ResourceException {
        LOGGER.finest("getXAResources()");
        return null;
    }

    @Override
    public void processMessage(Chat chat, Message message) {

        for (EndpointTarget endpointTarget : targets.values()) {
            endpointTarget.invoke(chat, message);
        }

        chat.removeMessageListener(this);
        chat.close();
    }

    @Override
    public void chatCreated(Chat chat, boolean b) {
        chat.addMessageListener(this);
    }

    @Override
    public String toString() {
        return "XMPPResourceAdapter{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", serviceName='" + serviceName + '\'' +
                '}';
    }

    public static class EndpointTarget {
        private final MessageEndpoint messageEndpoint;
        private final Class<?> clazz;

        public EndpointTarget(final MessageEndpoint messageEndpoint, final Class<?> clazz) {
            this.messageEndpoint = messageEndpoint;
            this.clazz = clazz;
        }

        public void invoke(Chat chat, Message message) {

            // This method will be invoked essentially each keystroke made by the remote user
            // We don't need to bother with this delivery logic until the user has pressed enter
            // and fully sent the message.
            if (message.getBody() == null) return;

            // This chat message object exists for clean logging.
            final ChatMessage chatMessage = new ChatMessage(chat, message);

            // find matching method(s)

            final List<Method> matchingMethods =
                    Arrays.asList(clazz.getDeclaredMethods())
                            .stream()
                            .sorted((m1, m2) -> m1.toString().compareTo(m2.toString()))
                            .filter(this::isPublic)
                            .filter(this::isNotFinal)
                            .filter(this::isNotAbstract)
                            .filter(m -> filterSender(chat.getParticipant(), m))
                            .filter(m -> filterMessage(message.getBody(), m))
                            .collect(Collectors.toList());

            if (matchingMethods == null || matchingMethods.size() == 0) {
                LOGGER.log(Level.INFO, "No method to match " + chatMessage);
                return;
            }

            if (this.clazz.isAnnotationPresent(InvokeAllMatches.class)) {
                for (final Method method : matchingMethods) {
                    LOGGER.log(Level.INFO, "Invoking method " + method.toString() + " for " + chatMessage);
                    invoke(method, chat.getParticipant(), message.getBody());
                }
            } else {
                final Method method = matchingMethods.get(0);
                LOGGER.log(Level.INFO, "Invoking method " + method.toString() + " for " + chatMessage);
                invoke(method, chat.getParticipant(), message.getBody());
            }
        }

        private boolean filterMessage(final String message, final Method m) {
            return !m.isAnnotationPresent(MessageText.class) || "".equals(m.getAnnotation(MessageText.class).value())
                    || templateMatches(m.getAnnotation(MessageText.class).value(), message);
        }

        private boolean filterSender(final String sender, final Method m) {
            return !m.isAnnotationPresent(Sender.class) || "".equals(m.getAnnotation(Sender.class).value())
                    || templateMatches(m.getAnnotation(Sender.class).value(), sender);
        }

        private boolean templateMatches(final String pattern, final String input) {
            try {
                if (Pattern.matches(pattern, input)) {
                    return true;
                }
            } catch (Exception e) {
                // ignore
            }

            final Template template = new Template(pattern);
            final Map<String, List<String>> values = new HashMap<>();
            return template.match(input, values);
        }

        private boolean isPublic(final Method m) {
            return Modifier.isPublic(m.getModifiers());
        }

        private boolean isNotAbstract(final Method m) {
            return !Modifier.isAbstract(m.getModifiers());
        }

        private boolean isNotFinal(final Method m) {
            return !Modifier.isFinal(m.getModifiers());
        }

        private void invoke(final Method method, final String sender, final String message) {
            try {
                try {
                    messageEndpoint.beforeDelivery(method);
                    final Object[] values = getValues(method, sender, message);
                    method.invoke(messageEndpoint, values);
                } finally {
                    messageEndpoint.afterDelivery();
                }
            } catch (final NoSuchMethodException | ResourceException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class ChatMessage {
        private final String sender;
        private final String text;

        public ChatMessage(Chat chat, Message message) {
            this.sender = chat.getParticipant();
            this.text = message.getBody();
        }

        @Override
        public String toString() {
            return "ChatMessage{" +
                    "sender='" + sender + '\'' +
                    ", text='" + text + '\'' +
                    '}';
        }
    }
}
