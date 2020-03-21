package org.apache.skywalking.apm.agent.demo;

import java.lang.instrument.Instrumentation;

/**
 * @Author: caoyixiong
 * @Date: 2020-03-21 23:17
 */
public class SkyWalkingAgent {
    public static void premain(String args, Instrumentation instrumentation) {
        System.out.println("Hello, This is a SkyWalking Handbook JavaAgent demo");
        instrumentation.addTransformer(new SkyWalkingTransformer());
    }
}