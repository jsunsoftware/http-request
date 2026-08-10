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
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the request-body charset contract: a charset explicitly declared on the request's
 * {@code Content-Type} header is authoritative for body encoding — the bytes on the wire must
 * match what the header advertises. The configured body charset
 * ({@link WebTarget#setBodyCharset}, {@link HttpRequestBuilder#setDefaultBodyCharset}, default
 * UTF-8) applies only when the header declares no charset.
 */
class RequestBodyCharsetTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private static HttpRequest httpRequest() {
        return HttpRequestBuilder.create(new ClientBuilder().build()).build();
    }

    @Test
    void objectPayload_headerDeclaredCharset_encodesBodyWithDeclaredCharset() {
        // é in ISO-8859-1 is the single byte 0xE9; in UTF-8 it is 0xC3 0xA9. binaryEqualTo makes
        // the stub match only when the body was actually encoded with the declared charset.
        byte[] isoBody = "{\"value\":\"café\"}".getBytes(ISO_8859_1);
        wm.stubFor(post(urlEqualTo("/obj"))
                .withRequestBody(binaryEqualTo(isoBody))
                .willReturn(aResponse().withStatus(200)));

        ResponseHandler<?> rh = httpRequest()
                .target(wm.getRuntimeInfo().getHttpBaseUrl())
                .path("obj")
                .addContentType(ContentType.APPLICATION_JSON.withCharset(ISO_8859_1))
                .rawPost(new Payload("café"));

        assertEquals(200, rh.getCode(),
                "object payload must be encoded with the charset declared on the Content-Type header");
    }

    @Test
    void stringPayload_headerDeclaredCharset_encodesBodyWithDeclaredCharset() {
        byte[] isoBody = "café".getBytes(ISO_8859_1);
        wm.stubFor(post(urlEqualTo("/str"))
                .withRequestBody(binaryEqualTo(isoBody))
                .willReturn(aResponse().withStatus(200)));

        ResponseHandler<?> rh = httpRequest()
                .target(wm.getRuntimeInfo().getHttpBaseUrl())
                .path("str")
                .addContentType(ContentType.TEXT_PLAIN.withCharset(ISO_8859_1))
                .rawPost("café");

        assertEquals(200, rh.getCode(),
                "String payload must be encoded with the charset declared on the Content-Type header");
    }

    @Test
    void objectPayload_noCharsetOnHeader_configuredBodyCharsetApplies() {
        byte[] isoBody = "{\"value\":\"café\"}".getBytes(ISO_8859_1);
        wm.stubFor(post(urlEqualTo("/no-charset"))
                .withRequestBody(binaryEqualTo(isoBody))
                .willReturn(aResponse().withStatus(200)));

        ResponseHandler<?> rh = httpRequest()
                .target(wm.getRuntimeInfo().getHttpBaseUrl())
                .path("no-charset")
                .addHeader("Content-Type", "application/json")
                .setBodyCharset(ISO_8859_1)
                .rawPost(new Payload("café"));

        assertEquals(200, rh.getCode(),
                "configured body charset must apply when the Content-Type header declares no charset");
    }

    @Test
    void resolveBodyCharset_precedence_headerWins_fallsBackOnMissingOrUnparseable() {
        // WireMock itself fails on an unparseable charset value, so the malformed-header fallback
        // is pinned here at the resolution level instead of on the wire.
        BasicWebTarget target = (BasicWebTarget) httpRequest().target("http://example.com/");

        assertEquals(StandardCharsets.UTF_8, target.resolveBodyCharset(),
                "no Content-Type header → configured body charset (library default UTF-8)");

        target.updateHeader("Content-Type", "text/plain");
        assertEquals(StandardCharsets.UTF_8, target.resolveBodyCharset(),
                "Content-Type without charset → configured body charset");

        target.updateHeader("Content-Type", "text/plain; charset=ISO-8859-1");
        assertEquals(ISO_8859_1, target.resolveBodyCharset(),
                "charset declared on the Content-Type header must win");

        target.setBodyCharset(StandardCharsets.US_ASCII);
        assertEquals(ISO_8859_1, target.resolveBodyCharset(),
                "declared header charset must win even over an explicitly configured body charset");

        target.updateHeader("Content-Type", "text/plain; charset=not-a-real-charset");
        assertEquals(StandardCharsets.US_ASCII, target.resolveBodyCharset(),
                "unparseable charset must fall back to the configured body charset, not throw");
    }

    @Test
    void jsonConverter_headerDeclaredCharset_winsOverConfiguredCharset() throws IOException {
        RequestBodyConverter converter = RequestBodyConverters.jsonConverter(
                ObjectMapperInitializer.initJsonMapperIfNull(null, null));
        RequestBodyConverterContext context = new RequestBodyConverterContext(
                new Payload("café"),
                ContentType.APPLICATION_JSON.withCharset(ISO_8859_1),
                StandardCharsets.UTF_8);

        HttpEntity entity = converter.convert(context);
        byte[] bytes = entity.getContent().readAllBytes();

        assertEquals(ISO_8859_1, ContentType.parse(entity.getContentType()).getCharset(),
                "entity content type must carry the declared charset");
        assertEquals("{\"value\":\"café\"}", new String(bytes, ISO_8859_1),
                "entity bytes must be encoded with the declared charset");
    }

    @Test
    void xmlConverter_headerDeclaredCharset_winsOverConfiguredCharset() throws IOException {
        RequestBodyConverter converter = RequestBodyConverters.xmlConverter(
                ObjectMapperInitializer.initXmlMapperIfNull(null, null));
        RequestBodyConverterContext context = new RequestBodyConverterContext(
                new Payload("café"),
                ContentType.APPLICATION_XML.withCharset(ISO_8859_1),
                StandardCharsets.UTF_8);

        HttpEntity entity = converter.convert(context);
        byte[] bytes = entity.getContent().readAllBytes();

        assertEquals(ISO_8859_1, ContentType.parse(entity.getContentType()).getCharset(),
                "entity content type must carry the declared charset");
        assertTrue(new String(bytes, ISO_8859_1).contains("café"),
                "entity bytes must be encoded with the declared charset");
    }

    static class Payload {
        public final String value;

        Payload(String value) {
            this.value = value;
        }
    }
}
