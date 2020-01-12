/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.kafka.upload.trace.provider.handler;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.skywalking.apm.network.language.agent.UpstreamSegment;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.SegmentParseV2;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.SegmentSource;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author caoyixiong
 */
public class TraceSegmentReportKafkaServiceHandler implements KafkaHandler {

    private static final Logger logger = LoggerFactory.getLogger(TraceSegmentReportKafkaServiceHandler.class);

    private final SegmentParseV2.Producer segmentProducer;
    private HistogramMetrics histogram;

    public TraceSegmentReportKafkaServiceHandler(SegmentParseV2.Producer segmentProducer, ModuleManager moduleManager) {
        this.segmentProducer = segmentProducer;
        MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME).provider().getService(MetricsCreator.class);
        histogram = metricsCreator.createHistogramMetric("trace_grpc_v6_in_latency", "The process latency of service mesh telemetry",
                MetricsTag.EMPTY_KEY, MetricsTag.EMPTY_VALUE);
    }

    @Override
    public void doConsumer(ConsumerRecords<String, byte[]> records) {
        for (ConsumerRecord<String, byte[]> record : records) {
            HistogramMetrics.Timer timer = histogram.createTimer();
            try {
                segmentProducer.send(UpstreamSegment.parseFrom(record.value()), SegmentSource.Agent);
            } catch (InvalidProtocolBufferException e) {
                logger.error(e.getMessage(), e);
            } finally {
                timer.finish();
            }
        }
    }
}
