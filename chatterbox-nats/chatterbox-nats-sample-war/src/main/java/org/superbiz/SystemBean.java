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

import org.apache.tomee.chatterbox.nats.api.InboundListener;
import org.apache.tomee.chatterbox.nats.api.NATSException;
import org.apache.tomee.chatterbox.nats.api.NATSMessage;

import javax.ejb.MessageDriven;

@MessageDriven(name = "Receiver")
public class SystemBean implements InboundListener {


    @Override
    public void onMessage(NATSMessage message) throws NATSException {
        // TODO: fill in implementation here
    }
}
