/*
 * Copyright (c) 2026. Benik Arakelyan
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

package com.jsunsoft.http;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the connection-pool defaults so they don't silently drift back to "single hot host can
 * saturate the entire pool." The 4× total-to-per-route ratio is a deliberate fairness
 * guarantee — see {@code HostPoolConfig} Javadoc and {@code MIGRATION.md} for the rationale.
 */
class ClientBuilderDefaultsTest {

    @Test
    void buildIsIdempotent_customizerSeesFreshBuilderEachTime() throws IOException {
        // Regression guard: previously, ClientBuilder kept its RequestConfig.Builder and
        // ConnectionConfig.Builder as fields, and customizers were applied to those persistent
        // instances on every build() call. The user-visible bug surfaces only when a customizer
        // makes a *state-dependent* decision — i.e., it inspects what's already on the Builder
        // and reacts to it. We probe that with a customizer that records each Builder reference
        // it sees: after two build() calls, the customizer must have observed two DIFFERENT
        // Builder instances (one per build), not the same persistent field reused across calls.
        java.util.List<RequestConfig.Builder> seenRequestBuilders = new java.util.ArrayList<>();
        java.util.List<org.apache.hc.client5.http.config.ConnectionConfig.Builder> seenConnectionBuilders = new java.util.ArrayList<>();

        ClientBuilder builder = ClientBuilder.create()
                .addDefaultRequestConfigCustomizer(seenRequestBuilders::add)
                .addDefaultConnectionConfigCustomizer(seenConnectionBuilders::add);

        try (CloseableHttpClient first = builder.build();
             CloseableHttpClient second = builder.build()) {
            assertNotSame(first, second, "Each build() must produce an independent client");
        }

        assertEquals(2, seenRequestBuilders.size(), "customizer invoked once per build()");
        assertNotSame(seenRequestBuilders.get(0), seenRequestBuilders.get(1), "Each build() must hand the customizer a FRESH RequestConfig.Builder, not the " +
                "persistent field — otherwise stateful customizers compound across builds.");

        assertEquals(2, seenConnectionBuilders.size(), "customizer invoked once per build()");
        assertNotSame(seenConnectionBuilders.get(0), seenConnectionBuilders.get(1), "Each build() must hand the customizer a FRESH ConnectionConfig.Builder.");
    }

    @Test
    void http1HeaderLimitsAndTlsKnobsCompileAndDoNotBlowUp() throws IOException {
        // Smoke test: setMaxHeaderCount / setMaxLineLength / setTlsVersions / setCipherSuites
        // all flow through to the underlying Apache HC5 builders without throwing during
        // build(). End-to-end behavior (a malicious server with 1000-headers is rejected at the
        // wire) requires controlling the wire — out of scope for a unit test. This pin keeps
        // the API call sites compiling.
        try (CloseableHttpClient client = ClientBuilder.create()
                .setMaxHeaderCount(64)
                .setMaxLineLength(8192)
                .setTlsVersions("TLSv1.3", "TLSv1.2")
                .setCipherSuites("TLS_AES_256_GCM_SHA384", "TLS_AES_128_GCM_SHA256")
                .build()) {
            assertNotNull(client);
        }

        // Negative values for header limits are accepted as "use Apache default" — pin that
        // they don't blow up and don't override anything weird.
        try (CloseableHttpClient client = ClientBuilder.create()
                .setMaxHeaderCount(-1)
                .setMaxLineLength(-1)
                .build()) {
            assertNotNull(client);
        }
    }

