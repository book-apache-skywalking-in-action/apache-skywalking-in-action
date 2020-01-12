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

package org.apache.skywalking.apm.agent.http.register;

import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.OverrideImplementor;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.commands.CommandService;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.RemoteDownstreamConfig;
import org.apache.skywalking.apm.agent.core.dictionary.*;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.os.OSUtil;
import org.apache.skywalking.apm.agent.core.remote.ServiceAndEndpointRegisterClient;
import org.apache.skywalking.apm.agent.http.register.client.HttpClient;
import org.apache.skywalking.apm.agent.http.register.directory.EndpointNameHttpDictionary;
import org.apache.skywalking.apm.agent.http.register.directory.NetworkAddressHttpDictionary;
import org.apache.skywalking.apm.dependencies.com.google.common.collect.Lists;
import org.apache.skywalking.apm.dependencies.com.google.gson.Gson;
import org.apache.skywalking.apm.dependencies.com.google.gson.JsonArray;
import org.apache.skywalking.apm.dependencies.com.google.gson.JsonElement;
import org.apache.skywalking.apm.dependencies.com.google.gson.JsonObject;
import org.apache.skywalking.apm.network.common.Commands;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;
import org.apache.skywalking.apm.util.StringUtil;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author caoyixiong
 */
@OverrideImplementor(ServiceAndEndpointRegisterClient.class)
public class ServiceAndEndpointHttpRegisterClient extends ServiceAndEndpointRegisterClient {
    private static final ILog logger = LogManager.getLogger(ServiceAndEndpointHttpRegisterClient.class);

    private static final String SERVICE_REGISTER_PATH = "/v6/service/register";
    private static final String SERVICE_INSTANCE_REGISTER_PATH = "/v6/serviceInstance/register";
    private static final String SERVICE_INSTANCE_PING_PATH = "/v6/serviceInstance/ping";

    private static final String SERVICE_NAME = "sn";
    private static final String SERVICE_ID = "si";

    private static final String INSTANCE_UUID = "iu";
    private static final String REGISTER_TIME = "rt";
    private static final String INSTANCE_ID = "ii";
    private static final String INSTANCE_PROPERTIES = "ips";

    private static final String HEARTBEAT_TIME = "ht";
    private static final String INSTANCE_COMMAND = "ic";

    private static String AGENT_INSTANCE_UUID;

    private volatile ScheduledFuture<?> applicationRegisterFuture;
    private volatile long coolDownStartTime = -1;
    private Gson gson = new Gson();

    @Override
    public void prepare() throws Throwable {
        AGENT_INSTANCE_UUID = StringUtil.isEmpty(Config.Agent.INSTANCE_UUID) ? UUID.randomUUID().toString()
                .replaceAll("-", "") : Config.Agent.INSTANCE_UUID;
    }

    @Override
    public void boot() throws Throwable {
        applicationRegisterFuture = Executors
                .newSingleThreadScheduledExecutor(new DefaultNamedThreadFactory("ServiceAndEndpointRegisterClient"))
                .scheduleAtFixedRate(new RunnableWithExceptionProtection(this, new RunnableWithExceptionProtection.CallbackWhenException() {
                    @Override
                    public void handle(Throwable t) {
                        logger.error("unexpected exception.", t);
                    }
                }), 0, Config.Collector.APP_AND_SERVICE_REGISTER_CHECK_INTERVAL, TimeUnit.SECONDS);
    }

    @Override
    public void onComplete() throws Throwable {
    }

    @Override
    public void shutdown() throws Throwable {
        applicationRegisterFuture.cancel(true);
    }

