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

import org.apache.tomee.chatterbox.nats.api.NATSConnection;
import org.apache.tomee.chatterbox.nats.api.NATSConnectionFactory;

import javax.annotation.Resource;
import javax.ejb.Lock;
import javax.ejb.LockType;
import javax.ejb.Singleton;
import javax.resource.ResourceException;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import java.nio.charset.StandardCharsets;

@Singleton
@Lock(LockType.READ)
@Path("sender")
public class Sender {

    @Resource
    private NATSConnectionFactory cf;

    @Path("{subject}")
    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    public void sendMessage(@PathParam("subject") final String subject, final String message) {
        try {
            final NATSConnection connection = cf.getConnection();
            connection.publish(subject, message.getBytes(StandardCharsets.UTF_8));
            connection.close();
        } catch (Throwable t) {
            // TODO: log this
        }
    }

}
