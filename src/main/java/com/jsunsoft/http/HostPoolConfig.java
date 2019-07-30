package com.jsunsoft.http;

/*
 * Copyright 2017 Benik Arakelyan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.http.HttpHost;

import java.util.HashMap;
import java.util.Map;

class HostPoolConfig {
    private int maxPoolSize = 128;
    private int defaultMaxPoolSizePerRoute = 128;
    private Map<HttpHost, Integer> httpHostToMaxRoutePoolSize = new HashMap<>();

    private HostPoolConfig() {
    }

    /**
     * Set the connection pool max size of concurrent connections, which is 128 by default.
     *
     * @param maxPoolSize value
     * @return Builder instance
     */
    public HostPoolConfig setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
        return this;
    }

    /**
     * Set the connection pool default max size of concurrent connections to a specific route, which is 128 by default.
     *
     * @param defaultMaxPoolSizePerRoute value
     * @return Builder instance
     */
    public HostPoolConfig setMaxPoolSizePerRoute(int defaultMaxPoolSizePerRoute) {
        this.defaultMaxPoolSizePerRoute = defaultMaxPoolSizePerRoute;
        return this;
    }

    /**
     * Set the connection pool default max size of concurrent connections to a specific route.
     *
     * @param httpHost         httpHost
     * @param maxRoutePoolSize maxRoutePoolSize
     * @return HostPoolConfig
     */
    public HostPoolConfig setMaxPoolSizePerRoute(HttpHost httpHost, int maxRoutePoolSize) {
        httpHostToMaxRoutePoolSize.put(httpHost, maxRoutePoolSize);
        return this;
    }

    public static HostPoolConfig create() {
        return new HostPoolConfig();
    }

    int getMaxPoolSize() {
        return maxPoolSize;
    }

    public int getDefaultMaxPoolSizePerRoute() {
        return defaultMaxPoolSizePerRoute;
    }

    public Map<HttpHost, Integer> getHttpHostToMaxPoolSize() {
        return httpHostToMaxRoutePoolSize;
    }
}
