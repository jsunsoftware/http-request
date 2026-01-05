package com.jsunsoft.http;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that intentionally demonstrate "sharp edges"/pitfalls in the current API behavior.
 * These tests pass by asserting the current behavior, but they highlight areas for improvement.
 */
class RequestParsingTest {

    @Test
    void getContentTypeFromHttpEntity_acceptsGarbageContentTypeHeaderValue() {
        HttpEntity entity = new HttpEntity() {
            @Override
            public boolean isRepeatable() {
                return true;
            }

            @Override
            public boolean isChunked() {
                return false;
            }

            @Override
            public long getContentLength() {
                return 0;
            }

            @Override
            public String getContentType() {
                // Intentionally invalid header value (but ContentType.parse(...) is permissive and accepts it).
                return "this is not a valid content-type";
            }

            @Override
            public String getContentEncoding() {
                return null;
            }

            @Override
            public Set<String> getTrailerNames() {
                return Collections.emptySet();
            }

            @Override
            public InputStream getContent() throws IOException {
                throw new UnsupportedOperationException("not needed");
            }

            @Override
            public void writeTo(java.io.OutputStream outStream) throws IOException {
                throw new UnsupportedOperationException("not needed");
            }

            @Override
            public boolean isStreaming() {
                return false;
            }

            @Override
            public Supplier<List<? extends Header>> getTrailers() {
                return Collections::emptyList;
            }

            @Override
            public void close() throws IOException {
                // no-op
            }
        };

        // Pitfall: garbage is accepted and treated as a "valid" mime type.
        ContentType parsed = HttpRequestUtils.getContentTypeFromHttpEntity(entity);
        assertNotNull(parsed);
        assertEquals("this is not a valid content-type", parsed.getMimeType());
    }

    @Test
    void requestBodySerialization_throws_whenRequestContentTypeHeaderIsInvalid() {
        HttpRequest httpRequest = HttpRequestBuilder.create(new ClientBuilder().build()).build();

        // Note: serialization happens before any network I/O; an invalid Content-Type header fails early.
        WebTarget target = httpRequest
                .target("http://localhost:1/does-not-matter")
                .addHeader(new BasicHeader(CONTENT_TYPE, "not a real content-type"));

        // ContentType.parse(...) is permissive, but since it's not JSON/XML the library can't pick a serializer.
        assertThrows(RequestException.class, () -> target.rawRequest(HttpMethod.POST, new Object()));
    }
}


