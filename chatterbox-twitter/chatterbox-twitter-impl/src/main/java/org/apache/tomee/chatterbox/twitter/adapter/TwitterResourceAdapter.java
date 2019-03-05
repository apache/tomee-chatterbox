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
package org.apache.tomee.chatterbox.twitter.adapter;

import com.twitter.hbc.httpclient.ControlStreamException;
import org.apache.tomee.chatterbox.twitter.api.InvokeAllMatches;
import org.apache.tomee.chatterbox.twitter.api.Response;
import org.apache.tomee.chatterbox.twitter.api.Tweet;
import org.apache.tomee.chatterbox.twitter.api.TweetParam;
import org.apache.tomee.chatterbox.twitter.api.User;
import org.apache.tomee.chatterbox.twitter.api.UserParam;
import org.tomitribe.util.editor.Converter;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;

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
import javax.validation.constraints.NotNull;
import java.io.IOException;
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

@Connector(description = "Twitter Resource Adapter", displayName = "Twitter Resource Adapter", eisType = "Twitter Resource Adapter", version = "1.0")
public class TwitterResourceAdapter implements ResourceAdapter, StatusChangeListener {

    private static final Logger LOGGER = Logger.getLogger(TwitterResourceAdapter.class.getName());
    private static final Map<String, Response> RESPONSE_MAP = new ConcurrentHashMap<>();
    final Map<TwitterActivationSpec, EndpointTarget> targets = new ConcurrentHashMap<TwitterActivationSpec, EndpointTarget>();
    private TwitterStreamingClient client;
    @ConfigProperty
    @NotNull
    private String consumerKey;
    @ConfigProperty
    @NotNull
    private String consumerSecret;
    @ConfigProperty
    @NotNull
    private String accessToken;
    @ConfigProperty
    @NotNull
    private String accessTokenSecret;
    private Twitter twitter;

    private static List<Method> findMatchingMethods(final Class<?> clazz, final Status status) {
        return Arrays.asList(clazz.getDeclaredMethods())
                .stream()
                .sorted((m1, m2) -> m1.toString().compareTo(m2.toString()))
                .filter(TwitterResourceAdapter::isPublic)
                .filter(TwitterResourceAdapter::isNotFinal)
                .filter(TwitterResourceAdapter::isNotAbstract)
                .filter(m -> filterTweet(status, m))
                .filter(m -> filterUser(status, m))
                .filter(m -> filterGetMethod(status, m))
                .collect(Collectors.toList());
    }

    private static boolean filterUser(final Status status, final Method m) {
        return !m.isAnnotationPresent(User.class) || "".equals(m.getAnnotation(User.class).value())
                || templateMatches(m.getAnnotation(User.class).value(), status.getUser().getScreenName());
    }

    private static boolean filterTweet(final Status status, final Method m) {
        return !m.isAnnotationPresent(Tweet.class) || "".equals(m.getAnnotation(Tweet.class).value())
                || templateMatches(m.getAnnotation(Tweet.class).value(), getNormalizedText(status));
    }

    private static String getNormalizedText(Status status) {
        String text = status.getText();
        while (text.startsWith("@")) {
            text = text.replaceFirst("@?(\\w){1,15}(\\s+)", "");
        }
        return text;
    }

    private static boolean filterGetMethod(final Status status, final Method m) {
        return !(Response.class.isAssignableFrom(m.getDeclaringClass())
                && "getMessage".equals(m.getName())
                && m.getParameterCount() == 0);
    }

