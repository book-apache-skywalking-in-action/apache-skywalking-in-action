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

package org.apache.skywalking.apm.agent.http.register.client;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.skywalking.apm.dependencies.com.google.gson.Gson;
import org.apache.skywalking.apm.util.StringUtil;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author caoyixiong
 */
public enum HttpClient {
    INSTANCE;
    private CloseableHttpClient closeableHttpClient;
    private Gson gson;
    private String backendRegisterAddress;

    HttpClient() {
        closeableHttpClient = HttpClients.createDefault();
        gson = new Gson();
        backendRegisterAddress = System.getProperties().getProperty("backendRegisterAddress");
        if (StringUtil.isEmpty(backendRegisterAddress)) {
            throw new RuntimeException("load http register plugin, but backendRegisterAddress is null");
        }
    }

    public String execute(String path, Object data) throws IOException {
        HttpPost httpPost = new HttpPost("http://" + getIpPort() + path);
        httpPost.setEntity(new StringEntity(gson.toJson(data)));
        CloseableHttpResponse response = closeableHttpClient.execute(httpPost);
        HttpEntity httpEntity = response.getEntity();
        return EntityUtils.toString(httpEntity);
    }

    public String getIpPort() {
        if (!StringUtil.isEmpty(backendRegisterAddress)) {
            String[] ipPorts = backendRegisterAddress.split(",");
            if (ipPorts.length == 0) {
                return null;
            }
            ThreadLocalRandom random = ThreadLocalRandom.current();
            return ipPorts[random.nextInt(0, ipPorts.length)];
        }
        return null;
    }
}
