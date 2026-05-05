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
