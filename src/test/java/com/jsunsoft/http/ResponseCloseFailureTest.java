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

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.BasicHttpEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code Response.close()} failure happens after the payload (or error text) is already safely
 * in memory — it must be logged, never override the committed {@link ResponseHandler} result.
 * The synthetic target below serves a canned response whose {@code close()} throws, exercising
 * the eager-API result handling without a network.
 */
class ResponseCloseFailureTest {

    private static final URI TEST_URI = URI.create("http://localhost/close-failure");

    private final ResponseBodyReaderConfig readerConfig = ResponseBodyReaderConfig.create().build();

    private CloseableHttpClient client;

    @BeforeEach
    void setUp() {
        client = new ClientBuilder().build();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null) client.close();
    }

    @Test
    void closeFailure_afterSuccessfulRead_keepsCommittedResult() {
        WebTarget target = closeThrowingTarget(200, "hello");

        ResponseHandler<String> rh = target.get(String.class);

        assertEquals(200, rh.getCode(), "a close() failure must not override the committed 200 result");
        assertEquals("hello", rh.requiredGet(), "successfully-read content must not be discarded over a close() failure");
    }

    @Test
    void closeFailure_afterNonSuccessRead_keepsSpecificFailureResult() {
        WebTarget target = closeThrowingTarget(404, "not-found-details");

        ResponseHandler<String> rh = target.get(String.class);

        assertEquals(404, rh.getCode(), "a close() failure must not mask the server's own failure code");
        assertTrue(rh.getErrorText().contains("not-found-details"),
                "the already-read error text must survive a close() failure, got: " + rh.getErrorText());
    }

    private WebTarget closeThrowingTarget(int statusCode, String body) {
        return new BasicWebTarget(client, TEST_URI, List.of(), List.of(),
                readerConfig, RequestBodySerializeConfig.create().build(),
                false, null, null, null) {
            @Override
            public Response request(HttpMethod method, HttpContext context) {
                ClassicHttpResponse raw = new BasicClassicHttpResponse(statusCode);
                raw.setEntity(new BasicHttpEntity(
                        new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)),
                        ContentType.TEXT_PLAIN.withCharset(StandardCharsets.UTF_8)));
                return new BasicResponse(raw, readerConfig, TEST_URI) {
                    @Override
                    public void close() throws IOException {
                        super.close();
                        throw new IOException("simulated close failure");
                    }
                };
            }
        };
    }
}
