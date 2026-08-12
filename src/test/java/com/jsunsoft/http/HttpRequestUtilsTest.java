package com.jsunsoft.http;

import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.net.URIBuilder;
import org.junit.jupiter.api.Test;

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
    void appendPath_onEmptyBasePath_producesRootedPath() throws URISyntaxException {
        URIBuilder b = new URIBuilder("http://example.com");

        HttpRequestUtils.appendPath(b, "v1");

        assertEquals("/v1", b.getPath());
        assertEquals("http://example.com/v1", b.build().toString());
    }

    @Test
    void appendPath_onRootBase_doesNotDoubleTheSlash() throws URISyntaxException {
        // URIBuilder#appendPath is NOT a drop-in here: it yields "//v1" for this case.
        URIBuilder b = new URIBuilder("http://example.com/");

        HttpRequestUtils.appendPath(b, "v1");

        assertEquals("/v1", b.getPath());
    }

    @Test
    void appendPath_multiSegmentPath_isAppendedAsIs() throws URISyntaxException {
        URIBuilder b = new URIBuilder("http://example.com/api");

        HttpRequestUtils.appendPath(b, "a/b");

        assertEquals("/api/a/b", b.getPath());
    }

    @Test
    void appendPath_rawSegments_arePercentEncodedAtBuildTime() throws URISyntaxException {
        URIBuilder b = new URIBuilder("http://example.com/api");

        HttpRequestUtils.appendPath(b, "a b");
        HttpRequestUtils.appendPath(b, "héllo");

        assertEquals("http://example.com/api/a%20b/h%C3%A9llo", b.build().toString());
    }

    @Test
    void appendPath_trailingSlashOnAppendedPath_isPreserved() throws URISyntaxException {
        URIBuilder b = new URIBuilder("http://example.com/api");

        HttpRequestUtils.appendPath(b, "v1/");

        assertEquals("/api/v1/", b.getPath());
    }

    @Test
    void appendPath_emptyPath_addsTrailingSlashToNonEmptyBase() throws URISyntaxException {
        URIBuilder b = new URIBuilder("http://example.com/api");

        HttpRequestUtils.appendPath(b, "");

        assertEquals("/api/", b.getPath());
    }

    @Test
    void appendPath_preservesQueryString() throws URISyntaxException {
        URIBuilder b = new URIBuilder("http://example.com/api?x=1");

        HttpRequestUtils.appendPath(b, "v1");

        assertEquals("http://example.com/api/v1?x=1", b.build().toString());
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
            public InputStream getContent() {
                throw new UnsupportedOperationException("not needed");
            }

            @Override
            public void writeTo(java.io.OutputStream outStream) {
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
            public void close() {
                // no-op
            }
        };

        assertNull(HttpRequestUtils.getContentTypeFromHttpEntity(entity));
    }
}


