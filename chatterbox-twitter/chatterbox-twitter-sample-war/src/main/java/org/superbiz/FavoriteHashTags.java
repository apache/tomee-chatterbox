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
package org.superbiz;

import org.apache.tomee.chatterbox.twitter.api.Tweet;
import org.apache.tomee.chatterbox.twitter.api.TweetParam;
import org.apache.tomee.chatterbox.twitter.api.TwitterUpdates;
import org.apache.tomee.chatterbox.twitter.api.UserParam;

import javax.ejb.MessageDriven;
import java.util.logging.Logger;

@MessageDriven(name = "Status")
public class FavoriteHashTags implements TwitterUpdates {

    private final static Logger LOGGER = Logger.getLogger(FavoriteHashTags.class.getName());

    @Tweet(".*#TomEE.*")
    public String tomeeStatus(@TweetParam final String status, @UserParam final String user) {

        LOGGER.info(String.format("New status: %s, by %s", status, user));

        return "#TomEE is awesome";
    }

    @Tweet(".*#JavaLand.*")
    public String javaLand(@TweetParam final String status, @UserParam final String user) {

        LOGGER.info(String.format("New JavaOne status: %s, by %s", status, user));

        return "#JavaLand is awesome! Where else can you see a session then ride a rollercoster?";
    }

    @Tweet(".*#JavaOne.*")
    public String javaoneStatus(@TweetParam final String status, @UserParam final String user) {

        LOGGER.info(String.format("New JavaOne status: %s, by %s", status, user));

        return "#JavaOne is where the cool tech showcases";
    }

    @Tweet(".*#NightHacking.*")
    public String nightHacking(@TweetParam final String status, @UserParam final String user) {

        LOGGER.info(String.format("New JavaOne status: %s, by %s", status, user));

        return "#NightHacking Stephen Chin is like an extremely technical version of the Fonz, with robots.";
    }

}
