package org.apache.skywalking.apm.agent.kafka.upload.trace;

import org.apache.skywalking.apm.agent.core.boot.OverrideImplementor;
import org.apache.skywalking.apm.agent.core.context.TracingContext;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.remote.TraceSegmentServiceClient;
import org.apache.skywalking.apm.agent.kafka.upload.trace.client.KafkaClient;
import org.apache.skywalking.apm.commons.datacarrier.DataCarrier;
import org.apache.skywalking.apm.commons.datacarrier.buffer.BufferStrategy;
import org.apache.skywalking.apm.network.language.agent.UpstreamSegment;

import java.util.List;

import static org.apache.skywalking.apm.agent.core.conf.Config.Buffer.BUFFER_SIZE;
import static org.apache.skywalking.apm.agent.core.conf.Config.Buffer.CHANNEL_SIZE;

/**
 * @author caoyixiong
 */
@OverrideImplementor(TraceSegmentServiceClient.class)
public class TraceSegmentKafkaServiceClient extends TraceSegmentServiceClient {
    private static final ILog logger = LogManager.getLogger(TraceSegmentServiceClient.class);
    private volatile DataCarrier<TraceSegment> carrier;
    private KafkaClient kafkaClient;
    @Override
    public void prepare() throws Throwable {
    }
    @Override
    public void boot() throws Throwable {
        kafkaClient = new KafkaClient();
        carrier = new DataCarrier<TraceSegment>(CHANNEL_SIZE, BUFFER_SIZE);
        carrier.setBufferStrategy(BufferStrategy.IF_POSSIBLE);
        carrier.consume(this, 1);
    }
    @Override
    public void onComplete() throws Throwable {
        TracingContext.ListenerManager.add(this);
    }

    @Override
    public void shutdown() throws Throwable {
        TracingContext.ListenerManager.remove(this);
        carrier.shutdownConsumers();
        kafkaClient.close();
    }
    @Override
    public void init() {
    }
    @Override
    public void consume(List<TraceSegment> data) {
        try {
            for (TraceSegment segment : data) {
                UpstreamSegment upstreamSegment = segment.transform();
                kafkaClient.send(upstreamSegment);
            }
        } catch (Throwable t) {
            logger.error(t, "Transform and send UpstreamSegment to collector fail.");
        }
    }
    @Override
    public void onError(List<TraceSegment> data, Throwable t) {
        logger.error(t, "Try to send {} trace segments to collector, with unexpected exception.", data.size());
    }
    @Override
    public void onExit() {
    }
    @Override
    public void afterFinished(TraceSegment traceSegment) {
        if (traceSegment.isIgnore()) {
            return;
        }
        if (!carrier.produce(traceSegment)) {
            if (logger.isDebugEnable()) {
                logger.debug("One trace segment has been abandoned, cause by buffer is full.");
            }
        }
    }
}
