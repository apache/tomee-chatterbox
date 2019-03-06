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
package org.apache.tomee.chatterbox.imap.adapter;

import org.apache.tomee.chatterbox.imap.api.Body;
import org.apache.tomee.chatterbox.imap.api.BodyParam;
import org.apache.tomee.chatterbox.imap.api.From;
import org.apache.tomee.chatterbox.imap.api.FromParam;
import org.apache.tomee.chatterbox.imap.api.InvokeAllMatches;
import org.apache.tomee.chatterbox.imap.api.Subject;
import org.apache.tomee.chatterbox.imap.api.SubjectParam;
import org.tomitribe.util.Longs;
import org.tomitribe.util.editor.Converter;
import org.tomitribe.util.hash.XxHash64;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.resource.ResourceException;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.BootstrapContext;
import javax.resource.spi.ConfigProperty;
import javax.resource.spi.Connector;
import javax.resource.spi.ResourceAdapter;
import javax.resource.spi.ResourceAdapterInternalException;
import javax.resource.spi.endpoint.MessageEndpoint;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.transaction.xa.XAResource;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Connector(
        description = "IMAP ResourceAdapter",
        displayName = "IMAP ResourceAdapter",
        eisType = "IMAP Adapter",
        version = "1.0")
public class ImapResourceAdapter implements ResourceAdapter {

    private static final Logger LOGGER = Logger.getLogger(ImapResourceAdapter.class.getName());

    final Map<ImapActivationSpec, EndpointTarget> targets = new ConcurrentHashMap<>();

    private ImapCheckThread worker;

    @ConfigProperty
    private String host;

    @ConfigProperty(defaultValue = "993")
    private Integer port;

    @ConfigProperty
    private String username;

    @ConfigProperty
    private String password;

    @ConfigProperty(defaultValue = "imaps")
    private String protocol;

    @ConfigProperty(defaultValue = "FINE")
    private String deliveryLogLevel;

    private Level level;

    private static Object[] getValues(final Method method, final String sender, final String subject, final String message) {

        if (method == null) {
            return null;
        }

        final Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) {
            return new Object[0];
        }

        final Template senderTemplate = getTemplate(method.getAnnotation(From.class));
        final Map<String, List<String>> senderParamValues = new HashMap<>();
        if (senderTemplate != null) {
            senderTemplate.match(sender, senderParamValues);
        }

        final Template messageTextTemplate = getTemplate(method.getAnnotation(Body.class));
        final Map<String, List<String>> messageTextParamValues = new HashMap<>();
        if (messageTextTemplate != null) {
            messageTextTemplate.match(message, messageTextParamValues);
        }

        final Template subjectTemplate = getTemplate(method.getAnnotation(Subject.class));
        final Map<String, List<String>> subjectParamValues = new HashMap<>();
        if (subjectTemplate != null) {
            subjectTemplate.match(subject, subjectParamValues);
        }

        final Object[] values = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            final Parameter parameter = parameters[i];

            values[i] = null;

            if (parameter.isAnnotationPresent(FromParam.class)) {
                final FromParam senderParam = parameter.getAnnotation(FromParam.class);
                if (senderParam.value() == null || senderParam.value().length() == 0) {
                    values[i] = Converter.convert(sender, parameter.getType(), null);
                } else {
                    final List<String> paramValues = senderParamValues.get(senderParam.value());
                    final String paramValue = paramValues == null || paramValues.size() == 0 ? null : paramValues.get(0);
                    values[i] = Converter.convert(paramValue, parameter.getType(), null);
                }
            }

            if (parameter.isAnnotationPresent(BodyParam.class)) {
                final BodyParam messageTextParam = parameter.getAnnotation(BodyParam.class);
                if (messageTextParam.value() == null || messageTextParam.value().length() == 0) {
                    values[i] = Converter.convert(message, parameter.getType(), null);
                } else {
                    final List<String> paramValues = messageTextParamValues.get(messageTextParam.value());
                    final String paramValue = paramValues == null || paramValues.size() == 0 ? null : paramValues.get(0);
                    values[i] = Converter.convert(paramValue, parameter.getType(), null);
                }
            }

