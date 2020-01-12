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

package org.apache.skywalking.apm.agent.http.register.directory;


import org.apache.skywalking.apm.agent.core.dictionary.Found;
import org.apache.skywalking.apm.agent.core.dictionary.NotFound;
import org.apache.skywalking.apm.agent.core.dictionary.PossibleFound;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.http.register.client.HttpClient;
import org.apache.skywalking.apm.dependencies.com.google.gson.Gson;
import org.apache.skywalking.apm.dependencies.com.google.gson.JsonArray;
import org.apache.skywalking.apm.dependencies.com.google.gson.JsonElement;
import org.apache.skywalking.apm.dependencies.com.google.gson.JsonObject;
import org.apache.skywalking.apm.dependencies.io.netty.util.internal.ConcurrentSet;
import org.apache.skywalking.apm.network.language.agent.SpanType;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.skywalking.apm.agent.core.conf.Config.Dictionary.ENDPOINT_NAME_BUFFER_SIZE;

/**
 * @author wusheng
 */
public enum EndpointNameHttpDictionary {
    INSTANCE;
    private static final ILog logger = LogManager.getLogger(EndpointNameHttpDictionary.class);

    private static final String ENDPOINT_REGISTER_PATH = "/v6/endpoint/register";

    private static final String SERVICE_ID = "si";
    private static final String ENDPOINT_NAME = "en";
    private static final String ENDPOINT_ID = "ei";
    private static final String SPAN_TYPE = "st";

    private Gson gson = new Gson();

    private Map<OperationNameKey, Integer> endpointDictionary = new ConcurrentHashMap<OperationNameKey, Integer>();
    private Set<OperationNameKey> unRegisterEndpoints = new ConcurrentSet<OperationNameKey>();

    public PossibleFound findOrPrepare4Register(int serviceId, String endpointName,
                                                boolean isEntry, boolean isExit) {
        return find0(serviceId, endpointName, isEntry, isExit, true);
    }

    public PossibleFound findOnly(int serviceId, String endpointName) {
        return find0(serviceId, endpointName, false, false, false);
    }

    private PossibleFound find0(int serviceId, String endpointName,
                                boolean isEntry, boolean isExit, boolean registerWhenNotFound) {
        if (endpointName == null || endpointName.length() == 0) {
            return new NotFound();
        }
        OperationNameKey key = new OperationNameKey(serviceId, endpointName, isEntry, isExit);
        Integer operationId = endpointDictionary.get(key);
        if (operationId != null) {
            return new Found(operationId);
        } else {
            if (registerWhenNotFound &&
                    endpointDictionary.size() + unRegisterEndpoints.size() < ENDPOINT_NAME_BUFFER_SIZE) {
                unRegisterEndpoints.add(key);
            }
            return new NotFound();
        }
    }

    public void syncRemoteDictionary() throws IOException {
        if (unRegisterEndpoints.size() > 0) {

            JsonArray unRegisterEndpointArray = new JsonArray();
            for (OperationNameKey operationNameKey : unRegisterEndpoints) {
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty(SERVICE_ID, operationNameKey.getServiceId());
                jsonObject.addProperty(ENDPOINT_NAME, operationNameKey.getEndpointName());
                jsonObject.addProperty(SPAN_TYPE, operationNameKey.getSpanType());
                unRegisterEndpointArray.add(jsonObject);
            }

            String response = HttpClient.INSTANCE.execute(ENDPOINT_REGISTER_PATH, unRegisterEndpointArray);
            JsonArray array = gson.fromJson(response, JsonArray.class);

            if (array != null && array.size() > 0) {
                for (JsonElement element : array) {
                    JsonObject jsonObject = element.getAsJsonObject();
                    int serviceId = jsonObject.get(SERVICE_ID).getAsInt();
                    String endpointName = jsonObject.get(ENDPOINT_NAME).getAsString();
                    int spanType = jsonObject.get(SPAN_TYPE).getAsInt();
                    int endpointId = jsonObject.get(ENDPOINT_ID).getAsInt();

                    OperationNameKey key = new OperationNameKey(
                            serviceId,
                            endpointName,
                            spanType == SpanType.Entry_VALUE,
                            spanType == SpanType.Exit_VALUE);
                    unRegisterEndpoints.remove(key);
                    endpointDictionary.put(key, endpointId);
                }
            }
        }
    }

    private class OperationNameKey {
        private int serviceId;
        private String endpointName;
        private boolean isEntry;
        private boolean isExit;

        public OperationNameKey(int serviceId, String endpointName, boolean isEntry, boolean isExit) {
            this.serviceId = serviceId;
            this.endpointName = endpointName;
            this.isEntry = isEntry;
            this.isExit = isExit;
        }

        public int getServiceId() {
            return serviceId;
        }

        public String getEndpointName() {
            return endpointName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            OperationNameKey key = (OperationNameKey) o;

            boolean isServiceEndpointMatch = false;
            if (serviceId == key.serviceId && endpointName.equals(key.endpointName)) {
                isServiceEndpointMatch = true;
            }
            return isServiceEndpointMatch && isEntry == key.isEntry
                    && isExit == key.isExit;
        }

        @Override
        public int hashCode() {
            int result = serviceId;
            result = 31 * result + endpointName.hashCode();
            return result;
        }

        boolean isEntry() {
            return isEntry;
        }

        boolean isExit() {
            return isExit;
        }

        int getSpanType() {
            if (isEntry) {
                return SpanType.Entry.getNumber();
            } else if (isExit) {
                return SpanType.Exit.getNumber();
            } else {
                return SpanType.UNRECOGNIZED.getNumber();
            }
        }

        @Override
        public String toString() {
            return "OperationNameKey{" +
                    "serviceId=" + serviceId +
                    ", endpointName='" + endpointName + '\'' +
                    ", isEntry=" + isEntry +
                    ", isExit=" + isExit +
                    '}';
        }
    }
}
