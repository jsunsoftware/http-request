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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for the {@link ResponseHandler} fluent chain — {@code ifSuccess(...).otherwise(...)},
 * {@code filter(...).ifPassed(...).otherwise(...)}, and the supporting {@link OtherwiseSupport} /
 * {@link FilterSupport} helpers. Previously these helpers had no direct test coverage; the only
 * thing pinning their behavior was a single end-to-end assertion in the README example.
 */
class ResponseHandlerFluentChainTest {

    @RegisterExtension
    static WireMockExtension server = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().dynamicPort())
            .build();

    private CloseableHttpClient client;
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

    private String httpUri(String path) {
        return server.getRuntimeInfo().getHttpBaseUrl() + path;
    }

    @Test
    void ifSuccess_invokesConsumer_andSuppressesOtherwise() {
        server.stubFor(get(urlEqualTo("/ok"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        AtomicBoolean successHit = new AtomicBoolean();
        AtomicBoolean otherwiseHit = new AtomicBoolean();

        ResponseHandler<String> handler = httpRequest.target(httpUri("/ok")).get(String.class);
        handler.ifSuccess(h -> successHit.set(true))
                .otherwise(h -> otherwiseHit.set(true));

        assertTrue(successHit.get(), "ifSuccess consumer must run on a 2xx response");
        assertFalse(otherwiseHit.get(), "otherwise must be suppressed when ifSuccess fired");
    }

    @Test
    void ifSuccess_skipsConsumer_andRunsOtherwise_onNonSuccess() {
        server.stubFor(get(urlEqualTo("/error"))
                .willReturn(aResponse().withStatus(503)));

        AtomicBoolean successHit = new AtomicBoolean();
        AtomicInteger otherwiseHit = new AtomicInteger();

        ResponseHandler<String> handler = httpRequest.target(httpUri("/error")).get(String.class);
        handler.ifSuccess(h -> successHit.set(true))
                .otherwise(h -> otherwiseHit.incrementAndGet());

        assertFalse(successHit.get(), "ifSuccess consumer must NOT run on a non-2xx response");
        assertEquals(1, otherwiseHit.get(), "otherwise must run exactly once");
    }

    @Test
    void filter_ifPassed_invokesConsumer_andSuppressesOtherwise() {
        server.stubFor(get(urlEqualTo("/yes"))
                .willReturn(aResponse().withStatus(200).withBody("yes")));

        AtomicBoolean passedHit = new AtomicBoolean();
        AtomicBoolean otherwiseHit = new AtomicBoolean();

        ResponseHandler<String> handler = httpRequest.target(httpUri("/yes")).get(String.class);
        handler.filter(ResponseHandler::hasContent)
                .ifPassed(h -> passedHit.set(true))
                .otherwise(h -> otherwiseHit.set(true));

        assertTrue(passedHit.get(), "ifPassed must run when the filter predicate is true");
        assertFalse(otherwiseHit.get(), "otherwise must be suppressed when ifPassed fired");
    }

    @Test
    void filter_ifPassed_skipsConsumer_andRunsOtherwise_whenPredicateFails() {
        server.stubFor(get(urlEqualTo("/empty"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Length", "0")));

        AtomicBoolean passedHit = new AtomicBoolean();
        AtomicBoolean otherwiseHit = new AtomicBoolean();

        ResponseHandler<String> handler = httpRequest.target(httpUri("/empty")).get(String.class);
        handler.filter(ResponseHandler::hasContent)   // empty body → predicate is false
                .ifPassed(h -> passedHit.set(true))
                .otherwise(h -> otherwiseHit.set(true));

        assertFalse(passedHit.get(), "ifPassed must NOT run when filter predicate is false");
        assertTrue(otherwiseHit.get(), "otherwise must run when filter predicate is false");
    }

    @Test
    void filter_chainsRequireAllPredicatesToPass() {
        server.stubFor(get(urlEqualTo("/big"))
                .willReturn(aResponse().withStatus(200).withBody("body-content")));

        AtomicBoolean passedHit = new AtomicBoolean();
        AtomicBoolean otherwiseHit = new AtomicBoolean();

        ResponseHandler<String> handler = httpRequest.target(httpUri("/big")).get(String.class);
        // Two predicates: hasContent passes, status==500 fails. AND-composition → ifPassed must NOT fire.
        handler.filter(ResponseHandler::hasContent)
                .filter(h -> h.getCode() == 500)
                .ifPassed(h -> passedHit.set(true))
                .otherwise(h -> otherwiseHit.set(true));

        assertFalse(passedHit.get(), "ifPassed must require ALL chained predicates to pass");
        assertTrue(otherwiseHit.get(), "otherwise must run when any chained predicate fails");
    }

    @Test
    void filter_chainsAllPassing_runIfPassed() {
        server.stubFor(get(urlEqualTo("/all"))
                .willReturn(aResponse().withStatus(200).withBody("body-content")));

        AtomicBoolean passedHit = new AtomicBoolean();
        AtomicBoolean otherwiseHit = new AtomicBoolean();

        ResponseHandler<String> handler = httpRequest.target(httpUri("/all")).get(String.class);
        handler.filter(ResponseHandler::isSuccess)
                .filter(ResponseHandler::hasContent)
                .filter(h -> h.getCode() == 200)
                .ifPassed(h -> passedHit.set(true))
                .otherwise(h -> otherwiseHit.set(true));

        assertTrue(passedHit.get(), "ifPassed must fire when every chained predicate passes");
        assertFalse(otherwiseHit.get(), "otherwise must be suppressed when ifPassed fired");
    }
}
