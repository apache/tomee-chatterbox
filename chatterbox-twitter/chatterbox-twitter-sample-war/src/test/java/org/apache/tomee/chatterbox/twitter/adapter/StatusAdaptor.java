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


import twitter4j.GeoLocation;
import twitter4j.HashtagEntity;
import twitter4j.MediaEntity;
import twitter4j.Place;
import twitter4j.RateLimitStatus;
import twitter4j.Scopes;
import twitter4j.Status;
import twitter4j.SymbolEntity;
import twitter4j.URLEntity;
import twitter4j.User;
import twitter4j.UserMentionEntity;

import java.util.Date;

public class StatusAdaptor implements Status {
    @Override
    public Date getCreatedAt() {
        return null;
    }

    @Override
    public long getId() {
        return 0;
    }

    @Override
    public String getText() {
        return null;
    }

    @Override
    public String getSource() {
        return null;
    }

    @Override
    public boolean isTruncated() {
        return false;
    }

    @Override
    public long getInReplyToStatusId() {
        return 0;
    }

    @Override
    public long getInReplyToUserId() {
        return 0;
    }

    @Override
    public String getInReplyToScreenName() {
        return null;
    }

    @Override
    public GeoLocation getGeoLocation() {
        return null;
    }

    @Override
    public Place getPlace() {
        return null;
    }

    @Override
    public boolean isFavorited() {
        return false;
    }

    @Override
    public boolean isRetweeted() {
        return false;
    }

    @Override
    public int getFavoriteCount() {
        return 0;
    }

    @Override
    public User getUser() {
        return null;
    }

    @Override
    public boolean isRetweet() {
        return false;
    }

    @Override
    public Status getRetweetedStatus() {
        return null;
    }

    @Override
    public long[] getContributors() {
        return new long[0];
    }

    @Override
    public int getRetweetCount() {
        return 0;
    }

    @Override
    public boolean isRetweetedByMe() {
        return false;
    }

    @Override
    public long getCurrentUserRetweetId() {
        return 0;
    }

    @Override
    public boolean isPossiblySensitive() {
        return false;
    }

    @Override
    public String getLang() {
        return null;
    }

    @Override
    public Scopes getScopes() {
        return null;
    }

    @Override
    public int compareTo(final Status o) {
        return 0;
    }

    @Override
    public UserMentionEntity[] getUserMentionEntities() {
        return new UserMentionEntity[0];
    }

    @Override
    public URLEntity[] getURLEntities() {
        return new URLEntity[0];
    }

    @Override
    public HashtagEntity[] getHashtagEntities() {
        return new HashtagEntity[0];
    }

    @Override
    public MediaEntity[] getMediaEntities() {
        return new MediaEntity[0];
    }

    @Override
    public SymbolEntity[] getSymbolEntities() {
        return new SymbolEntity[0];
    }

    @Override
    public RateLimitStatus getRateLimitStatus() {
        return null;
    }

    @Override
    public int getAccessLevel() {
        return 0;
    }
}
