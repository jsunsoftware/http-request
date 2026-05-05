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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * §4.8: response charset behavior. Apache HC5's {@code EntityUtils.toString(entity)} (the form
 * used before this fix) silently falls back to ISO-8859-1 when the response Content-Type lacks
 * a {@code charset} parameter. The library now defaults that fallback to UTF-8, with an opt-in
 * override via {@link HttpRequestBuilder#setDefaultResponseCharset}.
 */
class DefaultResponseCharsetTest {

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
    void defaultIsUtf8_whenServerOmitsCharset() {
        // Server returns UTF-8-encoded bytes for "héllo" but does NOT advertise a charset on the
        // Content-Type — typical of bare `text/plain` or misconfigured servers. Without our
        // UTF-8 default, Apache HC5 would decode these bytes as ISO-8859-1 and produce mojibake.
        byte[] utf8Body = "héllo".getBytes(StandardCharsets.UTF_8);
        server.stubFor(get(urlEqualTo("/no-charset"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/plain").withBody(utf8Body)));

        HttpRequest httpRequest = HttpRequestBuilder.create(client).build(); // default charset = UTF-8
        String result = httpRequest.target(httpUri("/no-charset")).get(String.class).orElseThrow();
        assertEquals("héllo", result);
    }

    @Test
    void serverSuppliedCharsetTakesPrecedenceOverDefault() {
        // When the server DOES advertise a charset, that wins regardless of our default. We
        // configure the default to a "wrong" charset to prove this — the server says UTF-8 and
        // the bytes ARE UTF-8, so the result should still be correct.
        byte[] utf8Body = "héllo".getBytes(StandardCharsets.UTF_8);
        server.stubFor(get(urlEqualTo("/utf8"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/plain; charset=utf-8").withBody(utf8Body)));

        HttpRequest httpRequest = HttpRequestBuilder.create(client)
                .setDefaultResponseCharset(StandardCharsets.ISO_8859_1) // intentionally wrong default
                .build();
        String result = httpRequest.target(httpUri("/utf8")).get(String.class).orElseThrow();
        assertEquals("héllo", result, "Server-supplied charset must win over the configured default");
    }

    @Test
    void overriddenDefaultIsHonored_whenServerOmitsCharset() {
        // Legacy server that emits ISO-8859-1 bytes without advertising a charset. The user
        // overrides the library default to ISO-8859-1; decoding now produces the right string.
        byte[] latin1Body = "héllo".getBytes(StandardCharsets.ISO_8859_1);
        server.stubFor(get(urlEqualTo("/legacy"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "text/plain").withBody(latin1Body)));

        HttpRequest httpRequest = HttpRequestBuilder.create(client)
                .setDefaultResponseCharset(StandardCharsets.ISO_8859_1)
                .build();
        String result = httpRequest.target(httpUri("/legacy")).get(String.class).orElseThrow();
        assertEquals("héllo", result);
    }

    @Test
    void setDefaultResponseCharsetRejectsNull() {
        HttpRequestBuilder builder = HttpRequestBuilder.create(client);
        assertThrows(NullPointerException.class, () -> builder.setDefaultResponseCharset((Charset) null));
    }
}
