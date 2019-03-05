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
package org.apache.tomee.chatterbox.twitter.api;

public class Response {

    private final boolean prefixReply;
    private final String message;
    private final Object dialog;

    public Response(boolean prefixReply, String message, Object dialog) {
        this.prefixReply = prefixReply;
        this.message = message;
        this.dialog = dialog;
    }

    public static ResponseBuilder prefixReply(final boolean prefixReply) {
        return new ResponseBuilder().prefixReply(prefixReply);
    }

    public static ResponseBuilder message(final String message) {
        return new ResponseBuilder().message(message);
    }

    public static ResponseBuilder dialog(final Object dialog) {
        return new ResponseBuilder().dialog(dialog);
    }

    public boolean isPrefixReply() {
        return prefixReply;
    }

    public String getMessage() {
        return message;
    }

    public Object getDialog() {
        return dialog;
    }

    public static class ResponseBuilder {
        private boolean prefixReply;
        private String message;
        private Object dialog;

        public ResponseBuilder prefixReply(final boolean prefixReply) {
            this.prefixReply = prefixReply;
            return this;
        }

        public ResponseBuilder message(final String message) {
            this.message = message;
            return this;
        }

        public ResponseBuilder dialog(final Object dialog) {
            this.dialog = dialog;
            return this;
        }

        public Response build() {
            return new Response(prefixReply, message, dialog);
        }
    }

}

