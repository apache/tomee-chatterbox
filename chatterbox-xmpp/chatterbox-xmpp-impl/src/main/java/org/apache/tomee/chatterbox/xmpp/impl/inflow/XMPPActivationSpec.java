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
package org.apache.tomee.chatterbox.xmpp.impl.inflow;

import org.apache.tomee.chatterbox.xmpp.api.inflow.XMPPMessageListener;

import javax.resource.spi.Activation;
import javax.resource.spi.ActivationSpec;
import javax.resource.spi.InvalidPropertyException;
import javax.resource.spi.ResourceAdapter;
import java.util.logging.Logger;

@Activation(messageListeners = {XMPPMessageListener.class})
public class XMPPActivationSpec implements ActivationSpec {

    private static Logger log = Logger.getLogger(XMPPActivationSpec.class.getName());

    private ResourceAdapter ra;

    private Class beanClass;

    public Class getBeanClass() {
        return beanClass;
    }

    public void setBeanClass(final Class beanClass) {
        this.beanClass = beanClass;
    }

    public void validate() throws InvalidPropertyException {
        log.finest("validate()");

    }

    public ResourceAdapter getResourceAdapter() {
        log.finest("getResourceAdapter()");
        return ra;
    }

    public void setResourceAdapter(ResourceAdapter ra) {
        log.finest("setResourceAdapter()");
        this.ra = ra;
    }


}
