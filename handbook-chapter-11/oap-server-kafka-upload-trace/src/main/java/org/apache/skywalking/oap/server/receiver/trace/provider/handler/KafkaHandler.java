package org.apache.skywalking.oap.server.receiver.trace.provider.handler;

import org.apache.kafka.clients.consumer.ConsumerRecords;

/**
 * @author caoyixiong
 */
public interface KafkaHandler {
    void doConsumer(ConsumerRecords<String, byte[]> records);
}
