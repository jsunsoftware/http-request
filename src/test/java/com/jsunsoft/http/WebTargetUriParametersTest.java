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
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins how parameters added via {@link WebTarget#addParameter} materialize:
 * <ul>
 *     <li>{@link WebTarget#getURI()} / {@link WebTarget#getURIString()} reflect the pending
 *         parameters (they are part of the target's request state);</li>
 *     <li>on the wire the parameters appear exactly once — reading {@code getURI()} or going
 *         through immutable fluent copies must not duplicate them;</li>
 *     <li>entity-less POST/PUT moves the parameters into an
 *         {@code application/x-www-form-urlencoded} body (the library's form-post mechanism);
 *         with an entity present they stay on the URI.</li>
 * </ul>
 */
class WebTargetUriParametersTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private org.apache.hc.client5.http.impl.classic.CloseableHttpClient client;
    private HttpRequest httpRequest;

    @BeforeEach
    void setUp() {
        client = new ClientBuilder().build();
        httpRequest = HttpRequestBuilder.create(client).build();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null) client.close();
    }

    private String uri(String path) {
        return wm.getRuntimeInfo().getHttpBaseUrl() + path;
    }

    @Test
    void getURI_includesAddedParameters() {
        WebTarget target = httpRequest.target(uri("/u")).addParameter("q", "v");

        assertTrue(target.getURI().toString().contains("q=v"),
                "getURI() must reflect parameters added via addParameter, got: " + target.getURI());
    }

    @Test
    void getURIString_matchesGetURI_withParameters() {
        WebTarget target = httpRequest.target(uri("/u")).addParameter("q", "v");

        assertEquals(target.getURI().toString(), target.getURIString());
    }

    @Test
    void wire_parametersSentExactlyOnce_evenAfterGetURI() {
        wm.stubFor(get(urlPathEqualTo("/once")).willReturn(aResponse().withStatus(200)));

        WebTarget target = httpRequest.target(uri("/once")).addParameter("q", "v");
        target.getURI();          // reading the URI must not double-materialize the parameters
        target.getURIString();
        target.rawGet();

        wm.verify(1, getRequestedFor(urlEqualTo("/once?q=v")));
    }

    @Test
    void immutableTarget_fluentChain_doesNotDuplicateParameters() {
        wm.stubFor(get(urlPathEqualTo("/imm/p")).willReturn(aResponse().withStatus(200)));

        WebTarget target = httpRequest.immutableTarget(uri("/imm"))
                .addParameter("q", "v")
                .addHeader("X-A", "1")
                .path("p");

        assertTrue(target.getURI().toString().contains("q=v"));
        target.rawGet();

        wm.verify(1, getRequestedFor(urlEqualTo("/imm/p?q=v")));
    }

    @Test
    void post_withOnlyParameters_sendsFormBody() {
        wm.stubFor(post(urlPathEqualTo("/form")).willReturn(aResponse().withStatus(200)));

        httpRequest.target(uri("/form")).addParameter("a", "b").rawPost();

        wm.verify(1, postRequestedFor(urlEqualTo("/form"))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withRequestBody(equalTo("a=b")));
    }

    @Test
    void post_withEntity_sendsParametersInUri() {
        wm.stubFor(post(urlPathEqualTo("/entity")).willReturn(aResponse().withStatus(200)));

        httpRequest.target(uri("/entity")).addParameter("a", "b")
                .rawPost(new StringEntity("payload", StandardCharsets.UTF_8));

        wm.verify(1, postRequestedFor(urlEqualTo("/entity?a=b"))
                .withRequestBody(equalTo("payload")));
    }
}