    @Test
    void poolDefaultsPreserveMultiHostFairness() throws IOException {
        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create().buildWithResources()) {
            PoolingHttpClientConnectionManager pool = (PoolingHttpClientConnectionManager) resources.getConnectionManager();

            assertEquals(128, pool.getMaxTotal(), "default total pool size");
            assertEquals(32, pool.getDefaultMaxPerRoute(), "default per-route cap");

            // Invariant guarding the multi-host-fairness rationale: per-route must stay strictly
            // below total so a single hot host cannot saturate the entire pool.
            assertTrue(pool.getDefaultMaxPerRoute() < pool.getMaxTotal(),
                    "perRoute (" + pool.getDefaultMaxPerRoute() + ") must be < total (" + pool.getMaxTotal() + ") to preserve multi-host fairness");
        }
    }

    @Test
    void poolSizes_rejectNonPositiveValues() {
        // Apache HC5's pool builder applies non-positive sizes as "not set" and silently falls
        // back to its internal defaults (25 total / 5 per route — empirically verified). A
        // misconfiguration must fail loud at the setter instead.
        ClientBuilder builder = ClientBuilder.create();

        assertThrows(IllegalArgumentException.class, () -> builder.setMaxPoolSize(0));
        assertThrows(IllegalArgumentException.class, () -> builder.setMaxPoolSize(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.setDefaultMaxPoolSizePerRoute(0));
        assertThrows(IllegalArgumentException.class, () -> builder.setDefaultMaxPoolSizePerRoute(-7));
        assertThrows(IllegalArgumentException.class, () -> builder.setMaxPoolSizePerRoute(new HttpHost("example.com"), 0));
        assertThrows(NullPointerException.class, () -> builder.setMaxPoolSizePerRoute(null, 5));
    }

    // ------------------------------------------------------------------------------------------
    // setMaxPoolSizePerRoute must register pool keys shaped exactly like the routes the runtime
    // route planner produces (DefaultRoutePlanner: scheme-default port resolved, secure flag for
    // https, proxy hop included) — PoolingHttpClientConnectionManager matches routes by full
    // equality, so any shape mismatch silently disables the cap.
    // ------------------------------------------------------------------------------------------

    @Test
    void maxPoolSizePerRoute_portlessHttpHost_capsTheNormalizedRuntimeRoute() throws IOException {
        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create()
                .setMaxPoolSizePerRoute(new HttpHost("example.com"), 7)
                .buildWithResources()) {
            PoolingHttpClientConnectionManager pool = (PoolingHttpClientConnectionManager) resources.getConnectionManager();

            HttpRoute runtimeRoute = new HttpRoute(new HttpHost("http", "example.com", 80));
            assertEquals(7, pool.getMaxPerRoute(runtimeRoute),
                    "a portless HttpHost must cap the runtime route with the scheme-default port resolved");
        }
    }

    @Test
    void maxPoolSizePerRoute_httpsHost_capsTheSecureRuntimeRoute() throws IOException {
        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create()
                .setMaxPoolSizePerRoute(new HttpHost("https", "secure.example.com"), 5)
                .buildWithResources()) {
            PoolingHttpClientConnectionManager pool = (PoolingHttpClientConnectionManager) resources.getConnectionManager();

            HttpRoute runtimeRoute = new HttpRoute(new HttpHost("https", "secure.example.com", 443), null, true);
            assertEquals(5, pool.getMaxPerRoute(runtimeRoute),
                    "an https host must cap the runtime route: port 443 resolved AND the secure flag set");
        }
    }

    @Test
    void maxPoolSizePerRoute_explicitPortHttpHost_stillApplies() throws IOException {
        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create()
                .setMaxPoolSizePerRoute(new HttpHost("example.com", 8080), 9)
                .buildWithResources()) {
            PoolingHttpClientConnectionManager pool = (PoolingHttpClientConnectionManager) resources.getConnectionManager();

            HttpRoute runtimeRoute = new HttpRoute(new HttpHost("http", "example.com", 8080));
            assertEquals(9, pool.getMaxPerRoute(runtimeRoute),
                    "the explicit-port http case must keep working");
        }
    }

    @Test
    void maxPoolSizePerRoute_explicitPortHttpsHost_capsTheSecureRoute() throws IOException {
        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create()
                .setMaxPoolSizePerRoute(new HttpHost("https", "secure.example.com", 8443), 4)
                .buildWithResources()) {
            PoolingHttpClientConnectionManager pool = (PoolingHttpClientConnectionManager) resources.getConnectionManager();

            HttpRoute runtimeRoute = new HttpRoute(new HttpHost("https", "secure.example.com", 8443), null, true);
            assertEquals(4, pool.getMaxPerRoute(runtimeRoute),
                    "an https host must cap the SECURE runtime route even when the port is explicit");
        }
    }

    @Test
    void maxPoolSizePerRoute_withProxyConfigured_capsTheProxiedRoute() throws IOException {
        HttpHost proxy = new HttpHost("proxy.local", 3128);
        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create()
                .proxy(proxy)
                .setMaxPoolSizePerRoute(new HttpHost("https", "secure.example.com"), 3)
                .buildWithResources()) {
            PoolingHttpClientConnectionManager pool = (PoolingHttpClientConnectionManager) resources.getConnectionManager();

            HttpRoute runtimeRoute = new HttpRoute(new HttpHost("https", "secure.example.com", 443), null, proxy, true);
            assertEquals(3, pool.getMaxPerRoute(runtimeRoute),
                    "with a proxy configured, the cap must land on the proxied route the planner produces");
        }
    }
}
