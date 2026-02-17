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
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
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
        HttpRequest httpRequest = HttpRequestBuilder.create(ClientBuilder.create().setConnectionRequestTimeout(5).build()).build();

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
        HttpRequest httpRequest = HttpRequestBuilder.create(ClientBuilder.create().setConnectionRequestTimeout(5).build()).build();

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
}
