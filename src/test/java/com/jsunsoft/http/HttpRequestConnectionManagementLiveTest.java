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

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class HttpRequestConnectionManagementLiveTest {
    private static final String SERVER1 = "https://www.jsunsoft.com/";
    private static final String SERVER7 = "http://www.youtube.com/";

    @Test
    public final void whenConnectionsNeededGreaterThanMaxTotal_thenReuseConnections() throws InterruptedException {
        HttpRequest<?> httpRequest = HttpRequestBuilder.createGet(SERVER1)
                .connectionConfig(
                        ConnectionConfig.create().connectionRequestTimeout(5)
                ).build();
        int validThreadSize = 128;
        final HttpRequestThread[] threads = new HttpRequestThread[validThreadSize];

        for (int i = 0; i < threads.length; i++) {
            threads[i] = new HttpRequestThread<>(httpRequest);
        }

        for (HttpRequestThread<?> thread : threads) {
            thread.start();
        }

        for (HttpRequestThread<?> thread : threads) {
            thread.join();
            assertTrue(!thread.getResponseHandler().getConnectionFailureType().isConnectionPoolEmpty());
        }
    }

    @Test
    public final void whenTwoConnectionsForTwoRequests_thenNoExceptions() throws InterruptedException {
        HttpRequest<?> httpRequest1 = HttpRequestBuilder.createGet(SERVER1).build();
        HttpRequest<?> httpRequest2 = HttpRequestBuilder.createGet(SERVER7).build();

        HttpRequestThread thread1 = new HttpRequestThread<>(httpRequest1);
        HttpRequestThread thread2 = new HttpRequestThread<>(httpRequest2);

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
        assertTrue(thread1.getResponseHandler().getConnectionFailureType().isNotFailed());
        assertTrue(thread2.getResponseHandler().getConnectionFailureType().isNotFailed());
    }

    @Test
    public final void whenPollingConnectionManagerIsConfiguredOnHttpClient_thenNoExceptions() {
        BasicHttpRequest<?> httpRequest = (BasicHttpRequest<?>) HttpRequestBuilder.createGet(SERVER1).build();
        httpRequest.execute();
        Assert.assertEquals(0, httpRequest.getConnectionManager().getTotalStats().getLeased());
    }

    @Test
    public final void whenThreeConnectionsForThreeRequests_thenConnectionsAreNotLeased() throws InterruptedException {
        BasicHttpRequest<?> httpRequest1 = (BasicHttpRequest<?>) HttpRequestBuilder.createGet(SERVER1).build();
        final HttpRequestThread thread1 = new HttpRequestThread<>(httpRequest1);
        final HttpRequestThread thread2 = new HttpRequestThread<>(httpRequest1.changeUri(SERVER7));
        final HttpRequestThread thread3 = new HttpRequestThread<>(httpRequest1.changeUri("http://www.google.com/"));
        thread1.start();
        thread2.start();
        thread3.start();
        thread1.join();
        thread2.join(1000);
        thread3.join();
        Assert.assertEquals(0, httpRequest1.getConnectionManager().getTotalStats().getLeased());
    }
}
