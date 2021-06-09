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
package org.apache.tomee.chatterbox.nats.api;

import java.time.Instant;

public interface NATSMessage {
    Instant getInstant();
    long getSequence();
    String getSubject();
    void setSubject(String subject);
    String getReplyTo();
    void setReplyTo(String reply);
    byte[] getData();
    void setData(byte[] data);
    void setData(byte[] data, int offset, int length);
    long getTimestamp();
    public boolean isRedelivered();
    public int getCrc32();
    public void ack();
    public String toString();
}
