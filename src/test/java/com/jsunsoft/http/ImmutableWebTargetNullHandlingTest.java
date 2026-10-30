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
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.NameValuePair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins that {@code immutableTarget()} handles {@code null} arguments exactly like the mutable
 * {@code target()}: mutators reject {@code null}, and a {@code null} charset falls back to UTF-8.
 */
class ImmutableWebTargetNullHandlingTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private CloseableHttpClient client;
    private HttpRequest httpRequest;

    @BeforeEach
    void setUp() {
        client = ClientBuilder.create().build();
        httpRequest = HttpRequestBuilder.create(client).build();
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    private String uri(String path) {
        return wm.getRuntimeInfo().getHttpBaseUrl() + path;
    }

    @Test
    void nullArguments_areRejectedAsByTheMutableTarget() {
        Map<String, Consumer<WebTarget>> nullCalls = new LinkedHashMap<>();
        nullCalls.put("path", target -> target.path(null));
        nullCalls.put("setPath", target -> target.setPath(null));
        nullCalls.put("addHeader", target -> target.addHeader((Header) null));
        nullCalls.put("updateHeader", target -> target.updateHeader((Header) null));
        nullCalls.put("removeHeader", target -> target.removeHeader((Header) null));
        nullCalls.put("removeHeaders", target -> target.removeHeaders((String) null));
        nullCalls.put("addParameter", target -> target.addParameter((NameValuePair) null));

        nullCalls.forEach((name, call) -> {
            assertThrows(NullPointerException.class, () -> call.accept(httpRequest.target(uri("/"))),
                    "target()." + name + "(null)");
            assertThrows(NullPointerException.class, () -> call.accept(httpRequest.immutableTarget(uri("/"))),
                    "immutableTarget()." + name + "(null)");
        });
    }

    @Test
    void nullCharset_encodesFormBodyAsUtf8() {
        wm.stubFor(post(urlEqualTo("/form")).willReturn(ok()));

        httpRequest.immutableTarget(uri("/form")).setCharset(null).addParameter("q", "Ü").rawPost();

        wm.verify(postRequestedFor(urlEqualTo("/form")).withRequestBody(equalTo("q=%C3%9C")));
    }

    @Test
    void nullQueryCharset_encodesFormBodyAsUtf8() {
        wm.stubFor(post(urlEqualTo("/form")).willReturn(ok()));

        httpRequest.immutableTarget(uri("/form")).setQueryCharset(null).addParameter("q", "Ü").rawPost();

        wm.verify(postRequestedFor(urlEqualTo("/form")).withRequestBody(equalTo("q=%C3%9C")));
    }
}
