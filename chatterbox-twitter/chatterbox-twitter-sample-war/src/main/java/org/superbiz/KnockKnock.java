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

import org.apache.tomee.chatterbox.twitter.api.Response;
import org.apache.tomee.chatterbox.twitter.api.Tweet;
import org.apache.tomee.chatterbox.twitter.api.TweetParam;
import org.apache.tomee.chatterbox.twitter.api.TwitterUpdates;
import org.apache.tomee.chatterbox.twitter.api.UserParam;

import javax.ejb.MessageDriven;

@MessageDriven(name = "Status")
public class KnockKnock implements TwitterUpdates {

    @Tweet(".*KNOCK KNOCK.*")
    public String loudKnock() {
        return "Not so loud, you're giving me a headache!";
    }

    @Tweet(".*[Kk]nock(,? |-)[Kk]nock.*")
    public Response knockKnock() {

        return Response.message("Who's there?")
                .dialog(new WhosThere())
                .build();
    }

    public class WhosThere {

        @Tweet("{who}")
        public Response who(@TweetParam("who") final String who, @UserParam final String user) {
            if (who.equals(user)) {
                return Response.message("You know how knock knock jokes work, right?")
                        .build();
            } else {
                return Response.message(who + " who?")
                        .dialog(new Who())
                        .build();
            }
        }

        @Tweet("(?i)Banana")
        public Response orange() {
            return Response.message("Orange you glad I didn't say Banana again.  Try again, who's there?")
                    .dialog(this)
                    .build();
        }
    }

    public class Who {

        public String punchline() {
            return "Haha, lol. That's a good one, I'll have to remember that.";
        }
    }

}
