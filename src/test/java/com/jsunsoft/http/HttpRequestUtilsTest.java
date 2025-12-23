/*
 * Copyright (c) 2025. Benik Arakelyan
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

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.utils.URIBuilder;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HttpRequestUtilsTest {

    @Test
    public void appendPath_shouldNotCreateDoubleSlash_whenCurrentHasNoTrailingSlash_andPathStartsWithSlash() throws URISyntaxException {
        URIBuilder b = new URIBuilder("http://example.com/api");

        HttpRequestUtils.appendPath(b, "/v1");

        assertEquals("/api/v1", b.getPath());
    }

    @Test
    public void appendPath_shouldNotCreateDoubleSlash_whenCurrentHasTrailingSlash_andPathHasNoLeadingSlash() throws URISyntaxException {
        URIBuilder b = new URIBuilder("http://example.com/api/");

        HttpRequestUtils.appendPath(b, "v1");

        assertEquals("/api/v1", b.getPath());
    }

    @Test
    public void appendPath_shouldNotCreateDoubleSlash_whenCurrentHasTrailingSlash_andPathHasLeadingSlash() throws URISyntaxException {
        URIBuilder b = new URIBuilder("http://example.com/api/");

        HttpRequestUtils.appendPath(b, "/v1");

        assertEquals("/api/v1", b.getPath());
    }

    @Test
    public void getContentTypeFromHttpEntity_shouldReturnNull_whenHeaderIsMissing() {
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
            public Header getContentType() {
                return null;
            }

            @Override
            public Header getContentEncoding() {
                return null;
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
            public void consumeContent() throws IOException {

            }

        };

        assertNull(entity.getContentType());
    }
}
