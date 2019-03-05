/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.superbiz;

import org.apache.tomee.chatterbox.xmpp.api.XMPPConnection;
import org.apache.tomee.chatterbox.xmpp.api.XMPPConnectionFactory;
import org.apache.tomee.chatterbox.xmpp.api.inflow.MessageText;
import org.apache.tomee.chatterbox.xmpp.api.inflow.MessageTextParam;
import org.apache.tomee.chatterbox.xmpp.api.inflow.SenderParam;
import org.apache.tomee.chatterbox.xmpp.api.inflow.XMPPMessageListener;

import javax.annotation.Resource;
import javax.ejb.MessageDriven;

@MessageDriven(name = "Chat")
public class ChatBean implements XMPPMessageListener {

    @Resource
    private XMPPConnectionFactory cf;

    @MessageText("echo {message:.*$}")
    public void echo(@SenderParam final String sender, @MessageTextParam("message") final String message) throws Exception {
        final XMPPConnection connection = cf.getConnection();
        connection.sendMessage(sender, message);
        connection.close();
    }

    @MessageText("help")
    public void help(@SenderParam final String sender) throws Exception {
        final String message = "Commands available\n" +
                "help - Displays this message\n" +
                "echo <text> - Echos the text\n" +
                "my name is <name> - greets the sender";

        final XMPPConnection connection = cf.getConnection();
        connection.sendMessage(sender, message);
        connection.close();
    }

    @MessageText("my name is {name}")
    public void greet(@SenderParam final String sender, @MessageTextParam("name") final String name) throws Exception {
        final String message = "Hello " + name + ", it is very nice to meet you";

        final XMPPConnection connection = cf.getConnection();
        connection.sendMessage(sender, message);
        connection.close();
    }
}
