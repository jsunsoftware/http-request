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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.apache.hc.core5.http.HttpStatus.SC_BAD_GATEWAY;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the 5.0.0-rc1 date-deserialization contract (MIGRATION.md, "Default date-format patterns
 * removed"): the default mapper uses Jackson 3's ISO-8601 {@code java.time} handling; the pre-5.0
 * implicit patterns ({@code dd/MM/yyyy} etc.) are no longer installed and must be registered
 * explicitly via {@link HttpRequestBuilder#addResponseDefaultDateDeserializationPattern}.
 */
class DateDeserializationDefaultsTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private static ResponseHandler<Event> fetch(HttpRequest httpRequest, String path) {
        return httpRequest.target(wm.getRuntimeInfo().getHttpBaseUrl()).path(path).get(Event.class);
    }

    @Test
    void defaultMapper_parsesIso8601Dates() {
        wm.stubFor(get(urlEqualTo("/iso"))
                .willReturn(okJson("{\"date\":\"2025-07-14\"}")));

        HttpRequest httpRequest = HttpRequestBuilder.create(new ClientBuilder().build()).build();

        assertEquals(LocalDate.of(2025, 7, 14), fetch(httpRequest, "iso").requiredGet().date);
    }

    @Test
    void defaultMapper_rejectsLegacyPattern_droppedIn5x() {
        wm.stubFor(get(urlEqualTo("/legacy"))
                .willReturn(okJson("{\"date\":\"14/07/2025\"}")));

        HttpRequest httpRequest = HttpRequestBuilder.create(new ClientBuilder().build()).build();

        ResponseHandler<Event> rh = fetch(httpRequest, "legacy");

        assertEquals(SC_BAD_GATEWAY, rh.getCode(),
                "pre-5.0 implicit dd/MM/yyyy pattern must no longer apply — deserialization fails as 502");
    }

    @Test
    void registeredPattern_restoresLegacyBehavior() {
        wm.stubFor(get(urlEqualTo("/configured"))
                .willReturn(okJson("{\"date\":\"14/07/2025\"}")));

        HttpRequest httpRequest = HttpRequestBuilder.create(new ClientBuilder().build())
                .addResponseDefaultDateDeserializationPattern(LocalDate.class, "dd/MM/yyyy")
                .build();

        assertEquals(LocalDate.of(2025, 7, 14), fetch(httpRequest, "configured").requiredGet().date);
    }

    static class Event {
        public LocalDate date;
    }
}
