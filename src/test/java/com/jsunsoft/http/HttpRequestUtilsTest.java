package com.jsunsoft.http;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.net.URIBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpRequestUtilsTest {

    @Test
    void appendPath_shouldNotCreateDoubleSlash_whenCurrentHasNoTrailingSlash_andPathStartsWithSlash() throws URISyntaxException {
        URIBuilder b = new URIBuilder("http://example.com/api");

        HttpRequestUtils.appendPath(b, "/v1");

        assertEquals("/api/v1", b.getPath());
    }

    @Test
    void appendPath_shouldNotCreateDoubleSlash_whenCurrentHasTrailingSlash_andPathHasNoLeadingSlash() throws URISyntaxException {
        URIBuilder b = new URIBuilder("http://example.com/api/");

        HttpRequestUtils.appendPath(b, "v1");

        assertEquals("/api/v1", b.getPath());
    }

    @Test
    void appendPath_shouldNotCreateDoubleSlash_whenCurrentHasTrailingSlash_andPathHasLeadingSlash() throws URISyntaxException {
        URIBuilder b = new URIBuilder("http://example.com/api/");

        HttpRequestUtils.appendPath(b, "/v1");

        assertEquals("/api/v1", b.getPath());
    }

    @Test
    void getContentTypeFromHttpEntity_shouldReturnNull_whenHeaderIsMissing() {
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
                return null;
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

        assertNull(HttpRequestUtils.getContentTypeFromHttpEntity(entity));
    }
}


