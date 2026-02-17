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

import org.apache.hc.core5.http.HttpEntity;

/**
 * Strategy interface for converting request bodies (for {@link WebTarget#rawRequest(HttpMethod, Object)}
 * and other overloads that accept {@code Object}) into an {@link HttpEntity}.
 * <p>
 * Implementations should be stateless / thread-safe.
 */
public interface RequestBodyConverter {
    /**
     * @param context request body conversion context
     * @return {@code true} if this converter can convert the request body for the given context.
     */
    boolean canConvert(RequestBodyConverterContext context);

    /**
     * Converts {@link RequestBodyConverterContext#getBody()} into an {@link HttpEntity}.
     *
     * @param context request body conversion context
     * @return converted {@link HttpEntity}
     * @throws RequestException if conversion fails
     */
    HttpEntity convert(RequestBodyConverterContext context) throws RequestException;
}

