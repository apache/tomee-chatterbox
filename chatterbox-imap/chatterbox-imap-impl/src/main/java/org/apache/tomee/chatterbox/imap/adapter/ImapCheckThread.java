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
package org.apache.tomee.chatterbox.imap.adapter;

import javax.mail.AuthenticationFailedException;
import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.search.FlagTerm;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ImapCheckThread extends Thread {

    private static final Logger LOGGER = Logger.getLogger(ImapCheckThread.class.getName());

    private final ImapResourceAdapter resourceAdapter;
    private final Session session;
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public ImapCheckThread(ImapResourceAdapter resourceAdapter) {
        this.resourceAdapter = resourceAdapter;
        final Properties properties = System.getProperties();
        session = Session.getDefaultInstance(properties, null);
        try {
            // Test the connection
            connect(session, resourceAdapter);
        } catch (AuthenticationFailedException e) {
            if ("imap.gmail.com".equals(resourceAdapter.getHost())) {
                LOGGER.log(Level.SEVERE, "Failed to Connect " + resourceAdapter + "  Ensure 'access to less secure apps' is turned on in your gmail account", e);
            } else {
                LOGGER.log(Level.SEVERE, "Failed to Connect " + resourceAdapter, e);
            }
        } catch (MessagingException e) {
            LOGGER.log(Level.SEVERE, "Failed to Connect " + resourceAdapter, e);
        }
    }

    private static Store connect(Session session, ImapResourceAdapter resourceAdapter) throws MessagingException {
        final Store store = session.getStore(resourceAdapter.getProtocol());
        store.connect(resourceAdapter.getHost(), resourceAdapter.getPort(), resourceAdapter.getUsername(), resourceAdapter.getPassword());
        return store;
    }

    @Override
    public void run() {
        while (!stopped.get()) {
            Store store = null;
            try {
                store = connect(session, resourceAdapter);
                processFolder(store, "inbox");
            } catch (MessagingException e) {
                LOGGER.log(Level.WARNING, String.format("Failed to Connect %s %s: %s",
                        resourceAdapter, e.getClass().getName(), e.getMessage()));
            } finally {
                if (store != null) {
                    try {
                        store.close();
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Unable to close store" , e);
                    }
                }
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    private void processFolder(Store store, String folderName) throws MessagingException {
        final Folder folder = store.getFolder(folderName);
        folder.open(Folder.READ_WRITE);

        final Message[] messages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));

        final FetchProfile fp = new FetchProfile();
        fp.add(FetchProfile.Item.ENVELOPE);
        fp.add(FetchProfile.Item.CONTENT_INFO);
        folder.fetch(messages, fp);

        for (final Message message : messages) {
            message.setFlag(Flags.Flag.SEEN, true);
            resourceAdapter.process(message);
        }
    }

    public void cancel() {
        stopped.set(true);
    }
}
