package org.apache.skywalking.oap.server.receiver.trace.server;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.skywalking.oap.server.receiver.trace.provider.handler.KafkaHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author caoyixiong
 */
public class KafkaServer {
    private static final Logger logger = LoggerFactory.getLogger(KafkaServer.class);

    private final String brokers;
    private final String topic;
    private CopyOnWriteArrayList<KafkaHandler> kafkaHandlers = new CopyOnWriteArrayList<>();

    private Consumer<String, byte[]> consumer;

    public KafkaServer(String brokers, String topic) {
        this.brokers = brokers;
        this.topic = topic;
        initialize();
    }

    public void addHandler(KafkaHandler kafkaHandler) {
        kafkaHandlers.add(kafkaHandler);
    }

    private void initialize() {
        Properties props = new Properties();
        props.put("bootstrap.servers", brokers);
        props.put("group.id", "sw_group");
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", 1000);
        props.put("session.timeout.ms", 120000);
        props.put("max.poll.interval.ms", 600000);
        props.put("max.poll.records", 100);
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        consumer = new KafkaConsumer<String, byte[]>(props);
        consumer.subscribe(Collections.singletonList(topic));
    }

    public void start() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    ConsumerRecords<String, byte[]> records = consumer.poll(1000);
                    logger.info("获取的kafka消息： " + records.count());
                    for (KafkaHandler kafkaHandler : kafkaHandlers) {
                        kafkaHandler.doConsumer(records);
                    }
                }
            }
        }).start();
    }
}
