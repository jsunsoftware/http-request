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

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for invariants the Javadoc commits to but that previously had no explicit coverage:
 * <ul>
 *   <li>{@link HttpRequest} is immutable + thread-safe — concurrent {@code target(...)} calls
 *       must each produce their own {@link WebTarget} with no cross-thread leakage.</li>
 *   <li>{@link HttpRequest#immutableTarget(String)} returns a thread-safe target — concurrent
 *       fluent operations on a shared instance produce independent snapshots without interfering
 *       with one another.</li>
 *   <li>A failed response-body deserialization must not poison the connection pool — subsequent
 *       requests to the same host complete normally.</li>
 * </ul>
 */
class ThreadSafetyAndPoolHealthTest {

    @RegisterExtension
    static WireMockExtension server = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private CloseableHttpClient client;

    @BeforeEach
    void setUp() {
        client = new ClientBuilder().build();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null) client.close();
    }

    private String httpUri(String path) {
        return server.getRuntimeInfo().getHttpBaseUrl() + path;
    }

    @Test
    void httpRequest_concurrentTargetCalls_produceIndependentWebTargets() throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
        // HttpRequest is documented as immutable and thread-safe. Concurrent
        // target(...) calls must each return their own configurable WebTarget — mutation on one
        // must not leak into another (which it would only do if HttpRequest itself were sharing
        // mutable state across calls).
        HttpRequest httpRequest = HttpRequestBuilder.create(client).build();

        int threads = 32;
        int callsPerThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            results.add(pool.submit(() -> {
                ready.countDown();
                fire.await();
                for (int i = 0; i < callsPerThread; i++) {
                    String uniqueHeaderValue = "thread-" + threadId + "-call-" + i;
                    WebTarget target = httpRequest.target("http://example.invalid/")
                            .addHeader("X-Probe", uniqueHeaderValue);
                    // The header just added must be the one we see — no other thread's header
                    // should have leaked into this target.

                    String observed = ((BasicWebTarget) target).getHttpUriRequestBuilder()
                            .getFirstHeader("X-Probe").getValue();
                    if (!uniqueHeaderValue.equals(observed)) {
                        return false;
                    }
                }
                return true;
            }));
        }

        ready.await();
        fire.countDown();
        for (Future<Boolean> f : results) {
            assertTrue(f.get(15, TimeUnit.SECONDS), "Cross-thread WebTarget leakage detected");
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void immutableTarget_concurrentFluentCalls_produceIndependentSnapshots() throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
        // The WebTarget returned from immutableTarget(...) is documented as
        // safe to share across threads. Concurrent fluent calls on the SAME shared instance
        // must produce independent results — i.e., one thread's addHeader must not be visible
        // to another thread's addHeader, since each call returns a fresh instance.
        HttpRequest httpRequest = HttpRequestBuilder.create(client).build();
        WebTarget shared = httpRequest.immutableTarget("http://example.invalid/");

        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            results.add(pool.submit(() -> {
                ready.countDown();
                fire.await();
                String headerValue = "thread-" + threadId;
                WebTarget mine = shared.addHeader("X-Probe", headerValue);
                // mine must reflect THIS thread's mutation only, not anyone else's.
                ImmutableWebTarget asImmutable = (ImmutableWebTarget) mine;
                org.apache.hc.core5.http.Header[] probes = asImmutable.getHttpUriRequestBuilder().getHeaders("X-Probe");
                int n = probes == null ? 0 : probes.length;
                if (n != 1) {
                    return "expected exactly 1 X-Probe header, got " + n;
                }
                return probes[0].getValue();
            }));
        }

        ready.await();
        fire.countDown();
        // Every thread should see its own value, all distinct.
        ConcurrentHashMap<String, Boolean> seen = new ConcurrentHashMap<>();
        for (int i = 0; i < threads; i++) {
            String val = results.get(i).get(15, TimeUnit.SECONDS);
            assertEquals("thread-" + i, val, "Thread " + i + " observed unexpected header value");
            assertNull(seen.putIfAbsent(val, Boolean.TRUE), "Header value " + val + " seen twice");
        }
        // And the shared instance itself must not have any X-Probe headers — none of the
        // mutations leaked back.
        org.apache.hc.core5.http.Header[] sharedProbes = ((ImmutableWebTarget) shared).getHttpUriRequestBuilder().getHeaders("X-Probe");
        int sharedCount = sharedProbes == null ? 0 : sharedProbes.length;
        assertEquals(0, sharedCount,
                "Concurrent addHeader on shared immutableTarget leaked back to the original instance");

        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void connectionPool_isNotPoisonedByDeserializationFailure() throws IOException {
        // Pool health: a server that returns malformed JSON triggers a deserialization
        // failure on the first request. The connection-management code path must release the
        // connection back to the pool (or discard it cleanly) — a follow-up request to the same
        // host must succeed without blocking on connectionRequestTimeout.
        server.stubFor(get(urlEqualTo("/malformed"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{this-is-not-valid-json")));
        server.stubFor(get(urlEqualTo("/healthy"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"value\":42}")));

        // Tight per-route cap so a leaked connection would force the next request to time out.
        try (CloseableHttpClient localClient = ClientBuilder.create()
                .setMaxPoolSize(2)
                .setDefaultMaxPoolSizePerRoute(1)
                .setConnectionRequestTimeout(2000)
                .build()) {

            HttpRequest httpRequest = HttpRequestBuilder.create(localClient).build();

            // First request: deserialization fails. The library wraps as 502 in the
            // ResponseHandler, but the connection MUST be released regardless.
            ResponseHandler<HealthData> bad = httpRequest.target(httpUri("/malformed")).get(HealthData.class);
            assertFalse(bad.isSuccess(), "Malformed JSON should surface as a deserialization failure");

            // Many follow-up requests — none of them should fail with CONNECTION_POOL_IS_EMPTY.
            // If the failed request leaked its connection, this loop times out at request 2.
            AtomicInteger successes = new AtomicInteger();
            for (int i = 0; i < 10; i++) {
                ResponseHandler<HealthData> rh = httpRequest.target(httpUri("/healthy")).get(HealthData.class);
                if (rh.isSuccess() && rh.orElseThrow().value == 42) {
                    successes.incrementAndGet();
                }
            }
            assertEquals(10, successes.get(),
                    "Connection pool was poisoned by the prior deserialization failure");
        }
    }

    static class HealthData {
        public int value;
    }
}
