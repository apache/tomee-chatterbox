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
package org.apache.tomee.chatterbox.slack.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import flowctrl.integration.slack.SlackClientFactory;
import flowctrl.integration.slack.rtm.Event;
import flowctrl.integration.slack.rtm.EventListener;
import flowctrl.integration.slack.rtm.SlackRealTimeMessagingClient;
import flowctrl.integration.slack.type.Authentication;
import flowctrl.integration.slack.type.Presence;
import flowctrl.integration.slack.webapi.SlackWebApiClient;
import flowctrl.integration.slack.webapi.method.chats.ChatPostMessageMethod;
import org.tomitribe.crest.Main;
import org.tomitribe.crest.cmds.Cmd;
import org.tomitribe.crest.cmds.processors.Commands;
import org.tomitribe.crest.cmds.targets.Target;
import org.tomitribe.crest.environments.Environment;

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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Connector(description = "Sample Resource Adapter", displayName = "Sample Resource Adapter", eisType = "Sample Resource Adapter", version = "1.0")
public class SlackResourceAdapter implements ResourceAdapter, EventListener {
    final Map<SlackActivationSpec, EndpointTarget> targets = new ConcurrentHashMap<SlackActivationSpec, EndpointTarget>();

    @ConfigProperty
    private String token;

    private SlackRealTimeMessagingClient slackRealTimeMessagingClient;
    private SlackWebApiClient webApiClient;
    private Main main;
    private WorkManager workManager;
    private String user;
    private String userId;

    public void start(BootstrapContext bootstrapContext) throws ResourceAdapterInternalException {
        workManager = bootstrapContext.getWorkManager();
        webApiClient = SlackClientFactory.createWebApiClient(token);
        slackRealTimeMessagingClient = SlackClientFactory.createSlackRealTimeMessagingClient(token);

        final Authentication authentication = webApiClient.auth();
        user = authentication.getUser();
        userId = authentication.getUser_id();

        webApiClient.setPresenceUser(Presence.AUTO);

        slackRealTimeMessagingClient.addListener(Event.MESSAGE, this);
        slackRealTimeMessagingClient.connect();
        main = new Main();
    }

    public void stop() {
        webApiClient.setPresenceUser(Presence.AWAY);
    }

    public void endpointActivation(final MessageEndpointFactory messageEndpointFactory, final ActivationSpec activationSpec)
            throws ResourceException {
        final SlackActivationSpec slackActivationSpec = (SlackActivationSpec) activationSpec;

        workManager.scheduleWork(new Work() {

            @Override
            public void run() {
                try {
                    final MessageEndpoint messageEndpoint = messageEndpointFactory.createEndpoint(null);

                    final EndpointTarget target = new EndpointTarget(messageEndpoint);
                    final Class<?> endpointClass = slackActivationSpec.getBeanClass() != null ? slackActivationSpec
                            .getBeanClass() : messageEndpointFactory.getEndpointClass();

                    target.commands.addAll(Commands.get(endpointClass, target, null).values());

                    for (Cmd cmd : target.commands) {
                        main.add(cmd);
                    }

                    targets.put(slackActivationSpec, target);
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
        final SlackActivationSpec telnetActivationSpec = (SlackActivationSpec) activationSpec;

        final EndpointTarget endpointTarget = targets.get(telnetActivationSpec);
        if (endpointTarget == null) {
            throw new IllegalStateException("No EndpointTarget to undeploy for ActivationSpec " + activationSpec);
        }

        final List<Cmd> commands = telnetActivationSpec.getCommands();
        for (Cmd command : commands) {
            main.remove(command);
        }

        endpointTarget.messageEndpoint.release();
    }

    public XAResource[] getXAResources(ActivationSpec[] activationSpecs) throws ResourceException {
        return new XAResource[0];
    }

    public void sendMessage(final String channel, final String message) {
        ChatPostMessageMethod postMessage = new ChatPostMessageMethod(channel, message);
        postMessage.setUsername(user);
        webApiClient.postMessage(postMessage);
    }

    @Override
    public void handleMessage(final JsonNode jsonNode) {

        String commandLine = jsonNode.get("text").textValue();
        final String userPrefix = "<@" + userId + ">";
        if (!commandLine.startsWith(userPrefix)) {
            return;
        }

        // chop off the user: prefix
        commandLine = commandLine.replaceAll("^" + userPrefix + ":?\\s*", "");

        final String channel = jsonNode.get("channel").textValue();

        String[] args;
        final Arguments[] arguments = ArgumentsParser.parse(commandLine);
        if (arguments == null || arguments.length == 0) {
            args = new String[]{"help"};
        } else {
            args = arguments[0].get();
        }

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        final PrintStream ps = new PrintStream(os);
        final Environment environment = new Environment() {
            @Override
            public PrintStream getOutput() {
                return ps;
            }

            @Override
            public PrintStream getError() {
                return ps;
            }

            @Override
            public InputStream getInput() {
                return null;
            }

            @Override
            public Properties getProperties() {
                return System.getProperties();
            }
        };

        try {
            main.main(environment, args);
        } catch (Exception e) {
            e.printStackTrace(ps);
        }

        ChatPostMessageMethod postMessage = new ChatPostMessageMethod(channel, new String(os.toByteArray()));
        postMessage.setUsername(user);
        webApiClient.postMessage(postMessage);
    }

    public String getToken() {
        return token;
    }

    public void setToken(final String token) {
        this.token = token;
    }

    private static class EndpointTarget implements Target {
        private final MessageEndpoint messageEndpoint;
        private final List<Cmd> commands = new ArrayList<Cmd>();

        public EndpointTarget(MessageEndpoint messageEndpoint) {
            this.messageEndpoint = messageEndpoint;
        }

        @Override
        public Object invoke(Method method, Object... objects)
                throws InvocationTargetException, IllegalAccessException {

            try {
                try {
                    messageEndpoint.beforeDelivery(method);

                    return method.invoke(messageEndpoint, objects);
                } finally {
                    messageEndpoint.afterDelivery();
                }
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (ResourceException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
