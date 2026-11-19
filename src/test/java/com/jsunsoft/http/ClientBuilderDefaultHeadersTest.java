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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the {@link ClientBuilder} header and redirect contract: with the default redirect strategy,
 * per-request credentials never follow a redirect to a different origin, while same-origin
 * redirects keep them; default headers added after {@code build()} never reach clients already
 * built.
 */
class ClientBuilderDefaultHeadersTest {

    @RegisterExtension
    static WireMockExtension origin = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension otherOrigin = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    @BeforeEach
    void stubRedirects() {
        otherOrigin.stubFor(get(urlEqualTo("/landing")).willReturn(ok("other origin")));
        origin.stubFor(get(urlEqualTo("/landing")).willReturn(ok("same origin")));
        origin.stubFor(get(urlEqualTo("/to-other-origin")).willReturn(aResponse().withStatus(302)
                .withHeader("Location", otherOrigin.getRuntimeInfo().getHttpBaseUrl() + "/landing")));
        origin.stubFor(get(urlEqualTo("/to-same-origin")).willReturn(aResponse().withStatus(302)
                .withHeader("Location", origin.getRuntimeInfo().getHttpBaseUrl() + "/landing")));
    }

    @Test
    void requestLevelBasicAuth_crossOriginRedirect_isNotFollowed() throws IOException {
        try (CloseableHttpClient client = ClientBuilder.create().enableDefaultRedirectStrategy().build()) {
            ResponseHandler<String> rh = HttpRequestBuilder.create(client).basicAuth("user", "secret").build()
                    .target(origin.url("/to-other-origin")).get(String.class);

            assertEquals(302, rh.getCode(), "the redirect must not be followed — the caller gets the 3xx");
        }
        otherOrigin.verify(0, getRequestedFor(urlEqualTo("/landing")));
    }

    @Test
    void requestLevelBasicAuth_sameOriginRedirect_isFollowedWithTheHeader() throws IOException {
        try (CloseableHttpClient client = ClientBuilder.create().enableDefaultRedirectStrategy().build()) {
            ResponseHandler<String> rh = HttpRequestBuilder.create(client).basicAuth("user", "secret").build()
                    .target(origin.url("/to-same-origin")).get(String.class);

            assertEquals(200, rh.getCode());
            assertEquals("same origin", rh.get());
        }
        origin.verify(1, getRequestedFor(urlEqualTo("/landing")).withHeader("Authorization", matching("Basic .+")));
    }

    @Test
    void addDefaultHeader_afterBuild_doesNotReachAlreadyBuiltClient() throws IOException {
        ClientBuilder builder = ClientBuilder.create().addDefaultHeader("X-App", "svc");

        try (CloseableHttpClient first = builder.build();
             CloseableHttpClient second = builder.addDefaultHeader("X-After-Build", "yes").build()) {
            HttpRequestBuilder.create(first).build().target(origin.url("/landing")).rawGet();
            HttpRequestBuilder.create(second).build().target(origin.url("/landing")).rawGet();
        }

        origin.verify(1, getRequestedFor(urlEqualTo("/landing")).withoutHeader("X-After-Build"));
        origin.verify(1, getRequestedFor(urlEqualTo("/landing")).withHeader("X-After-Build", equalTo("yes")));
    }
}
