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

import com.google.common.collect.Lists;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.endpoint.UserstreamEndpoint;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.BasicClient;
import com.twitter.hbc.httpclient.ControlStreamException;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;
import com.twitter.hbc.twitter4j.Twitter4jUserstreamClient;
import twitter4j.Status;
import twitter4j.UserStreamAdapter;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class TwitterStreamingClient {

    private final StatusChangeListener statusChangeListener;
    private final String consumerKey;
    private final String consumerSecret;
    private final String accessToken;
    private final String accessTokenSecret;
    private final UserStreamAdapter listener = new UserStreamAdapter() {
        @Override
        public void onStatus(final Status status) {
            statusChangeListener.onStatus(status);
        }
    };
    private Twitter4jUserstreamClient t4jClient;

    public TwitterStreamingClient(
            final StatusChangeListener statusChangeListener,
            final String consumerKey,
            final String consumerSecret,
            final String accessToken,
            final String accessTokenSecret) {

        this.statusChangeListener = statusChangeListener;
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.accessToken = accessToken;
        this.accessTokenSecret = accessTokenSecret;
    }

    public void run() throws InterruptedException, ControlStreamException, IOException {
        // Create an appropriately sized blocking queue
        final BlockingQueue<String> queue = new LinkedBlockingQueue<String>(10000);

        final UserstreamEndpoint endpoint = new UserstreamEndpoint();
        final Authentication auth = new OAuth1(consumerKey, consumerSecret, accessToken, accessTokenSecret);

        // Create a new BasicClient. By default gzip is enabled.
        final BasicClient client = new ClientBuilder()
                .hosts(Constants.SITESTREAM_HOST)
                .endpoint(endpoint)
                .authentication(auth)
                .processor(new StringDelimitedProcessor(queue))
                .build();

        // Create an executor service which will spawn threads to do the actual work of parsing the incoming messages and
        // calling the listeners on each message
        final int numProcessingThreads = 4;
        final ExecutorService service = Executors.newFixedThreadPool(numProcessingThreads);

        // Wrap our BasicClient with the twitter4j client
        t4jClient = new Twitter4jUserstreamClient(
                client, queue, Lists.newArrayList(listener), service);

        // Establish a connection
        t4jClient.connect();
        for (int threads = 0; threads < numProcessingThreads; threads++) {
            // This must be called once per processing thread
            t4jClient.process();
        }
    }

    public void stop() {
        t4jClient.stop();
    }
}
