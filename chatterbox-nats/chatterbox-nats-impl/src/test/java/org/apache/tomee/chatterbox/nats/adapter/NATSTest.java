package org.apache.tomee.chatterbox.nats.adapter;

import io.nats.streaming.Options;
import io.nats.streaming.StreamingConnection;
import io.nats.streaming.StreamingConnectionFactory;
import org.junit.Assert;
import org.junit.Test;

public class NATSTest {

    @Test
    public void testShouldConnect() throws Exception {
        StreamingConnectionFactory cf = new
                StreamingConnectionFactory(new Options.Builder().natsUrl("nats://localhost:4222")
                .clusterId("cluster-id").clientId("yourclientid").build());

        final StreamingConnection connection = cf.createConnection();
        Assert.assertNotNull(connection);

        connection.close();
    }
}
