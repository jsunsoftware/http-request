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

import org.apache.hc.core5.http.HttpHost;

import java.util.HashMap;
import java.util.Map;

class HostPoolConfig {
    /**
     * Total cap on concurrent open TCP connections across all routes the client talks to.
     * Defaults to 128 — sized for typical microservice / backend workloads talking to a handful
     * of upstream hosts. Tune up for high-throughput aggregators or scrapers that fan out across
     * many hosts; tune down in tightly-resourced containers.
     */
    private int maxPoolSize = 128;

    /**
     * Cap on concurrent open TCP connections per route ((host, port, scheme) tuple), used when no
     * route-specific cap has been registered via {@link #setMaxPoolSizePerRoute}. Defaults to 32.
     * <p>
     * Why {@code 32}, not {@link #maxPoolSize}: when {@code defaultMaxPoolSizePerRoute == maxPoolSize},
     * a single hot host can saturate the entire pool — a parallel request to a different host
     * then waits for the connection-request timeout. Keeping per-route at roughly {@code total / 4}
     * matches the convention used by Apache HC, AWS SDK, and Spring's typical configurations and
     * preserves multi-host fairness under load.
     */
    private int defaultMaxPoolSizePerRoute = 32;

    private final Map<HttpHost, Integer> httpHostToMaxRoutePoolSize = new HashMap<>();

    private HostPoolConfig() {
    }

    /**
     * Sets the total cap on concurrent open connections across all routes. Default is {@code 128}.
     *
     * @param maxPoolSize value
     *
     * @return Builder instance
     */
    public HostPoolConfig setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
        return this;
    }

    /**
     * Sets the cap on concurrent open connections per route, used when no route-specific cap has
     * been registered via {@link #setMaxPoolSizePerRoute}. Default is {@code 32}.
     * <p>
     * Setting this equal to or greater than {@link #setMaxPoolSize(int)} lets a single hot host
     * starve other routes — only do that if you genuinely talk to a single upstream.
     *
     * @param defaultMaxPoolSizePerRoute value
     *
     * @return Builder instance
     */
    public HostPoolConfig setDefaultMaxPoolSizePerRoute(int defaultMaxPoolSizePerRoute) {
        this.defaultMaxPoolSizePerRoute = defaultMaxPoolSizePerRoute;
        return this;
    }

    /**
     * Set the connection pool default max size of concurrent connections to a specific route.
     *
     * @param httpHost         httpHost
     * @param maxRoutePoolSize maxRoutePoolSize
     *
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
