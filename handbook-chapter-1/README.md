# 第1.4.1章节示例代码

`SkyWalkingAgent`： `premain` 启动类
`SkyWalkingTest`： 测试类
`SkyWalkingTransformer`: 转换字节码文件类

## 如何使用

1. 将项目导入至`IDEA`
2. `mvn package` 打包
3. 通过设置`-javaagent:/path/to/handbook-chapter-1-1.0-SNAPSHOT.jar`运行`SkyWalkingTest`类

## 结果
```
Hello, This is a SkyWalking Handbook JavaAgent demo
This is SkyWalkingTest main method
总共耗时：0
```
