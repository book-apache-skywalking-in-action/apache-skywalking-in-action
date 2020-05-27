# 第11章节示例代码

`apm-agent-http-register`： Http注册通讯 - 探针端代码

`oap-server-http-register`: Http注册通讯 - 服务端代码

`apm-agent-kafka-upload-trace`: Kafka数据上报通讯 - 探针端代码

`oap-server-kafka-upload-trace`: Kafka数据上报通讯 - 服务端代码

## 如何使用

1. 使用 `mvn package` 打包

2. 将 `apm-agent-http-register` 和  `apm-agent-kafka-upload-trace` 生成的探针端`jar`包放置在`SkyWaling`主站包的`agent/plugins`目录之下

3. 将 `oap-server-http-register` 和 `oap-server-kafka-upload-trace` 生成的服务端`jar`包放置在`SkyWaling`主站包的`oap-libs`目录之下

4. 配置服务端参数
   - 配置`Http`注册通讯
     将服务端参数 `receiver-register:` 换成 `http-receiver-register:`
   - 配置`Kafka`数据上报通讯
     将服务端参数 `receiver-trace:` 换成 `kafka-upload-trace:`
     并在其 `default`参数下添加`kafkaBrokers`和`topic`参数
   
配置示例如下:
```yaml
...上面的配置...
http-receiver-register:
  default:
kafka-upload-trace:
  default:
    bufferPath: ${SW_RECEIVER_BUFFER_PATH:../trace-buffer/}  # Path to trace buffer files, suggest to use absolute path
    bufferOffsetMaxFileSize: ${SW_RECEIVER_BUFFER_OFFSET_MAX_FILE_SIZE:100} # Unit is MB
    bufferDataMaxFileSize: ${SW_RECEIVER_BUFFER_DATA_MAX_FILE_SIZE:500} # Unit is MB
    bufferFileCleanWhenRestart: ${SW_RECEIVER_BUFFER_FILE_CLEAN_WHEN_RESTART:false}
    sampleRate: ${SW_TRACE_SAMPLE_RATE:10000} # The sample rate precision is 1/10000. 10000 means 100% sample in default.
    slowDBAccessThreshold: ${SW_SLOW_DB_THRESHOLD:default:200,mongodb:100} # The slow database access thresholds. Unit ms.
    kafkaBrokers: 127.0.0.1:9092
    topic: test
...下面的配置...
```

5. 配置探针端参数，探针端参数是通过`Java`环境变量来进行注入的
```
-DskyWalkingKafkaBrokers=127.0.0.1:9092  // 用于配置Kafka的集群地址
-DskyWalkingKafkaTopic=test              // 用于配置Kafka的topic
-DbackendRegisterAddress=127.0.0.1:12800 // 用于配置Http的接收端地址，支持多个地址(随机负载均衡)，通过","进行分隔，
```

6. 正常启动服务端和探针端