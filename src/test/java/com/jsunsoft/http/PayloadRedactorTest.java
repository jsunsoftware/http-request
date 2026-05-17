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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Payload redactors mask sensitive content (passwords, tokens, …) before the request body is
 * logged via {@link HttpRequestBuilder#enableRequestPayloadLogging()}. Asserting on log output
 * directly is fragile, so we instead assert on the redactor's <em>input</em> via a
 * capture-and-no-op redactor — which proves the redactor is invoked when logging is enabled.
 */
class PayloadRedactorTest {

    @RegisterExtension
    static WireMockExtension server = WireMockExtension.newInstance()
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

    private String httpUri(String path) {
        return server.getRuntimeInfo().getHttpBaseUrl() + path;
    }

    @Test
    void redactor_isInvokedWithRequestPayload_whenLoggingEnabled() {
        server.stubFor(post(urlEqualTo("/login"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        AtomicReference<String> seen = new AtomicReference<>();
        UnaryOperator<String> capturingRedactor = s -> {
            seen.set(s);
            // mask the password
            return s.replaceAll("\"password\"\\s*:\\s*\"[^\"]+\"", "\"password\":\"***\"");
        };

        HttpRequest httpRequest = HttpRequestBuilder.create(client)
                .enableRequestPayloadLogging()
                .addPayloadRedactor(capturingRedactor)
                .build();

        String body = """
                {"user":"alice","password":"hunter2"}""";
        httpRequest.target(httpUri("/login")).rawRequest(HttpMethod.POST, body);

        assertEquals(body, seen.get(), "Redactor must receive the original payload as input");
    }

    @Test
    void redactor_chainComposesLeftToRight() {
        server.stubFor(post(urlEqualTo("/chain"))
                .willReturn(aResponse().withStatus(200)));

        AtomicReference<String> finalSeen = new AtomicReference<>();
        UnaryOperator<String> first = s -> s.replace("alpha", "BETA");
        UnaryOperator<String> second = s -> {
            finalSeen.set(s);              // capture what the second sees
            return s.replace("BETA", "GAMMA");
        };

        HttpRequest httpRequest = HttpRequestBuilder.create(client)
                .enableRequestPayloadLogging()
                .addPayloadRedactor(first)
                .addPayloadRedactor(second)
                .build();

        httpRequest.target(httpUri("/chain")).rawRequest(HttpMethod.POST, "alpha-token");

        // The second redactor sees the OUTPUT of the first — i.e. "BETA-token".
        assertEquals("BETA-token", finalSeen.get(),
                "Redactors must compose left-to-right (each sees the previous one's output)");
    }

    @Test
    void redactor_isNotInvoked_whenLoggingDisabled() {
        server.stubFor(post(urlEqualTo("/no-log"))
                .willReturn(aResponse().withStatus(200)));

        AtomicReference<String> seen = new AtomicReference<>();
        // Logging is off — redactor must never be invoked.
        HttpRequest httpRequest = HttpRequestBuilder.create(client)
                .addPayloadRedactor(s -> {
                    seen.set(s);
                    return s;
                })
                .build();

        httpRequest.target(httpUri("/no-log")).rawRequest(HttpMethod.POST, "{\"x\":1}");

        assertNotNull(seen);
        // seen.get() must remain null.
        if (seen.get() != null) {
            throw new AssertionError("Redactor invoked despite logging being off — got: " + seen.get());
        }
    }

    @Test
    void addPayloadRedactor_rejectsNull() {
        HttpRequestBuilder builder = HttpRequestBuilder.create(client);
        assertThrows(NullPointerException.class, () -> builder.addPayloadRedactor(null));
    }

    @Test
    void redactor_throwingRedactorDoesNotKillTheRequest() {
        // A buggy redactor must NOT take down the in-flight request — the library catches and
        // swaps in a placeholder, then the request proceeds normally.
        server.stubFor(post(urlEqualTo("/buggy"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        HttpRequest httpRequest = HttpRequestBuilder.create(client)
                .enableRequestPayloadLogging()
                .addPayloadRedactor(s -> {
                    throw new RuntimeException("redactor blew up");
                })
                .build();

        // The request itself succeeds.
        int code = httpRequest.target(httpUri("/buggy"))
                .rawRequest(HttpMethod.POST, "{\"x\":1}")
                .getCode();
        assertEquals(200, code);
    }
}