    private static boolean templateMatches(final String pattern, final String input) {
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

    private static boolean isPublic(final Method m) {
        return Modifier.isPublic(m.getModifiers());
    }

    private static boolean isNotAbstract(final Method m) {
        return !Modifier.isAbstract(m.getModifiers());
    }

    private static boolean isNotFinal(final Method m) {
        return !Modifier.isFinal(m.getModifiers());
    }

    public static Object[] getValues(final Method method, final Status status) {

        if (method == null) {
            return null;
        }

        final Parameter[] parameters = method.getParameters();
        if (parameters.length == 0) {
            return new Object[0];
        }

        final Template tweetTemplate = getTemplate(method.getAnnotation(Tweet.class));
        final Map<String, List<String>> tweetParamValues = new HashMap<>();
        if (tweetTemplate != null) {
            tweetTemplate.match(getNormalizedText(status), tweetParamValues);
        }

        final Template userTemplate = getTemplate(method.getAnnotation(User.class));
        final Map<String, List<String>> userParamValues = new HashMap<>();
        if (userTemplate != null) {
            userTemplate.match(status.getUser().getScreenName(), userParamValues);
        }

        final Object[] values = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            final Parameter parameter = parameters[i];

            values[i] = null;

            if (parameter.isAnnotationPresent(TweetParam.class)) {
                final TweetParam tweetParam = parameter.getAnnotation(TweetParam.class);
                if (tweetParam.value() == null || tweetParam.value().length() == 0) {
                    values[i] = Converter.convert(getNormalizedText(status), parameter.getType(), null);
                } else {
                    final List<String> paramValues = tweetParamValues.get(tweetParam.value());
                    final String paramValue = paramValues == null || paramValues.size() == 0 ? null : paramValues.get(0);
                    values[i] = Converter.convert(paramValue, parameter.getType(), null);
                }
            }

            if (parameter.isAnnotationPresent(UserParam.class)) {
                final UserParam userParam = parameter.getAnnotation(UserParam.class);
                if (userParam.value() == null || userParam.value().length() == 0) {
                    values[i] = Converter.convert(status.getUser().getScreenName(), parameter.getType(), null);
                } else {
                    final List<String> paramValues = userParamValues.get(userParam.value());
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

    public void start(final BootstrapContext bootstrapContext) throws ResourceAdapterInternalException {

        LOGGER.info("Starting " + this);

        client = new TwitterStreamingClient(this, consumerKey, consumerSecret, accessToken, accessTokenSecret);
        twitter = new TwitterFactory().getInstance();
        twitter.setOAuthConsumer(consumerKey, consumerSecret);
        twitter.setOAuthAccessToken(new AccessToken(accessToken, accessTokenSecret));


        try {
            client.run();
        } catch (InterruptedException | ControlStreamException | IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        LOGGER.info("Stopping " + this);
        client.stop();
    }

    public void endpointActivation(final MessageEndpointFactory messageEndpointFactory, final ActivationSpec activationSpec)
            throws ResourceException {
        final TwitterActivationSpec twitterActivationSpec = (TwitterActivationSpec) activationSpec;
        final MessageEndpoint messageEndpoint = messageEndpointFactory.createEndpoint(null);

        final Class<?> endpointClass = twitterActivationSpec.getBeanClass() != null ? twitterActivationSpec
                .getBeanClass() : messageEndpointFactory.getEndpointClass();

        LOGGER.info("Deploying " + endpointClass.getName());

        final EndpointTarget target = new EndpointTarget(messageEndpoint, endpointClass);
        targets.put(twitterActivationSpec, target);

    }

    public void endpointDeactivation(final MessageEndpointFactory messageEndpointFactory, final ActivationSpec activationSpec) {
        final TwitterActivationSpec twitterActivationSpec = (TwitterActivationSpec) activationSpec;

        final EndpointTarget endpointTarget = targets.get(twitterActivationSpec);
        if (endpointTarget == null) {
            throw new IllegalStateException("No EndpointTarget to undeploy for ActivationSpec " + activationSpec);
        }

        endpointTarget.messageEndpoint.release();
    }

    public XAResource[] getXAResources(final ActivationSpec[] activationSpecs) throws ResourceException {
        return new XAResource[0];
    }

    @Override
    public void onStatus(final Status status) {

        final String username = status.getUser().getScreenName();
        final Response response = RESPONSE_MAP.remove(username);

        if (response != null && response.getDialog() != null) {
            // pull the response object from the map

            final Object dialog = response.getDialog();

            try {
                final List<Method> matchingMethods = findMatchingMethods(dialog.getClass(), status);

                if (dialog.getClass().isAnnotationPresent(InvokeAllMatches.class)) {
                    for (final Method method : matchingMethods) {
                        LOGGER.log(Level.INFO, "Invoking method " + method.toString() + " for " + getNormalizedText(status));
                        final Object[] values = getValues(method, status);
                        final Object result = method.invoke(dialog, values);
                        processResponse(status, result);
                    }
                } else {
                    final Method method = matchingMethods.get(0);
                    LOGGER.log(Level.INFO, "Invoking method " + method.toString() + " for " + getNormalizedText(status));
                    final Object[] values = getValues(method, status);
                    final Object result = method.invoke(dialog, values);
                    processResponse(status, result);
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                LOGGER.severe("Unable to call response object:" + e.getMessage());
                RESPONSE_MAP.remove(status.getUser().getScreenName());
            }
        } else {
            for (final EndpointTarget endpointTarget : this.targets.values()) {
                endpointTarget.invoke(status);
            }
        }
    }

    private void processResponse(final Status status, final Object result) {
        if (Response.class.isInstance(result)) {
            final Response response = Response.class.cast(result);
            RESPONSE_MAP.put(status.getUser().getScreenName(), response);
            try {
                replyTo(status, response.getMessage());
            } catch (TwitterException e) {
                LOGGER.severe("Unable to send tweet" + e.getMessage());
            }
        } else {
            RESPONSE_MAP.remove(status.getUser().getScreenName());
        }

        if (String.class.isInstance(result)) {
            RESPONSE_MAP.remove(status.getUser().getScreenName());
            try {
                replyTo(status, String.class.cast(result));
            } catch (TwitterException e) {
                LOGGER.severe("Unable to send tweet" + e.getMessage());
            }
        }
    }

    private void replyTo(final Status status, final String reply) throws TwitterException {
        replyTo(status, reply, true);
    }

    private void replyTo(final Status status, final String reply, final boolean prefix) throws TwitterException {
        final String message;

        if (prefix) {
            message = "@" + status.getUser().getScreenName() + " " + reply;
        } else {
            message = reply;
        }

        final StatusUpdate statusUpdate = new StatusUpdate(message);
        statusUpdate.setInReplyToStatusId(status.getId());
        twitter.updateStatus(statusUpdate);
    }

    public void tweet(final String tweet) throws TwitterException {
        twitter.updateStatus(tweet);
    }

    @Override
    public String toString() {
        return "TwitterResourceAdapter{" +
                "consumerKey='" + consumerKey + '\'' +
                ", consumerSecret='" + consumerSecret + '\'' +
                ", accessToken='" + accessToken + '\'' +
                ", accessTokenSecret='" + accessTokenSecret + '\'' +
                '}';
    }

    public static class TweetWrapper {
        private final String user;
        private final String text;
        //        private final String


        public TweetWrapper(final Status status) {
            user = status.getUser().getScreenName();
            text = status.getText();
        }

        @Override
        public String toString() {
            return "Tweet{" +
                    "user='" + user + '\'' +
                    ", text='" + text + '\'' +
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

        public void invoke(final Status status) {

            // wrapper object for logging purposes
            final TweetWrapper tweet = new TweetWrapper(status);

            // find matching method(s)

            final List<Method> matchingMethods = findMatchingMethods(clazz, status);

            if (matchingMethods == null || matchingMethods.size() == 0) {
                // log this
                LOGGER.log(Level.INFO, "No method to match " + tweet);
                return;
            }

            if (this.clazz.isAnnotationPresent(InvokeAllMatches.class)) {
                for (final Method method : matchingMethods) {
                    LOGGER.log(Level.INFO, "Invoking method " + method.toString() + " for " + tweet);
                    invoke(method, status);
                }
            } else {
                final Method method = matchingMethods.get(0);
                LOGGER.log(Level.INFO, "Invoking method " + method.toString() + " for " + tweet);
                invoke(method, status);
            }
        }

        private void invoke(final Method method, final Status status) {
            try {
                try {
                    messageEndpoint.beforeDelivery(method);
                    final Object[] values = getValues(method, status);
                    final Object result = method.invoke(messageEndpoint, values);
                    processResponse(status, result);
                } finally {
                    messageEndpoint.afterDelivery();
                }
            } catch (final NoSuchMethodException | ResourceException | IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
