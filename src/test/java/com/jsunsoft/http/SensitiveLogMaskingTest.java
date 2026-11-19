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

import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.message.HeaderGroup;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code toString()} representations end up in log files (the library's own TRACE lines log the
 * request builder and the executed handler; user code logs handlers all the time) — they must
 * never dump credentials or payloads. These pins guard the masking against regressions.
 */
class SensitiveLogMaskingTest {

    @Test
    void httpUriRequestBuilder_toString_masksCredentialHeaders() {
        HttpUriRequestBuilder builder = new HttpUriRequestBuilder()
                .addHeader("Authorization", "Basic dXNlcjpwYXNz")
                .addHeader("Proxy-Authorization", "Bearer proxy-secret-token")
                .addHeader("Cookie", "session=deadbeef")
                .addHeader("X-Trace-Id", "trace-42");

        String rendered = builder.toString();

        assertFalse(rendered.contains("dXNlcjpwYXNz"), "Authorization value must be masked, got: " + rendered);
        assertFalse(rendered.contains("proxy-secret-token"), "Proxy-Authorization value must be masked");
        assertFalse(rendered.contains("deadbeef"), "Cookie value must be masked");
        assertTrue(rendered.contains("Authorization"), "the header NAME should remain visible for diagnostics");
        assertTrue(rendered.contains("trace-42"), "non-sensitive header values must remain visible");
    }

    @Test
    void responseHandler_toString_doesNotDumpContentOrHeaderValues() {
        HeaderGroup headers = new HeaderGroup();
        headers.addHeader(new BasicHeader("Set-Cookie", "session=top-secret-cookie"));

        BasicResponseHandler<String> handler = new BasicResponseHandler<>(
                "TOP-SECRET-BODY", 200, 200, headers, null, null, String.class, null,
                URI.create("http://localhost/masking"), System.nanoTime());

        String rendered = handler.toString();

        assertFalse(rendered.contains("TOP-SECRET-BODY"),
                "handler toString must not dump the deserialized content, got: " + rendered);
        assertFalse(rendered.contains("top-secret-cookie"),
                "handler toString must not dump response header values (Set-Cookie!)");
        assertTrue(rendered.contains("200"), "status stays visible");
        assertTrue(rendered.contains("http://localhost/masking"), "uri stays visible");
    }
}
