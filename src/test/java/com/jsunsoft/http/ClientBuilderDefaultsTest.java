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

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the connection-pool defaults so they don't silently drift back to "single hot host can
 * saturate the entire pool" (the bug §3.15 in the analysis doc flagged). The 4× total-to-per-route
 * ratio is a deliberate fairness guarantee — see {@code HostPoolConfig} Javadoc and
 * {@code MIGRATION.md} for the rationale.
 */
class ClientBuilderDefaultsTest {

    @Test
    void buildIsIdempotent_customizerSeesFreshBuilderEachTime() throws IOException {
        // Regression guard for §3.18: previously, ClientBuilder kept its RequestConfig.Builder and
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
            assertTrue(first != second, "Each build() must produce an independent client");
        }

        assertEquals(2, seenRequestBuilders.size(), "customizer invoked once per build()");
        assertTrue(seenRequestBuilders.get(0) != seenRequestBuilders.get(1),
                "Each build() must hand the customizer a FRESH RequestConfig.Builder, not the " +
                        "persistent field — otherwise stateful customizers compound across builds.");

        assertEquals(2, seenConnectionBuilders.size(), "customizer invoked once per build()");
        assertTrue(seenConnectionBuilders.get(0) != seenConnectionBuilders.get(1),
                "Each build() must hand the customizer a FRESH ConnectionConfig.Builder.");
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
}
