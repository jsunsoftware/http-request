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
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.URI;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class HttpRequestProxyTest {
    //when
    private static final HttpRequest httpRequestToSimpleProxy = HttpRequestBuilder.create(
                    ClientBuilder.create()
                            .proxy(new HttpHost("localhost", 8090))
                            .addDefaultRequestConfigCustomizer(builder -> builder.setProtocolUpgradeEnabled(false))
                            .build()
            )
            .build();

    private static final HttpRequest httpRequestToProxyAuth = HttpRequestBuilder.create(
                    ClientBuilder.create()
                            .proxy("localhost", 8090)
                            .addDefaultRequestConfigCustomizer(builder -> builder.setProtocolUpgradeEnabled(false))
                            .build()
            )
            .basicAuth("username_admin", "secret_password").build();

    @RegisterExtension
    static WireMockExtension serviceMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(8089))
            .build();

    @RegisterExtension
    static WireMockExtension proxyMock = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(8090))
            .build();

    @Test
    void simpleProxyTest() {
        //given
        proxyMock.stubFor(get(urlMatching(".*"))
                .willReturn(aResponse().proxiedFrom("http://localhost:8089")));

        serviceMock.stubFor(get(urlEqualTo("/private"))
                .willReturn(aResponse().withStatus(200)));

        //then
        assertEquals(200, httpRequestToSimpleProxy.target("http://localhost:8089/private").get(Void.class).getCode());
        proxyMock.verify(getRequestedFor(urlEqualTo("/private")));
        serviceMock.verify(getRequestedFor(urlEqualTo("/private")));
    }

    @Test
    void proxyUriWithUserinfoIsRejectedWithActionableMessage() {
        // URI userinfo (http://user:pass@proxy) was previously stripped silently by the Apache
        // HttpHost constructor — users who think they're configuring proxy auth would see no
        // credentials sent. We now reject this loudly and point at the proper Apache HC5 path
        // (BasicCredentialsProvider on HttpClientContext).
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                ClientBuilder.create().proxy(URI.create("http://user:secret@proxy.corp:8080")));
        assertTrue(ex.getMessage().contains("userinfo"),
                "Error message should name the cause: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("CredentialsProvider"),
                "Error message should point at the fix: " + ex.getMessage());
    }

    @Test
    void proxyUriWithoutUserinfoIsAccepted() {
        // Sanity check: clean URIs (no userinfo) still flow through proxy(URI) unchanged.
        ClientBuilder.create().proxy(URI.create("http://proxy.corp:8080"));
        ClientBuilder.create().proxy(URI.create("https://proxy.corp")); // default-port resolves via scheme
    }

    @Test
    void authorizationTest() {
        //given
        proxyMock.stubFor(get(urlMatching("/private"))
                .willReturn(aResponse().proxiedFrom("http://localhost:8089")));
        serviceMock.stubFor(get(urlEqualTo("/private"))
                .willReturn(aResponse().withStatus(200)));

        //then
        assertEquals(200, httpRequestToProxyAuth.target("http://localhost:8089/private").get(Void.class).getCode());
        proxyMock.verify(getRequestedFor(urlEqualTo("/private")).withHeader("Authorization", containing("Basic")));
        serviceMock.verify(getRequestedFor(urlEqualTo("/private")));
    }
}
