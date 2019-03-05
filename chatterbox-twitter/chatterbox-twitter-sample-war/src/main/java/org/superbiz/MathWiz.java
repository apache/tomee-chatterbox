/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.superbiz;

import org.apache.tomee.chatterbox.twitter.api.Tweet;
import org.apache.tomee.chatterbox.twitter.api.TweetParam;
import org.apache.tomee.chatterbox.twitter.api.TwitterUpdates;

import javax.ejb.MessageDriven;

@MessageDriven
public class MathWiz implements TwitterUpdates {

    @Tweet("(?i)what is 2 ?[*x] ?2")
    public String twoTimesTwo() {
        return "The same thing as 2 + 2";
    }

    @Tweet("(?i)what is {a} ?[*x] ?{b}")
    public String times(@TweetParam("a") int a, @TweetParam("b") int b) {
        return String.format("%s times %s is %s", a, b, a * b);
    }

    @Tweet("(?i)what is {a} times {b}")
    public String times2(@TweetParam("a") int a, @TweetParam("b") int b) {
        return times(a, b);
    }

    @Tweet("(?i)what is 2 ?+ ?2")
    public String plus2() {
        return "The same thing as 2 x 2";
    }

    @Tweet("(?i)what is {a} ?+ ?{b}")
    public String plus(@TweetParam("a") int a, @TweetParam("b") int b) {
        return String.format("%s plus %s is %s", a, b, a + b);
    }

    @Tweet("(?i)what is {a} plus {b}")
    public String plus2(@TweetParam("a") int a, @TweetParam("b") int b) {
        return plus(a, b);
    }

    @Tweet("(?i)what is {a} ?- ?{b}")
    public String minus(@TweetParam("a") int a, @TweetParam("b") int b) {
        return String.format("%s minus %s is %s", a, b, a - b);
    }

    @Tweet("(?i)what is {a} minus {b}")
    public String minus2(@TweetParam("a") int a, @TweetParam("b") int b) {
        return plus(a, b);
    }

    @Tweet("(?i)what is {a} ?/ ?{b}")
    public String divided(@TweetParam("a") int a, @TweetParam("b") int b) {
        return String.format("%s divided by %s is %s", a, b, a / b);
    }

    @Tweet("(?i)what is {a} minus {b}")
    public String divided2(@TweetParam("a") int a, @TweetParam("b") int b) {
        return plus(a, b);
    }


}
