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

import org.apache.tomee.chatterbox.twitter.api.InvokeAllMatches;
import org.apache.tomee.chatterbox.twitter.api.Tweet;
import org.apache.tomee.chatterbox.twitter.api.TweetParam;
import org.apache.tomee.chatterbox.twitter.api.User;
import org.apache.tomee.chatterbox.twitter.api.UserParam;
import org.junit.Assert;
import org.junit.Test;
import org.superbiz.FavoriteHashTags;

import javax.resource.ResourceException;
import javax.resource.spi.endpoint.MessageEndpoint;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public class TwitterResourceAdapterTest {

    @Test
    public void testShouldInvokeMdb() throws Exception {

        final Class<?> clazz = TweetBean.class;
        final TwitterResourceAdapter.EndpointTarget endpointTarget = new TwitterResourceAdapter().new EndpointTarget(new MyTweetBean(), clazz);

        endpointTarget.invoke(new TestStatus("Testing connectors on #TomEE for #JavaOne", "jongallimore"));
    }

    @Test
    public void valuesSimple() throws Exception {
        final Object o = new Object() {
            @Tweet("do you like {thing}\\?")
            public String like(@TweetParam("thing") String thing) {
                return "I'm not sure if I like " + thing;
            }
        };

        final Method method = o.getClass().getMethod("like", String.class);
        final Object[] values = TwitterResourceAdapter.getValues(method, new TestStatus("@Bot @Bot do you like candy?", "Joe"));

        Assert.assertEquals(1, values.length);
        Assert.assertEquals("candy", values[0]);
    }

    @Test
    public void valuesBetweenSpaces() throws Exception {
        final Object o = new Object() {
            @Tweet("what is {a} ?[*x] ?{b}")
            public String math(@TweetParam("a") int a, @TweetParam("b") int b) {
                return null;
            }
        };

        final Method method = o.getClass().getMethod("math", int.class, int.class);

        {
            final Object[] values = TwitterResourceAdapter.getValues(method, new TestStatus("what is 4 x 7", "Joe"));
            Assert.assertEquals(2, values.length);
            Assert.assertEquals(new Integer(4), values[0]);
            Assert.assertEquals(new Integer(7), values[1]);
        }
    }


    private static class MyTweetBean extends TweetBean implements MessageEndpoint {

        @Override
        public void beforeDelivery(final Method method) throws NoSuchMethodException, ResourceException {

        }

        @Override
        public void afterDelivery() throws ResourceException {

        }

        @Override
        public void release() {

        }
    }

    @InvokeAllMatches
    private static class TweetBean {

        private final static Logger LOGGER = Logger.getLogger(FavoriteHashTags.class.getName());

        @Tweet(".*#TomEE.*")
        public void tomeeStatus(@TweetParam final String status, @UserParam final String user) {
            LOGGER.info(String.format("New status: %s, by %s", status, user));
        }

        @Tweet(".*#JavaOne.*")
        public void javaoneStatus(@TweetParam final String status, @UserParam final String user) {
            LOGGER.info(String.format("New JavaOne status: %s, by %s", status, user));
        }

        @User(".*jongallimore.*")
        public void asfStatus(@TweetParam final String status, @UserParam final String user) {
            LOGGER.info(String.format("New ASF status: %s, by %s", status, user));
        }
    }


}
