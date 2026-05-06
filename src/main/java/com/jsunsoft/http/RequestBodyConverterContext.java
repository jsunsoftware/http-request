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

import org.apache.hc.core5.http.ContentType;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Context passed to {@link RequestBodyConverter} implementations.
 */
public final class RequestBodyConverterContext {
    private final Object body;
    private final ContentType contentType;
    private final Charset charset;

    /**
     * Creates a new conversion context.
     *
     * @param body request body (non-null)
     * @param contentType request content type, may be {@code null}
     * @param charset request charset, defaults to UTF-8 if {@code null}
     */
    public RequestBodyConverterContext(Object body, ContentType contentType, Charset charset) {
        this.body = ArgsCheck.notNull(body, "body");
        this.contentType = contentType;
        this.charset = charset != null ? charset : StandardCharsets.UTF_8;
    }

    /**
     * @return request body value
     */
    public Object getBody() {
        return body;
    }

    /**
     * @return request {@code Content-Type} as parsed from request headers; may be {@code null}.
     */
    public ContentType getContentType() {
        return contentType;
    }

    /**
     * @return configured request body charset; never {@code null}.
     */
    public Charset getCharset() {
        return charset;
    }
}