    @Override
    public void run() {
        if (coolDownStartTime > 0) {
            final long coolDownDurationInMillis = TimeUnit.MINUTES.toMillis(Config.Agent.COOL_DOWN_THRESHOLD);
            if (System.currentTimeMillis() - coolDownStartTime < coolDownDurationInMillis) {
                logger.warn("The agent is cooling down, won't register itself");
                return;
            } else {
                logger.warn("The agent is re-registering itself to backend");
            }
        }
        coolDownStartTime = -1;

        boolean shouldTry = true;
        while (shouldTry) {
            shouldTry = false;
            try {
                if (RemoteDownstreamConfig.Agent.SERVICE_ID == DictionaryUtil.nullValue()) {
                    JsonArray jsonElements = gson.fromJson(
                            HttpClient.INSTANCE.execute(SERVICE_REGISTER_PATH, Lists.newArrayList(Config.Agent.SERVICE_NAME)),
                            JsonArray.class);
                    if (jsonElements != null && jsonElements.size() > 0) {
                        for (JsonElement jsonElement : jsonElements) {
                            JsonObject jsonObject = jsonElement.getAsJsonObject();
                            String serviceName = jsonObject.get(SERVICE_NAME).getAsString();
                            int serviceId = jsonObject.get(SERVICE_ID).getAsInt();

                            if (Config.Agent.SERVICE_NAME.equals(serviceName)) {
                                RemoteDownstreamConfig.Agent.SERVICE_ID = serviceId;
                                shouldTry = true;
                            }
                        }
                    }
                } else {
                    if (RemoteDownstreamConfig.Agent.SERVICE_INSTANCE_ID == DictionaryUtil.nullValue()) {

                        JsonArray jsonArray = new JsonArray();
                        JsonObject mapping = new JsonObject();
                        jsonArray.add(mapping);

                        mapping.addProperty(SERVICE_ID, RemoteDownstreamConfig.Agent.SERVICE_ID);
                        mapping.addProperty(INSTANCE_UUID, AGENT_INSTANCE_UUID);
                        mapping.addProperty(REGISTER_TIME, System.currentTimeMillis());
                        mapping.addProperty(INSTANCE_PROPERTIES, gson.toJson(OSUtil.buildOSInfo()));

                        JsonArray response = gson.fromJson(HttpClient.INSTANCE.execute(SERVICE_INSTANCE_REGISTER_PATH, jsonArray), JsonArray.class);
                        for (JsonElement serviceInstance : response) {
                            String agentInstanceUUID = serviceInstance.getAsJsonObject().get(INSTANCE_UUID).getAsString();
                            if (AGENT_INSTANCE_UUID.equals(agentInstanceUUID)) {
                                int serviceInstanceId = serviceInstance.getAsJsonObject().get(INSTANCE_ID).getAsInt();
                                if (serviceInstanceId != DictionaryUtil.nullValue()) {
                                    RemoteDownstreamConfig.Agent.SERVICE_INSTANCE_ID = serviceInstanceId;
                                    RemoteDownstreamConfig.Agent.INSTANCE_REGISTERED_TIME = System.currentTimeMillis();
                                }
                            }
                        }
                    } else {
                        JsonObject jsonObject = new JsonObject();
                        jsonObject.addProperty(INSTANCE_ID, RemoteDownstreamConfig.Agent.SERVICE_INSTANCE_ID);
                        jsonObject.addProperty(HEARTBEAT_TIME, System.currentTimeMillis());
                        jsonObject.addProperty(INSTANCE_UUID, AGENT_INSTANCE_UUID);

                        JsonObject response = gson.fromJson(HttpClient.INSTANCE.execute(SERVICE_INSTANCE_PING_PATH, jsonObject), JsonObject.class);

                        final Commands commands = gson.fromJson(response.get(INSTANCE_COMMAND).getAsString(), Commands.class);
                        ServiceManager.INSTANCE.findService(CommandService.class).receiveCommand(commands);

                        NetworkAddressHttpDictionary.INSTANCE.syncRemoteDictionary();
                        EndpointNameHttpDictionary.INSTANCE.syncRemoteDictionary();
                    }
                }
            } catch (Throwable t) {
                logger.error(t, "ServiceAndEndpointHttpRegisterClient execute fail.");
            }
        }
    }

    @Override
    public void coolDown() {
        this.coolDownStartTime = System.currentTimeMillis();
    }
}
