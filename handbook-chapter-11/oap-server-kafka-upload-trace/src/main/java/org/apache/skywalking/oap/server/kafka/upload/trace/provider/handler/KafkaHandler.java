package org.apache.skywalking.oap.server.kafka.upload.trace.provider.handler;

import org.apache.kafka.clients.consumer.ConsumerRecords;

/**
 * @author caoyixiong
 */
public interface KafkaHandler {
    void doConsumer(ConsumerRecords<String, byte[]> records);
}
