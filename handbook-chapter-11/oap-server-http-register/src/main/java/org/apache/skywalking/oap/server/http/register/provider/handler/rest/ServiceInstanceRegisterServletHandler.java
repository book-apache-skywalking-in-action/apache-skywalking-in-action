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

package org.apache.skywalking.oap.server.http.register.provider.handler.rest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.apache.skywalking.apm.network.common.KeyStringValuePair;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.register.service.IServiceInstanceInventoryRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.jetty.ArgumentsParseException;
import org.apache.skywalking.oap.server.library.server.jetty.JettyJsonHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory.PropertyUtil.*;

/**
 * @author caoyixiong
 */
public class ServiceInstanceRegisterServletHandler extends JettyJsonHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstanceRegisterServletHandler.class);

    private final IServiceInstanceInventoryRegister serviceInstanceInventoryRegister;
    private final ServiceInventoryCache serviceInventoryCache;
    private final Gson gson = new Gson();

    private static final String SERVICE_ID = "si";
    private static final String INSTANCE_UUID = "iu";
    private static final String REGISTER_TIME = "rt";
    private static final String INSTANCE_ID = "ii";
    private static final String INSTANCE_PROPERTIES = "ips";

    public ServiceInstanceRegisterServletHandler(ModuleManager moduleManager) {
        this.serviceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInventoryCache.class);
        this.serviceInstanceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(IServiceInstanceInventoryRegister.class);
    }

    @Override
    public String pathSpec() {
        return "/v6/serviceInstance/register";
    }

    @Override
    protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException {
        JsonArray responseArray = new JsonArray();
        try {
            JsonArray instanceList = gson.fromJson(req.getReader(), JsonArray.class);
            for (int i = 0; i < instanceList.size(); i++) {
                JsonObject instance = instanceList.get(i).getAsJsonObject();
                int serviceId = instance.get(SERVICE_ID).getAsInt();
                String instanceUUID = instance.get(INSTANCE_UUID).getAsString();
                long registerTime = instance.get(REGISTER_TIME).getAsLong();
                List<KeyStringValuePair> propertiesList = gson.fromJson(instance.get(INSTANCE_PROPERTIES).getAsString(), new TypeToken<List<KeyStringValuePair>>() {
                }.getType());

                ServiceInventory serviceInventory = serviceInventoryCache.get(serviceId);
                JsonObject instanceProperties = new JsonObject();
                List<String> ipv4s = new ArrayList<>();

                for (KeyStringValuePair property : propertiesList) {
                    String key = property.getKey();
                    switch (key) {
                        case HOST_NAME:
                            instanceProperties.addProperty(HOST_NAME, property.getValue());
                            break;
                        case OS_NAME:
                            instanceProperties.addProperty(OS_NAME, property.getValue());
                            break;
                        case LANGUAGE:
                            instanceProperties.addProperty(LANGUAGE, property.getValue());
                            break;
                        case "ipv4":
                            ipv4s.add(property.getValue());
                            break;
                        case PROCESS_NO:
                            instanceProperties.addProperty(PROCESS_NO, property.getValue());
                            break;
                    }
                }
                instanceProperties.addProperty(IPV4S, ServiceInstanceInventory.PropertyUtil.ipv4sSerialize(ipv4s));

                String instanceName = serviceInventory.getName();
                if (instanceProperties.has(PROCESS_NO)) {
                    instanceName += "-pid:" + instanceProperties.get(PROCESS_NO).getAsString();
                }
                if (instanceProperties.has(HOST_NAME)) {
                    instanceName += "@" + instanceProperties.get(HOST_NAME).getAsString();
                }

                int serviceInstanceId = serviceInstanceInventoryRegister.getOrCreate(serviceId, instanceName, instanceUUID, registerTime, instanceProperties);

                if (serviceInstanceId != Const.NONE) {
                    logger.info("register service instance id={} [UUID:{}]", serviceInstanceId, instanceUUID);
                    JsonObject mapping = new JsonObject();
                    mapping.addProperty(INSTANCE_UUID, instanceUUID);
                    mapping.addProperty(INSTANCE_ID, serviceInstanceId);
                    responseArray.add(mapping);
                }
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return responseArray;
    }
}
