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

package org.apache.skywalking.oap.server.http.register.provider;

import org.apache.skywalking.oap.server.core.server.JettyHandlerRegister;
import org.apache.skywalking.oap.server.http.register.module.HttpRegisterModule;
import org.apache.skywalking.oap.server.http.register.provider.handler.rest.*;
import org.apache.skywalking.oap.server.library.module.ModuleDefine;
import org.apache.skywalking.oap.server.receiver.register.provider.RegisterModuleProvider;
import org.apache.skywalking.oap.server.receiver.sharing.server.SharingServerModule;

/**
 * @author peng-yongsheng
 */
public class HttpRegisterModuleProvider extends RegisterModuleProvider {

    @Override
    public Class<? extends ModuleDefine> module() {
        return HttpRegisterModule.class;
    }

    @Override
    public void start() {
        super.start();

        JettyHandlerRegister jettyHandlerRegister = getManager().find(SharingServerModule.NAME).provider().getService(JettyHandlerRegister.class);
        jettyHandlerRegister.addHandler(new ServiceRegisterServletHandler(getManager()));
        jettyHandlerRegister.addHandler(new ServiceInstanceRegisterServletHandler(getManager()));
        jettyHandlerRegister.addHandler(new ServiceInstancePingServiceHandler(getManager()));
        jettyHandlerRegister.addHandler(new EndpointRegisterServiceHandler(getManager()));
        jettyHandlerRegister.addHandler(new NetworkAddressRegisterServletHandler(getManager()));
    }
}
