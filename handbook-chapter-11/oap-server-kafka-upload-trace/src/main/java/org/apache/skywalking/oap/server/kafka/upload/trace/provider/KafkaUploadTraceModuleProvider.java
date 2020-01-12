package org.apache.skywalking.oap.server.kafka.upload.trace.provider;

import org.apache.skywalking.oap.server.configuration.api.ConfigurationModule;
import org.apache.skywalking.oap.server.configuration.api.DynamicConfigurationService;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.server.GRPCHandlerRegister;
import org.apache.skywalking.oap.server.core.server.JettyHandlerRegister;
import org.apache.skywalking.oap.server.kafka.upload.trace.module.KafkaUploadTraceModule;
import org.apache.skywalking.oap.server.kafka.upload.trace.provider.handler.TraceSegmentReportKafkaServiceHandler;
import org.apache.skywalking.oap.server.kafka.upload.trace.server.KafkaServer;
import org.apache.skywalking.oap.server.library.module.*;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;
import org.apache.skywalking.oap.server.receiver.trace.module.TraceModule;
import org.apache.skywalking.oap.server.receiver.trace.provider.DBLatencyThresholdsAndWatcher;
import org.apache.skywalking.oap.server.receiver.trace.provider.TraceModuleProvider;
import org.apache.skywalking.oap.server.receiver.trace.provider.TraceServiceModuleConfig;
import org.apache.skywalking.oap.server.receiver.trace.provider.UninstrumentedGatewaysConfig;
import org.apache.skywalking.oap.server.receiver.trace.provider.handler.v5.grpc.TraceSegmentServiceHandler;
import org.apache.skywalking.oap.server.receiver.trace.provider.handler.v5.rest.TraceSegmentServletHandler;
import org.apache.skywalking.oap.server.receiver.trace.provider.handler.v6.grpc.TraceSegmentReportServiceHandler;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.*;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.endpoint.MultiScopesSpanListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.segment.SegmentSpanListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener.service.ServiceMappingSpanListener;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.standardization.SegmentStandardizationWorker;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * @author caoyixiong
 */
public class KafkaUploadTraceModuleProvider extends ModuleProvider {
    private final KafkaUploadTraceServiceModuleConfig moduleConfig;
    private SegmentParse.Producer segmentProducer;
    private SegmentParseV2.Producer segmentProducerV2;

    public KafkaUploadTraceModuleProvider() {
        this.moduleConfig = new KafkaUploadTraceServiceModuleConfig();
    }

    @Override
    public String name() {
        return "default";
    }

    @Override
    public Class<? extends ModuleDefine> module() {
        return KafkaUploadTraceModule.class;
    }

    @Override
    public ModuleConfig createConfigBeanIfAbsent() {
        return moduleConfig;
    }

    @Override
    public void prepare() throws ServiceNotProvidedException {

        segmentProducer = new SegmentParse.Producer(getManager(), listenerManager(), moduleConfig);
        segmentProducerV2 = new SegmentParseV2.Producer(getManager(), listenerManager(), moduleConfig);

        this.registerServiceImplementation(ISegmentParserService.class, new SegmentParserServiceImpl(segmentProducerV2));
    }

    public SegmentParserListenerManager listenerManager() {
        SegmentParserListenerManager listenerManager = new SegmentParserListenerManager();
        if (moduleConfig.isTraceAnalysis()) {
            listenerManager.add(new MultiScopesSpanListener.Factory());
            listenerManager.add(new ServiceMappingSpanListener.Factory());
        }
        listenerManager.add(new SegmentSpanListener.Factory(moduleConfig.getSampleRate()));

        return listenerManager;
    }

    @Override
    public void start() throws ModuleStartException {
        try {
            SegmentStandardizationWorker standardizationWorker = new SegmentStandardizationWorker(getManager(), segmentProducer, moduleConfig.getBufferPath() + "v5", moduleConfig.getBufferOffsetMaxFileSize(), moduleConfig.getBufferDataMaxFileSize(), moduleConfig.isBufferFileCleanWhenRestart(), false);
            segmentProducer.setStandardizationWorker(standardizationWorker);

            SegmentStandardizationWorker standardizationWorkerV2 = new SegmentStandardizationWorker(getManager(), segmentProducerV2, moduleConfig.getBufferPath(), moduleConfig.getBufferOffsetMaxFileSize(), moduleConfig.getBufferDataMaxFileSize(), moduleConfig.isBufferFileCleanWhenRestart(), true);
            segmentProducerV2.setStandardizationWorker(standardizationWorkerV2);

            KafkaServer kafkaServer = new KafkaServer(moduleConfig.getKafkaBrokers(), moduleConfig.getTopic());
            TraceSegmentReportKafkaServiceHandler handler = new TraceSegmentReportKafkaServiceHandler(segmentProducerV2, getManager());
            kafkaServer.addHandler(handler);
            kafkaServer.start();
        } catch (IOException e) {
            throw new ModuleStartException(e.getMessage(), e);
        }
    }

    @Override
    public void notifyAfterCompleted() {

    }

    @Override
    public String[] requiredModules() {
        return new String[]{TelemetryModule.NAME, CoreModule.NAME, SharingServerModule.NAME, ConfigurationModule.NAME};
    }
}
