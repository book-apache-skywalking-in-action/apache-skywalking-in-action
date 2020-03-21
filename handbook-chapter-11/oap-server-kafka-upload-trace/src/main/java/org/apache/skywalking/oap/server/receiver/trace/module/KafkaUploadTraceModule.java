package org.apache.skywalking.oap.server.receiver.trace.module;

import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.receiver.trace.provider.parser.ISegmentParserService;

/**
 * @author caoyixiong
 */
public class KafkaUploadTraceModule extends ModuleDefine {
    public static final String NAME = "kafka-upload-trace";

    public KafkaUploadTraceModule() {
        super(NAME);
    }

    @Override
    public Class[] services() {
        return new Class[]{ISegmentParserService.class};
    }
}
