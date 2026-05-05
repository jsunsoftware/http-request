/*
 * Copyright (c) 2024. Benik Arakelyan
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

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class HttpRequestConnectionManagementLiveTest {

    @RegisterExtension
    static WireMockExtension server1 = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension server2 = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension server3 = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @BeforeEach
    void setup() {
        server1.resetAll();
        server2.resetAll();
        server3.resetAll();

        server1.stubFor(get(urlEqualTo("/")).willReturn(aResponse().withStatus(200).withBody("ok")));
        server2.stubFor(get(urlEqualTo("/")).willReturn(aResponse().withStatus(200).withBody("ok")));
        server3.stubFor(get(urlEqualTo("/")).willReturn(aResponse().withStatus(200).withBody("ok")));
    }

    private static String server1Url() {
        return server1.getRuntimeInfo().getHttpBaseUrl();
    }

    private static String server2Url() {
        return server2.getRuntimeInfo().getHttpBaseUrl();
    }

    private static String server3Url() {
        return server3.getRuntimeInfo().getHttpBaseUrl();
    }

    @Test
    void whenConnectionsNeededGreaterThanMaxTotal_thenReuseConnections() throws InterruptedException {
        // This test was authored against the old defaults (maxPoolSize=128, perRoute=128) where
        // perRoute == maxTotal let all 128 threads acquire connections simultaneously without
        // queuing — the 5 ms connectionRequestTimeout was workable only under that assumption.
        // The current default perRoute is 32 (see HostPoolConfig — preserves multi-host fairness),
        // so we must restore the original perRoute explicitly to exercise this test's authored
        // single-route saturation scenario rather than the new per-route-queuing scenario.
        HttpRequest httpRequest = HttpRequestBuilder.create(
                ClientBuilder.create()
                        .setDefaultMaxPoolSizePerRoute(128)
                        .setConnectionRequestTimeout(5)
                        .build()).build();

        int validThreadSize = 128;
        final HttpRequestThread[] threads = new HttpRequestThread[validThreadSize];

        for (int i = 0; i < threads.length; i++) {
            threads[i] = new HttpRequestThread(httpRequest.target(server1Url()));
        }

        for (HttpRequestThread thread : threads) {
            thread.start();
        }

        for (HttpRequestThread thread : threads) {
            thread.join();
            assertFalse(thread.getResponseHandler().getConnectionFailureType().isConnectionPoolEmpty());
        }
    }

    @Test
    void whenTwoConnectionsForTwoRequests_thenNoExceptions() throws InterruptedException {
        HttpRequest httpRequest1 = HttpRequestBuilder.create(ClientBuilder.create().build()).build();
        HttpRequest httpRequest2 = HttpRequestBuilder.create(ClientBuilder.create().build()).build();

        HttpRequestThread thread1 = new HttpRequestThread(httpRequest1.target(server1Url()));
        HttpRequestThread thread2 = new HttpRequestThread(httpRequest2.target(server2Url()));

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
        assertTrue(thread1.getResponseHandler().getConnectionFailureType().isNotFailed());
        assertTrue(thread2.getResponseHandler().getConnectionFailureType().isNotFailed());
    }

    @Test
    void whenPollingConnectionManagerIsConfiguredOnHttpClient_thenNoExceptions() throws IOException {
        try (ClientBuilder.HttpClientWithResourcesWrapper cch = ClientBuilder.create().buildWithResources()) {
            BasicHttpRequest httpRequest = (BasicHttpRequest) HttpRequestBuilder.create(cch.getClient()).build();
            httpRequest.target(server1Url()).rawGet();
            assertEquals(0, ((PoolingHttpClientConnectionManager) cch.getConnectionManager()).getTotalStats().getLeased());
        }

    }

    @Test
    void whenPollingConnectionManagerIsConfiguredOnHttpClient_thenNoExceptionsImmutableWebTarget() throws IOException {
        try (ClientBuilder.HttpClientWithResourcesWrapper cch = ClientBuilder.create().buildWithResources()) {
            BasicHttpRequest httpRequest = (BasicHttpRequest) HttpRequestBuilder.create(cch.getClient()).build();

            Response response = httpRequest.immutableTarget(server1Url()).get();
            assertEquals(1, ((PoolingHttpClientConnectionManager) cch.getConnectionManager()).getTotalStats().getLeased());
            response.close();
            assertEquals(0, ((PoolingHttpClientConnectionManager) cch.getConnectionManager()).getTotalStats().getLeased());
        }
    }

    @Test
    void whenThreeConnectionsForThreeRequests_thenConnectionsAreNotLeased() throws InterruptedException, IOException {
        try (ClientBuilder.HttpClientWithResourcesWrapper cch = ClientBuilder.create().buildWithResources()) {

            HttpRequest httpRequest1 = HttpRequestBuilder.create(cch.getClient()).build();
            final HttpRequestThread thread1 = new HttpRequestThread(httpRequest1.target(server1Url()));
            final HttpRequestThread thread2 = new HttpRequestThread(httpRequest1.target(server2Url()));
            final HttpRequestThread thread3 = new HttpRequestThread(httpRequest1.target(server3Url()));
            thread1.start();
            thread2.start();
            thread3.start();
            thread1.join();
            thread2.join(1000);
            thread3.join();
            assertEquals(0, ((PoolingHttpClientConnectionManager) cch.getConnectionManager()).getTotalStats().getLeased());
        }
    }

    @Test
    void whenConnectionsNeededGreaterThanMaxTotal_thenReuseConnectionsImmutableWebTarget() throws InterruptedException {
        // See note in whenConnectionsNeededGreaterThanMaxTotal_thenReuseConnections — restoring
        // perRoute=128 keeps this test's authored "no per-route queuing on a single host" premise.
        HttpRequest httpRequest = HttpRequestBuilder.create(
                ClientBuilder.create()
                        .setDefaultMaxPoolSizePerRoute(128)
                        .setConnectionRequestTimeout(5)
                        .build()).build();

        int validThreadSize = 128;
        final HttpRequestThread[] threads = new HttpRequestThread[validThreadSize];

        for (int i = 0; i < threads.length; i++) {
            threads[i] = new HttpRequestThread(httpRequest.immutableTarget(server1Url()));
        }

        for (HttpRequestThread thread : threads) {
            thread.start();
        }

        for (HttpRequestThread thread : threads) {
            thread.join();
            assertFalse(thread.getResponseHandler().getConnectionFailureType().isConnectionPoolEmpty());
        }
    }

    @Test
    void whenTwoConnectionsForTwoRequests_thenNoExceptionsImmutableWebTarget() throws InterruptedException {
        HttpRequest httpRequest1 = HttpRequestBuilder.create(ClientBuilder.create().build()).build();
        HttpRequest httpRequest2 = HttpRequestBuilder.create(ClientBuilder.create().build()).build();

        HttpRequestThread thread1 = new HttpRequestThread(httpRequest1.immutableTarget(server1Url()));
        HttpRequestThread thread2 = new HttpRequestThread(httpRequest2.immutableTarget(server2Url()));

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
        assertTrue(thread1.getResponseHandler().getConnectionFailureType().isNotFailed());
        assertTrue(thread2.getResponseHandler().getConnectionFailureType().isNotFailed());
    }

    @Test
    void whenThreeConnectionsForThreeRequests_thenConnectionsAreNotLeasedImmutableWebTarget() throws InterruptedException, IOException {
        try (ClientBuilder.HttpClientWithResourcesWrapper cch = ClientBuilder.create().buildWithResources()) {

            HttpRequest httpRequest1 = HttpRequestBuilder.create(cch.getClient()).build();
            final HttpRequestThread thread1 = new HttpRequestThread(httpRequest1.immutableTarget(server1Url()));
            final HttpRequestThread thread2 = new HttpRequestThread(httpRequest1.immutableTarget(server2Url()));
            final HttpRequestThread thread3 = new HttpRequestThread(httpRequest1.immutableTarget(server3Url()));
            thread1.start();
            thread2.start();
            thread3.start();
            thread1.join();
            thread2.join(1000);
            thread3.join();
            assertEquals(0, ((PoolingHttpClientConnectionManager) cch.getConnectionManager()).getTotalStats().getLeased());
        }
    }

    @Test
    void perRouteOverride_isAppliedToSpecificHostAndOthersFallThroughToDefault() throws IOException {
        // setMaxPoolSizePerRoute(host, n) is the escape hatch the new lower default per-route cap
        // (32) is meant to enable: a service that mostly fans out across hosts but has one or two
        // hot upstreams should be able to raise the per-route budget for those specifically. This
        // test verifies the override is applied to the right route and that other routes still see
        // the configured default.
        HttpHost hot = new HttpHost("http", "hot.example.com", 80);
        HttpHost cold = new HttpHost("http", "cold.example.com", 80);

        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create()
                .setMaxPoolSize(64)
                .setDefaultMaxPoolSizePerRoute(8)
                .setMaxPoolSizePerRoute(hot, 32)
                .buildWithResources()) {
            PoolingHttpClientConnectionManager pool = (PoolingHttpClientConnectionManager) resources.getConnectionManager();

            assertEquals(64, pool.getMaxTotal());
            assertEquals(8, pool.getDefaultMaxPerRoute());
            assertEquals(32, pool.getMaxPerRoute(new HttpRoute(hot)),
                    "Override host must use the configured per-route cap (32), not the default (8)");
            assertEquals(8, pool.getMaxPerRoute(new HttpRoute(cold)),
                    "Non-override host must fall through to the default per-route cap (8)");
        }
    }

    @Test
    void hotHostCannotStarveOtherRoutes_whenPerRouteIsLessThanTotal() throws InterruptedException, IOException {
        // The multi-host fairness invariant: with perRoute < maxTotal, saturating one route's per-route
        // quota must NOT block requests to other routes. Configures perRoute=2 / maxTotal=4, fills
        // server1 with 2 in-flight slow requests, then verifies server2 still completes promptly
        // because the total budget has slack beyond server1's per-route cap.
        server1.stubFor(get(urlEqualTo("/slow"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(3000).withBody("ok")));

        HttpRequest httpRequest = HttpRequestBuilder.create(
                ClientBuilder.create()
                        .setMaxPoolSize(4)
                        .setDefaultMaxPoolSizePerRoute(2)
                        .setConnectionRequestTimeout(1000)
                        .build()).build();

        // Saturate server1 with 2 slow in-flight requests.
        HttpRequestThread hot1 = new HttpRequestThread(httpRequest.target(server1Url()).path("/slow"));
        HttpRequestThread hot2 = new HttpRequestThread(httpRequest.target(server1Url()).path("/slow"));
        hot1.start();
        hot2.start();
        Thread.sleep(500); // give them time to establish + start blocking on the response delay

        // server2 must NOT queue: with perRoute=2 < maxTotal=4, there's room for 2 more
        // connections beyond server1's per-route cap. The 1 s acquisition timeout is far longer
        // than a healthy localhost acquire (microseconds), so any timeout here is the bug.
        long started = System.currentTimeMillis();
        try (Response response = httpRequest.target(server2Url()).get()) {
            long elapsed = System.currentTimeMillis() - started;
            assertEquals(200, response.getCode());
            assertTrue(elapsed < 1000,
                    "server2 must not queue while server1 is per-route-saturated; took " + elapsed + " ms");
        }

        hot1.join();
        hot2.join();
    }

    @Test
    void hotHostStarvesOtherRoutes_whenPerRouteEqualsTotal() throws InterruptedException {
        // Negative regression guard: with perRoute == maxTotal, a saturated route DOES starve
        // other routes. This test pins the broken behavior so any future change that re-aligns
        // the default per-route to the total cap will fail loudly here and force a reviewer to
        // confront the multi-host-fairness regression.
        server1.stubFor(get(urlEqualTo("/slow"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(3000).withBody("ok")));

        HttpRequest httpRequest = HttpRequestBuilder.create(
                ClientBuilder.create()
                        .setMaxPoolSize(2)
                        .setDefaultMaxPoolSizePerRoute(2) // == maxTotal — the bug condition
                        .setConnectionRequestTimeout(500)
                        .build()).build();

        HttpRequestThread hot1 = new HttpRequestThread(httpRequest.target(server1Url()).path("/slow"));
        HttpRequestThread hot2 = new HttpRequestThread(httpRequest.target(server1Url()).path("/slow"));
        hot1.start();
        hot2.start();
        Thread.sleep(500);

        // server2 cannot acquire — server1 has consumed all 2 of the total budget. The handler
        // surfaces a CONNECTION_POOL_IS_EMPTY classification (mapped from
        // ConnectionRequestTimeoutException in BasicWebTarget).
        HttpRequestThread server2Thread = new HttpRequestThread(httpRequest.target(server2Url()));
        server2Thread.start();
        server2Thread.join();
        assertTrue(server2Thread.getResponseHandler().getConnectionFailureType().isConnectionPoolEmpty(),
                "When perRoute == maxTotal, a saturated route must starve other routes (the multi-host fairness bug). " +
                        "If this assertion fails, the pool default has been tightened in a way that fixed " +
                        "the bug — update the test to match the new (good) behavior or remove it.");

        hot1.join();
        hot2.join();
    }
}
