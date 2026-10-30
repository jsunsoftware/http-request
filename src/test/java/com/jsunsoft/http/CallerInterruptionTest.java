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
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins that interrupting the calling thread is reported as a cancellation — a
 * {@link RequestException}, with the interrupt flag left set — and never as a synthetic 503:
 * nothing failed on the server or the network.
 */
class CallerInterruptionTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private String uri(String path) {
        return wm.getRuntimeInfo().getHttpBaseUrl() + path;
    }

    private static long leased(ClientBuilder.HttpClientWithResourcesWrapper resources) {
        return ((PoolingHttpClientConnectionManager) resources.getConnectionManager()).getTotalStats().getLeased();
    }

    @Test
    void interruptedBeforeTheRequest_throwsRequestException() throws IOException {
        wm.stubFor(get(urlEqualTo("/any")).willReturn(ok("any")));

        try (CloseableHttpClient client = ClientBuilder.create().build()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(client).build();
            Thread.currentThread().interrupt();
            try {
                // Apache HC5 cancels the connection lease of an already-interrupted thread.
                RequestException e = assertThrows(RequestException.class,
                        () -> httpRequest.target(uri("/any")).get(String.class));

                assertInstanceOf(CancellationException.class, e.getCause());
                assertTrue(Thread.currentThread().isInterrupted(), "the interrupt flag must stay set");
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void interruptedWhileWaitingForPooledConnection_throwsRequestException() throws IOException {
        wm.stubFor(get(urlEqualTo("/held")).willReturn(ok("held")));
        wm.stubFor(get(urlEqualTo("/waiting")).willReturn(ok("waiting")));
        Thread caller = Thread.currentThread();
        Thread interrupter = new Thread(() -> {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            try {
                while (caller.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) {
                    Thread.sleep(5);
                }
            } catch (InterruptedException ignored) {
                return;
            }
            caller.interrupt();
        });

        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create()
                .setMaxPoolSize(1)
                .setDefaultMaxPoolSizePerRoute(1)
                .buildWithResources()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(resources.getClient()).build();

            // The only pooled connection stays leased until `held` is closed, so the next request
            // waits for it until the interrupter (seeing the caller block) interrupts the wait.
            try (Response held = httpRequest.target(uri("/held")).get()) {
                assertEquals(200, held.getCode());
                interrupter.start();
                try {
                    RequestException e = assertThrows(RequestException.class,
                            () -> httpRequest.target(uri("/waiting")).get(String.class));

                    assertInstanceOf(InterruptedIOException.class, e.getCause());
                    assertTrue(Thread.currentThread().isInterrupted(), "the interrupt flag must stay set");
                } finally {
                    Thread.interrupted();
                }
            }
        }
    }

    @Test
    void interruptedWhileReadingTheBody_throwsRequestException() throws IOException {
        wm.stubFor(get(urlEqualTo("/body")).willReturn(ok("body")));
        // Behaves like a virtual thread interrupted in a socket read: the JDK closes the socket and
        // the read fails with the interrupt flag set.
        ResponseBodyReader<String> interruptedRead = new ResponseBodyReader<>() {
            @Override
            public boolean isReadable(ResponseBodyReadableContext bodyReadableContext) {
                return true;
            }

            @Override
            public String read(ResponseBodyReaderContext<String> bodyReaderContext) throws IOException {
                Thread.currentThread().interrupt();
                throw new SocketException("Closed by interrupt");
            }
        };

        try (CloseableHttpClient client = ClientBuilder.create().build()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(client).addBodyReader(interruptedRead).build();
            try {
                RequestException eager = assertThrows(RequestException.class,
                        () -> httpRequest.target(uri("/body")).get(String.class));
                assertInstanceOf(SocketException.class, eager.getCause());
                assertTrue(Thread.interrupted(), "the interrupt flag must stay set");

                try (Response response = httpRequest.target(uri("/body")).get()) {
                    RequestException lazy = assertThrows(RequestException.class, () -> response.readEntity(String.class));
                    assertInstanceOf(SocketException.class, lazy.getCause());
                    assertTrue(Thread.currentThread().isInterrupted(), "the interrupt flag must stay set");
                }
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void interruptedWhileWaitingToRetry_throwsRequestException_andDoesNotLeakConnection() throws IOException {
        wm.stubFor(get(urlEqualTo("/retried")).willReturn(aResponse().withStatus(500)));
        Thread caller = Thread.currentThread();
        CountDownLatch waiting = new CountDownLatch(1);
        RetryContext slowRetry = new RetryContext() {
            @Override
            public int getRetryCount() {
                return 3;
            }

            @Override
            public boolean mustBeRetried(RetryAttempt attempt) {
                return true;
            }

            @Override
            public Duration getRetryDelay(RetryAttempt attempt) {
                waiting.countDown();
                return Duration.ofSeconds(30);
            }
        };
        Thread interrupter = new Thread(() -> {
            try {
                if (waiting.await(10, TimeUnit.SECONDS)) {
                    caller.interrupt();
                }
            } catch (InterruptedException ignored) {
                // The test thread is done either way.
            }
        });

        try (ClientBuilder.HttpClientWithResourcesWrapper resources = ClientBuilder.create().buildWithResources()) {
            HttpRequest httpRequest = HttpRequestBuilder.create(resources.getClient()).build();
            interrupter.start();
            try {
                RequestException e = assertThrows(RequestException.class,
                        () -> httpRequest.retryableTarget(uri("/retried"), slowRetry).get(String.class));

                assertInstanceOf(InterruptedException.class, e.getCause());
                assertTrue(Thread.currentThread().isInterrupted(), "the interrupt flag must stay set");
            } finally {
                Thread.interrupted();
            }

            assertEquals(0, leased(resources), "the response being retried must not leak its connection");
        }
    }
}
