package org.apache.skywalking.oap.server.receiver.trace.provider;

/**
 * @author caoyixiong
 */
class KafkaUploadTraceServiceModuleConfig extends TraceServiceModuleConfig {

    private String kafkaBrokers;

    private String topic;

    public String getKafkaBrokers() {
        return kafkaBrokers;
    }

    public void setKafkaBrokers(String kafkaBrokers) {
        this.kafkaBrokers = kafkaBrokers;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }
}
