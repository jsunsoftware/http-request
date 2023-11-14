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

import org.apache.http.HttpEntity;

import java.io.IOException;
import java.io.InputStream;

public interface ResponseBodyReaderContext<T> extends ResponseBodyReadableContext {
    /**
     * @return content stream of the entity.
     *
     * @throws IOException if the stream could not be created.
     */
    InputStream getContent() throws IOException;

    /**
     * @return the type that is to be read from the entity stream.
     */
    @Override
    Class<T> getType();

    /**
     * @return the {@link HttpEntity}
     */
    HttpEntity getHttpEntity();
}