            if (parameter.isAnnotationPresent(SubjectParam.class)) {
                final SubjectParam subjectParam = parameter.getAnnotation(SubjectParam.class);
                if (subjectParam.value() == null || subjectParam.value().length() == 0) {
                    values[i] = Converter.convert(subject, parameter.getType(), null);
                } else {
                    final List<String> paramValues = messageTextParamValues.get(subjectParam.value());
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

    private static String getMessageText(final Message message) {
        try {
            if (message instanceof MimeMessage) {
                final MimeMessage m = (MimeMessage) message;
                Object contentObject = m.getContent();
                if (contentObject instanceof Multipart) {
                    BodyPart clearTextPart = null;

                    Multipart content = (Multipart) contentObject;
                    int count = content.getCount();
                    for (int i = 0; i < count; i++) {
                        BodyPart part = content.getBodyPart(i);
                        if (part.isMimeType("text/plain")) {
                            clearTextPart = part;
                            break;
                        }
                    }

                    if (clearTextPart != null) {
                        return (String) clearTextPart.getContent();
                    }

                } else if (contentObject instanceof String) {
                    return (String) contentObject;
                } else {
                    LOGGER.log(Level.WARNING, "Unable to get message text");
                    return "";
                }
            }
        } catch (IOException | MessagingException e) {
            LOGGER.log(Level.WARNING, "Unable to get message text");
        }

        return "";
    }

    public void start(BootstrapContext bootstrapContext) throws ResourceAdapterInternalException {
        level = Level.parse(deliveryLogLevel);
        LOGGER.info("Starting " + this);
        worker = new ImapCheckThread(this);
        worker.start();
    }

    public void stop() {
        LOGGER.info("Stopping " + this);
        worker.cancel();
    }

    public void endpointActivation(final MessageEndpointFactory messageEndpointFactory, final ActivationSpec activationSpec)
            throws ResourceException {
        final ImapActivationSpec imapActivationSpec = (ImapActivationSpec) activationSpec;
        final MessageEndpoint messageEndpoint = messageEndpointFactory.createEndpoint(null);

        final Class<?> endpointClass = imapActivationSpec.getBeanClass() != null ? imapActivationSpec
                .getBeanClass() : messageEndpointFactory.getEndpointClass();

        final EndpointTarget target = new EndpointTarget(messageEndpoint, endpointClass);
        targets.put(imapActivationSpec, target);

    }

    public void endpointDeactivation(final MessageEndpointFactory messageEndpointFactory, final ActivationSpec activationSpec) {
        final ImapActivationSpec imapActivationSpec = (ImapActivationSpec) activationSpec;

        final EndpointTarget endpointTarget = targets.get(imapActivationSpec);
        if (endpointTarget == null) {
            throw new IllegalStateException("No EndpointTarget to undeploy for ActivationSpec " + activationSpec);
        }

        endpointTarget.messageEndpoint.release();
    }

    public XAResource[] getXAResources(ActivationSpec[] activationSpecs) throws ResourceException {
        return new XAResource[0];
    }

    public void process(final Message message) {
        final Collection<EndpointTarget> endpoints = targets.values();
        for (final EndpointTarget endpoint : endpoints) {
            endpoint.invoke(message);
        }
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

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    @Override
    public String toString() {
        return "ImapResourceAdapter{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", username='" + username + '\'' +
                ", password='" + Longs.toHex(XxHash64.hash(password)) + '\'' +
                ", protocol='" + protocol + '\'' +
                '}';
    }

    private static class Email {
        private final String sent;
        private final String to;
        private final String from;
        private final String subject;

        private Email(final Message message) throws MessagingException {
            final Address[] recipients = message.getRecipients(Message.RecipientType.TO);
            this.to = recipients[0].toString();
            this.from = message.getFrom()[0].toString();
            this.sent = format(message.getSentDate());
            this.subject = message.getSubject();
        }

        private String format(Date sentDate) {
            final SimpleDateFormat simpleDateFormat = new SimpleDateFormat();
            return simpleDateFormat.format(sentDate);
        }

        @Override
        public String toString() {
            return "Email{" +
                    "sent='" + sent + '\'' +
                    ", to='" + to + '\'' +
                    ", from='" + from + '\'' +
                    ", subject='" + subject + '\'' +
                    '}';
        }
    }

    public class EndpointTarget {
        private final MessageEndpoint messageEndpoint;
        private final Class<?> clazz;

        public EndpointTarget(final MessageEndpoint messageEndpoint, final Class<?> clazz) {
            this.messageEndpoint = messageEndpoint;
            this.clazz = clazz;
        }

        public void invoke(Message message) {

            // Wrapper for convenient logging
            final Email email;
            try {
                email = new Email(message);
            } catch (MessagingException e) {
                throw new IllegalStateException(e);
            }

            // find matching method(s)

            final List<Method> matchingMethods =
                    Arrays.asList(clazz.getDeclaredMethods())
                            .stream()
                            .sorted((m1, m2) -> m1.toString().compareTo(m2.toString()))
                            .filter(this::isPublic)
                            .filter(this::isNotFinal)
                            .filter(this::isNotAbstract)
                            .filter(m -> filterSender(message, m))
                            .filter(m -> filterSubject(message, m))
                            .filter(m -> filterMessage(message, m))
                            .collect(Collectors.toList());

            if (matchingMethods == null || matchingMethods.size() == 0) {
                LOGGER.log(Level.INFO, "No method to match " + email);
                return;
            }

            if (this.clazz.isAnnotationPresent(InvokeAllMatches.class)) {
                for (final Method method : matchingMethods) {
                    LOGGER.log(level, "Invoking method " + method.toString() + " for " + email);
                    try {
                        invoke(method, InternetAddress.toString(message.getFrom()),
                                message.getSubject(),
                                getMessageText(message));
                    } catch (MessagingException e) {
                        LOGGER.log(Level.SEVERE, "Unable to invoke method " + method.toString() + " for " + email);
                    }
                }
            } else {
                final Method method = matchingMethods.get(0);
                LOGGER.log(level, "Invoking method " + method.toString() + " for " + email);
                try {
                    invoke(method, InternetAddress.toString(message.getFrom()),
                            message.getSubject(),
                            getMessageText(message));
                } catch (MessagingException e) {
                    LOGGER.log(Level.SEVERE, "Unable to invoke method " + method.toString() + " for " + email);
                }
            }
        }

        private boolean filterMessage(final Message message, final Method m) {
            try {
                final String messageBody = message.getContent().toString();

                return !m.isAnnotationPresent(Body.class) || "".equals(m.getAnnotation(Body.class).value())
                        || templateMatches(m.getAnnotation(Body.class).value(), messageBody);

            } catch (IOException | MessagingException e) {
                return false;
            }
        }

        private boolean filterSender(final Message message, final Method m) {
            try {
                final String sender = InternetAddress.toString(message.getFrom());

                return !m.isAnnotationPresent(From.class) || "".equals(m.getAnnotation(From.class).value())
                        || templateMatches(m.getAnnotation(From.class).value(), sender);

            } catch (MessagingException e) {
                return false;
            }
        }

        private boolean filterSubject(final Message message, final Method m) {
            try {
                final String subject = message.getSubject();

                return !m.isAnnotationPresent(Subject.class) || "".equals(m.getAnnotation(Subject.class).value())
                        || templateMatches(m.getAnnotation(Subject.class).value(), subject);

            } catch (MessagingException e) {
                return false;
            }
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

        private void invoke(final Method method, final String sender, final String subject, final String message) {
            try {
                try {
                    messageEndpoint.beforeDelivery(method);
                    final Object[] values = getValues(method, sender, subject, message);
                    method.invoke(messageEndpoint, values);
                } finally {
                    messageEndpoint.afterDelivery();
                }
            } catch (final NoSuchMethodException | ResourceException | IllegalAccessException | InvocationTargetException e) {
                LOGGER.log(Level.SEVERE, "Unable to call method: " + method.toString());
            }
        }
    }
}
