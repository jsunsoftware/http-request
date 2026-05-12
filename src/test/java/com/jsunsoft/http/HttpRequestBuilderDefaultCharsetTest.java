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
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the {@link HttpRequestBuilder#setDefaultQueryCharset(java.nio.charset.Charset)} /
 * {@link HttpRequestBuilder#setDefaultBodyCharset(java.nio.charset.Charset)} contract introduced
 * in 3.5.0:
 * <ul>
 *     <li>defaults propagate to every {@link WebTarget} produced by {@link HttpRequest#target},
 *         {@link HttpRequest#immutableTarget}, and {@link HttpRequest#retryableTarget};</li>
 *     <li>per-target {@link WebTarget#setQueryCharset(java.nio.charset.Charset)} /
 *         {@link WebTarget#setBodyCharset(java.nio.charset.Charset)} still overrides;</li>
 *     <li>{@code null} at the builder level restores the library default of UTF-8 — the two
 *         axes are independent (setting one doesn't move the other).</li>
 * </ul>
 * Verifies state via the package-private accessors {@code BasicWebTarget#getUriBuilder()} (whose
 * {@code URIBuilder} carries the effective query charset) and {@code BasicWebTarget#getBodyCharset()}.
 */
class HttpRequestBuilderDefaultCharsetTest {

    private static CloseableHttpClient client() {
        return new ClientBuilder().build();
    }

    @Test
    void noBuilderDefaults_freshTargetsUseUtf8() {
        HttpRequest req = HttpRequestBuilder.create(client()).build();
        BasicWebTarget t = (BasicWebTarget) req.target("http://example.com/");

        assertEquals(StandardCharsets.UTF_8, t.getUriBuilder().getCharset());
        assertEquals(StandardCharsets.UTF_8, t.getBodyCharset());
    }

    @Test
    void setDefaultQueryCharset_appliesToFreshTarget_andLeavesBodyAlone() {
        HttpRequest req = HttpRequestBuilder.create(client())
                .setDefaultQueryCharset(StandardCharsets.ISO_8859_1)
                .build();
        BasicWebTarget t = (BasicWebTarget) req.target("http://example.com/");

        assertEquals(StandardCharsets.ISO_8859_1, t.getUriBuilder().getCharset());
        assertEquals(StandardCharsets.UTF_8, t.getBodyCharset(),
                "body charset must not be affected by setDefaultQueryCharset");
    }

    @Test
    void setDefaultBodyCharset_appliesToFreshTarget_andLeavesQueryAlone() {
        HttpRequest req = HttpRequestBuilder.create(client())
                .setDefaultBodyCharset(StandardCharsets.ISO_8859_1)
                .build();
        BasicWebTarget t = (BasicWebTarget) req.target("http://example.com/");

        assertEquals(StandardCharsets.UTF_8, t.getUriBuilder().getCharset(),
                "query charset must not be affected by setDefaultBodyCharset");
        assertEquals(StandardCharsets.ISO_8859_1, t.getBodyCharset());
    }

    @Test
    void perTargetOverride_winsOverBuilderDefault() {
        HttpRequest req = HttpRequestBuilder.create(client())
                .setDefaultQueryCharset(StandardCharsets.ISO_8859_1)
                .setDefaultBodyCharset(StandardCharsets.ISO_8859_1)
                .build();

        BasicWebTarget t = (BasicWebTarget) req.target("http://example.com/")
                .setQueryCharset(StandardCharsets.US_ASCII)
                .setBodyCharset(StandardCharsets.US_ASCII);

        assertEquals(StandardCharsets.US_ASCII, t.getUriBuilder().getCharset());
        assertEquals(StandardCharsets.US_ASCII, t.getBodyCharset());
    }

    @Test
    void builderDefaults_propagateToImmutableTarget() {
        HttpRequest req = HttpRequestBuilder.create(client())
                .setDefaultQueryCharset(StandardCharsets.ISO_8859_1)
                .setDefaultBodyCharset(StandardCharsets.ISO_8859_1)
                .build();

        BasicWebTarget t = (BasicWebTarget) req.immutableTarget("http://example.com/");

        assertEquals(StandardCharsets.ISO_8859_1, t.getUriBuilder().getCharset());
        assertEquals(StandardCharsets.ISO_8859_1, t.getBodyCharset());
    }

    @Test
    void builderDefaults_propagateToRetryableTarget() {
        HttpRequest req = HttpRequestBuilder.create(client())
                .setDefaultQueryCharset(StandardCharsets.ISO_8859_1)
                .setDefaultBodyCharset(StandardCharsets.ISO_8859_1)
                .build();
        RetryContext retry = RetryContext.onIdempotent5xx(1, Duration.ofMillis(1));

        BasicWebTarget t = (BasicWebTarget) req.retryableTarget("http://example.com/", retry);

        assertEquals(StandardCharsets.ISO_8859_1, t.getUriBuilder().getCharset());
        assertEquals(StandardCharsets.ISO_8859_1, t.getBodyCharset());
    }

    @Test
    void nullAtBuilderLevel_restoresLibraryUtf8_eachAxisIndependent() {
        // Setting ISO then null clears the builder-level default; the produced target falls back
        // to the library default UTF-8. The two axes are tracked independently — clearing one
        // doesn't touch the other.
        HttpRequest req = HttpRequestBuilder.create(client())
                .setDefaultQueryCharset(StandardCharsets.ISO_8859_1)
                .setDefaultBodyCharset(StandardCharsets.ISO_8859_1)
                .setDefaultQueryCharset(null)            // reset only the query axis
                .build();
        BasicWebTarget t = (BasicWebTarget) req.target("http://example.com/");

        assertEquals(StandardCharsets.UTF_8, t.getUriBuilder().getCharset(),
                "query reset to null should restore UTF-8");
        assertEquals(StandardCharsets.ISO_8859_1, t.getBodyCharset(),
                "body axis untouched by query-axis reset");
    }

    @Test
    void independentBuilders_doNotShareCharsetState() {
        // Pin: each build() snapshots the builder's *current* charset state. A second build()
        // from the same builder after a charset reset must not leak the older value.
        HttpRequestBuilder builder = HttpRequestBuilder.create(client())
                .setDefaultQueryCharset(StandardCharsets.ISO_8859_1);
        HttpRequest reqA = builder.build();
        builder.setDefaultQueryCharset(StandardCharsets.US_ASCII);
        HttpRequest reqB = builder.build();

        BasicWebTarget tA = (BasicWebTarget) reqA.target("http://example.com/");
        BasicWebTarget tB = (BasicWebTarget) reqB.target("http://example.com/");

        assertEquals(StandardCharsets.ISO_8859_1, tA.getUriBuilder().getCharset(),
                "reqA still sees its snapshot");
        assertEquals(StandardCharsets.US_ASCII, tB.getUriBuilder().getCharset(),
                "reqB sees the updated value");
    }
}
