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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.hc.core5.http.HttpHost;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;

public class HttpRequestProxyTest {
    //when
    private static final HttpRequest httpRequestToSimpleProxy = HttpRequestBuilder.create(ClientBuilder.create()
            .proxy(new HttpHost("localhost", 8090)).build()).build();

    private static final HttpRequest httpRequestToProxyAuth = HttpRequestBuilder.create(ClientBuilder.create().proxy("localhost", 8090).build())
            .basicAuth("username_admin", "secret_password").build();

    @Rule
    public WireMockRule serviceMock = new WireMockRule(8089);

    @Rule
    public WireMockRule proxyMock = new WireMockRule(8090);

    @Test
    public void simpleProxyTest() {
        //given
        proxyMock.stubFor(get(urlMatching(".*"))
                .willReturn(aResponse().proxiedFrom("http://localhost:8089/")));

        serviceMock.stubFor(get(urlEqualTo("/private"))
                .willReturn(aResponse().withStatus(200)));

        //then
        assertEquals(httpRequestToSimpleProxy.target("http://localhost:8089/private").get(Void.class).getStatusCode(), 200);
        proxyMock.verify(getRequestedFor(urlEqualTo("/private")));
        serviceMock.verify(getRequestedFor(urlEqualTo("/private")));
    }

    @Test
    public void authorizationTest() throws IOException {
        //given
        proxyMock.stubFor(get(urlMatching("/private"))
                .willReturn(aResponse().proxiedFrom("http://localhost:8089/")));
        serviceMock.stubFor(get(urlEqualTo("/private"))
                .willReturn(aResponse().withStatus(200)));

        //then
        assertEquals(httpRequestToProxyAuth.target("http://localhost:8089/private").get(Void.class).getStatusCode(), 200);
        proxyMock.verify(getRequestedFor(urlEqualTo("/private")).withHeader("Authorization", containing("Basic")));
        serviceMock.verify(getRequestedFor(urlEqualTo("/private")));
    }
}
