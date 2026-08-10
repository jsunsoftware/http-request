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
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the {@link HttpRequestBuilder} reuse contract: mutating the builder after a first
 * {@link HttpRequestBuilder#build()} and building again is supported. Each {@code build()}
 * snapshots the state accumulated so far — later mutations must apply to subsequently built
 * instances only, never leak into previously built ones.
 */
class HttpRequestBuilderReuseTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
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

    private String uri(String path) {
        return wm.getRuntimeInfo().getHttpBaseUrl() + path;
    }

    @Test
    void addDefaultHeader_afterBuild_appliesToNextBuildOnly() {
        wm.stubFor(get(urlPathEqualTo("/h")).willReturn(aResponse().withStatus(200)));

        HttpRequestBuilder builder = HttpRequestBuilder.create(client);
        HttpRequest first = builder.build();
        HttpRequest second = builder.addDefaultHeader("X-After-Build", "yes").build();

        first.target(uri("/h")).rawGet();
        second.target(uri("/h")).rawGet();

        wm.verify(1, getRequestedFor(urlPathEqualTo("/h")).withoutHeader("X-After-Build"));
        wm.verify(1, getRequestedFor(urlPathEqualTo("/h")).withHeader("X-After-Build", equalTo("yes")));
    }

    @Test
    void addDefaultRequestParameter_afterBuild_appliesToNextBuildOnly() {
        wm.stubFor(get(urlPathEqualTo("/p")).willReturn(aResponse().withStatus(200)));

        HttpRequestBuilder builder = HttpRequestBuilder.create(client);
        HttpRequest first = builder.build();
        HttpRequest second = builder.addDefaultRequestParameter("p", "1").build();

        first.target(uri("/p")).rawGet();
        second.target(uri("/p")).rawGet();

        wm.verify(1, getRequestedFor(urlEqualTo("/p")));
        wm.verify(1, getRequestedFor(urlEqualTo("/p?p=1")));
    }

    @Test
    void addBodyReader_afterBuild_appliesToNextBuildOnly() {
        wm.stubFor(get(urlPathEqualTo("/r"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/plain")
                        .withBody("server-body")));

        HttpRequestBuilder builder = HttpRequestBuilder.create(client);
        HttpRequest first = builder.build();
        HttpRequest second = builder.addBodyReader(new ResponseBodyReader<String>() {
            @Override
            public boolean isReadable(ResponseBodyReadableContext context) {
                return context.getType() == String.class && context.hasEntity();
            }

            @Override
            public String read(ResponseBodyReaderContext<String> context) {
                return "CUSTOM";
            }
        }).build();

        assertEquals("server-body", first.target(uri("/r")).get(String.class).requiredGet(),
                "instance built before the reader was added must not see it");
        assertEquals("CUSTOM", second.target(uri("/r")).get(String.class).requiredGet(),
                "instance built after the reader was added must use it");
    }

    @Test
    void addRequestBodyConverter_afterBuild_appliesToNextBuild() {
        wm.stubFor(post(urlPathEqualTo("/c")).willReturn(aResponse().withStatus(200)));

        HttpRequestBuilder builder = HttpRequestBuilder.create(client);
        builder.build();
        HttpRequest second = builder.addRequestBodyConverter(new RequestBodyConverter() {
            @Override
            public boolean canConvert(RequestBodyConverterContext context) {
                return context.getBody() instanceof Marker;
            }

            @Override
            public HttpEntity convert(RequestBodyConverterContext context) {
                return new StringEntity("CONVERTED", StandardCharsets.UTF_8);
            }
        }).build();

        ResponseHandler<?> rh = second.target(uri("/c")).rawPost(new Marker());

        assertEquals(200, rh.getCode());
        wm.verify(1, postRequestedFor(urlPathEqualTo("/c")).withRequestBody(equalTo("CONVERTED")));
    }

    static class Marker {
    }
}
