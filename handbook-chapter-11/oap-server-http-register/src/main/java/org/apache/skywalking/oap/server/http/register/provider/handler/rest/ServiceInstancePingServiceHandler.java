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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.skywalking.apm.network.common.Command;
import org.apache.skywalking.apm.network.common.Commands;
import org.apache.skywalking.apm.network.trace.component.command.ServiceResetCommand;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.cache.ServiceInstanceInventoryCache;
import org.apache.skywalking.oap.server.core.command.CommandService;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.service.IServiceInstanceInventoryRegister;
import org.apache.skywalking.oap.server.core.register.service.IServiceInventoryRegister;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.jetty.ArgumentsParseException;
import org.apache.skywalking.oap.server.library.server.jetty.JettyJsonHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Objects;

/**
 * @author caoyixiong
 */
public class ServiceInstancePingServiceHandler extends JettyJsonHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstancePingServiceHandler.class);

    private final IServiceInstanceInventoryRegister serviceInstanceInventoryRegister;
    private final ServiceInstanceInventoryCache serviceInstanceInventoryCache;
    private final IServiceInventoryRegister serviceInventoryRegister;
    private final CommandService commandService;
    private final Gson gson = new Gson();

    private static final String INSTANCE_ID = "ii";
    private static final String HEARTBEAT_TIME = "ht";
    private static final String INSTANCE_UUID = "iu";
    private static final String INSTANCE_COMMAND = "ic";

    public ServiceInstancePingServiceHandler(ModuleManager moduleManager) {
        this.serviceInstanceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(IServiceInstanceInventoryRegister.class);
        this.serviceInstanceInventoryCache = moduleManager.find(CoreModule.NAME).provider().getService(ServiceInstanceInventoryCache.class);
        this.serviceInventoryRegister = moduleManager.find(CoreModule.NAME).provider().getService(IServiceInventoryRegister.class);
        this.commandService = moduleManager.find(CoreModule.NAME).provider().getService(CommandService.class);
    }

    @Override
    public String pathSpec() {
        return "/v6/serviceInstance/ping";
    }

    @Override
    protected JsonElement doGet(HttpServletRequest req) throws ArgumentsParseException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected JsonElement doPost(HttpServletRequest req) throws ArgumentsParseException, IOException {
        JsonObject responseJson = new JsonObject();
        try {
            JsonObject heartBeat = gson.fromJson(req.getReader(), JsonObject.class);
            int instanceId = heartBeat.get(INSTANCE_ID).getAsInt();
            long heartBeatTime = heartBeat.get(HEARTBEAT_TIME).getAsLong();
            String instanceUUID = heartBeat.get(INSTANCE_UUID).getAsString();

            serviceInstanceInventoryRegister.heartbeat(instanceId, heartBeatTime);
            ServiceInstanceInventory serviceInstanceInventory = serviceInstanceInventoryCache.get(instanceId);
            if (serviceInstanceInventory != null) {
                serviceInventoryRegister.heartbeat(serviceInstanceInventory.getServiceId(), heartBeatTime);
                responseJson.addProperty(INSTANCE_COMMAND, gson.toJson(Commands.getDefaultInstance()));
            } else {
                logger.warn("Can't find service by service instance id from cache," +
                        " service instance id is: {}, will send a reset command to agent side", instanceId);
                final ServiceResetCommand resetCommand = commandService.newResetCommand(instanceId, heartBeatTime, instanceUUID);
                final Command command = resetCommand.serialize().build();
                final Commands nextCommands = Commands.newBuilder().addCommands(command).build();
                responseJson.addProperty(INSTANCE_COMMAND, gson.toJson(nextCommands));
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return responseJson;
    }
}
