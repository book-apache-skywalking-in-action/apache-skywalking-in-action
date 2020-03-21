package org.apache.skywalking.apm.agent.demo;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * @Author: caoyixiong
 * @Date: 2020-03-21 23:17
 */
public class SkyWalkingTransformer implements ClassFileTransformer {
    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {

        // 只拦截SkyWalkingTest测试程序
        if (!"org/apache/skywalking/apm/agent/demo/SkyWalkingTest".equals(className)) {
            return null;
        }
        // 获取Javassist Class池
        ClassPool cp = ClassPool.getDefault();
        try {
            //获取到Class池中的SkyWalkingTest CtClass对象
            CtClass ctClass = cp.getCtClass(className.replace("/", "."));
            //找到对应的main方法
            CtMethod method = ctClass.getDeclaredMethod("main");
            //增加本地变量 - long 类型的 beginTime
            method.addLocalVariable("beginTime", CtClass.longType);
            //在main方法之前增加 `long beginTime = System.currentTimeMillis();` 代码
            method.insertBefore("long beginTime = System.currentTimeMillis();");
            //在main方法之后打印出耗时长短
            method.insertAfter("System.out.print(\"总共耗时：\");");
            method.insertAfter("System.out.println(System.currentTimeMillis() - beginTime);");
            // 返回修改过后的字节码数据
            return ctClass.toBytecode();
        } catch (NotFoundException | CannotCompileException | IOException e) {
            e.printStackTrace();
        }
        //返回null，代表没有修改此字节码
        return null;
    }
}