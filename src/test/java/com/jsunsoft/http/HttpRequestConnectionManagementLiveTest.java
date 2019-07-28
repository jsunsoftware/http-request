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

package com.jsunsoft.http;

import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpRequestConnectionManagementLiveTest {
    private static final String SERVER1 = "https://www.google.com/";
    private static final String SERVER7 = "http://www.youtube.com/";

    @Test
    public final void whenConnectionsNeededGreaterThanMaxTotal_thenReuseConnections() throws InterruptedException {
        HttpRequest httpRequest = HttpRequestBuilder.create(ClientBuilder.create().connectionRequestTimeout(5).build()).build();

        int validThreadSize = 128;
        final HttpRequestThread[] threads = new HttpRequestThread[validThreadSize];

        for (int i = 0; i < threads.length; i++) {
            threads[i] = new HttpRequestThread(httpRequest.target(SERVER1));
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
    public final void whenTwoConnectionsForTwoRequests_thenNoExceptions() throws InterruptedException {
        HttpRequest httpRequest1 = HttpRequestBuilder.create(ClientBuilder.create().build()).build();
        HttpRequest httpRequest2 = HttpRequestBuilder.create(ClientBuilder.create().build()).build();

        HttpRequestThread thread1 = new HttpRequestThread(httpRequest1.target(SERVER1));
        HttpRequestThread thread2 = new HttpRequestThread(httpRequest2.target(SERVER7));

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
        assertTrue(thread1.getResponseHandler().getConnectionFailureType().isNotFailed());
        assertTrue(thread2.getResponseHandler().getConnectionFailureType().isNotFailed());
    }

    @Test
    public final void whenPollingConnectionManagerIsConfiguredOnHttpClient_thenNoExceptions() {
        CloseableHttpClient closeableHttpClient = ClientBuilder.create().build();
        BasicHttpRequest httpRequest = (BasicHttpRequest) HttpRequestBuilder.create(closeableHttpClient).build();
        httpRequest.target(SERVER1).get();
//        Assert.assertEquals(0, ((PoolingHttpClientConnectionManager) closeableHttpClient.getConnectionManager()).getTotalStats().getLeased());
    }

    @Test
    public final void whenThreeConnectionsForThreeRequests_thenConnectionsAreNotLeased() throws InterruptedException {
        CloseableHttpClient closeableHttpClient = ClientBuilder.create().build();

        HttpRequest httpRequest1 = HttpRequestBuilder.create(closeableHttpClient).build();
        final HttpRequestThread thread1 = new HttpRequestThread(httpRequest1.target(SERVER1));
        final HttpRequestThread thread2 = new HttpRequestThread(httpRequest1.target(SERVER7));
        final HttpRequestThread thread3 = new HttpRequestThread(httpRequest1.target("http://www.google.com/"));
        thread1.start();
        thread2.start();
        thread3.start();
        thread1.join();
        thread2.join(1000);
        thread3.join();
//        Assert.assertEquals(0, ((PoolingHttpClientConnectionManager) closeableHttpClient.getConnectionManager()).getTotalStats().getLeased());
    }
}
