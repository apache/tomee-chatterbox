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
import io.nats.streaming.Message;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@MessageDriven(name = "Echo", activationConfig = {
        @ActivationConfigProperty(propertyName = "subject", propertyValue = "echo")
})
public class EchoBean implements InboundListener {


    @Override
    public void onMessage(final Message message) throws NATSException {
        try {
            final String text = new String(message.getData(), StandardCharsets.UTF_8);
            System.out.println(text);

            message.ack();
        } catch (IOException e) {
            throw new NATSException(e);
        }
    }
}
