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
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.apache.hc.core5.http.ContentType.APPLICATION_XML;
import static org.apache.hc.core5.http.HttpHeaders.ACCEPT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpRequestSSLTest {
    private final HttpRequest httpRequest = HttpRequestBuilder.create(ClientBuilder.create()
            .trustAllCertificates()
            .trustAllHosts()
            .addDefaultHeader(ACCEPT, APPLICATION_XML.toString())
            .build()
    ).build();

    @RegisterExtension
    static WireMockExtension httpsServer = WireMockExtension.newInstance()
            .options(
                    WireMockConfiguration.wireMockConfig()
                            .dynamicPort()
                            .dynamicHttpsPort()
            )
            .build();

    @Test
    void ignoreSSLAndHostsTest() {
        httpsServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse().withStatus(200)));

        assertEquals(HttpStatus.SC_OK, httpRequest.target(httpsServer.getRuntimeInfo().getHttpsBaseUrl()).rawGet().getCode());
    }

    @Test
    @Disabled
    void ignoreRealSSLAndHostsTest() {
        httpsServer.stubFor(get(urlEqualTo("/"))
                .willReturn(aResponse().withStatus(200)));

        assertEquals(HttpStatus.SC_OK, httpRequest.target(httpsServer.getRuntimeInfo().getHttpsBaseUrl()).rawGet().getCode());
    }
}
