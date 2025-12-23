/*
 * Copyright 2017 Benik Arakelyan
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

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;

final class BasicResponseBodyReaderContext<T> implements ResponseBodyReaderContext<T> {
    private final ClassicHttpResponse httpResponse;
    private final Class<T> type;
    private final Type genericType;
    private final URI uri;
    private final long maxResponseBodySizeBytes;

    BasicResponseBodyReaderContext(ClassicHttpResponse httpResponse, Class<T> type, Type genericType, URI uri, long maxResponseBodySizeBytes) {
        this.httpResponse = ArgsCheck.notNull(httpResponse, "httpResponse");
        this.type = ArgsCheck.notNull(type, "type");
        this.genericType = ArgsCheck.notNull(genericType, "genericType");
        this.uri = ArgsCheck.notNull(uri, "uri");
        this.maxResponseBodySizeBytes = maxResponseBodySizeBytes;
    }

    @Override
    public InputStream getContent() throws IOException {
        HttpEntity entity = httpResponse.getEntity();
        if (entity == null) {
            return null;
        }

        long maxBytes = maxResponseBodySizeBytes;
        if (maxBytes <= 0) {
            return entity.getContent();
        }

        long contentLength = entity.getContentLength();
        if (contentLength > maxBytes) {
            throw new InvalidContentLengthException(contentLength, "Response body exceeds maximum allowed size: " + maxBytes + " bytes");
        }

        return new LimitedInputStream(entity.getContent(), maxBytes);
    }

    @Override
    public ContentType getContentType() {
        return HttpRequestUtils.getContentTypeFromHttpEntity(getHttpEntity());
    }

    @Override
    public long getContentLength() {
        return getHttpEntity().getContentLength();
    }

    @Override
    public HttpEntity getHttpEntity() {
        return httpResponse.getEntity();
    }

    @Override
    public Class<T> getType() {
        return type;
    }

    @Override
    public Type getGenericType() {
        return genericType;
    }

    @Override
    public int getStatusCode() {
        return httpResponse.getCode();
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public boolean hasEntity() {
        return getHttpEntity() != null;
    }

    @Override
    public long getMaxResponseBodySizeBytes() {
        return maxResponseBodySizeBytes;
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private final long maxBytes;
        private long readBytes;

        LimitedInputStream(InputStream in, long maxBytes) {
            super(in);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) {
                incrementAndCheck(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) {
                incrementAndCheck(n);
            }
            return n;
        }

        private void incrementAndCheck(int delta) throws IOException {
            readBytes += delta;
            if (readBytes > maxBytes) {
                throw new InvalidContentLengthException(readBytes, "Response body exceeds maximum allowed size: " + maxBytes + " bytes");
            }
        }
    }
}
