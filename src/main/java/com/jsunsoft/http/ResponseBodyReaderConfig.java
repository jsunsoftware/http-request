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

/*
 * Date: 11/20/2019.
 * Developer: Beno Arakelyan
 */


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

class ResponseBodyReaderConfig {
    private final ResponseBodyReader<?> defaultResponseBodyReader;
    private final Collection<ResponseBodyReader<?>> responseBodyReaders;
    private final boolean useDefaultReader;

    private ResponseBodyReaderConfig(ResponseBodyReader<?> defaultResponseBodyReader, Collection<ResponseBodyReader<?>> responseBodyReaders, boolean useDefaultReader) {
        this.defaultResponseBodyReader = defaultResponseBodyReader;
        this.responseBodyReaders = Collections.unmodifiableList(new ArrayList<>(ArgsCheck.notNull(responseBodyReaders, "responseBodyReaders")));
        this.useDefaultReader = useDefaultReader;
    }

    ResponseBodyReader<?> getDefaultResponseBodyReader() {
        return defaultResponseBodyReader;
    }


    Collection<ResponseBodyReader<?>> getResponseBodyReaders() {
        return responseBodyReaders;
    }

    boolean isUseDefaultReader() {
        return useDefaultReader;
    }

    static Builder create() {
        return new Builder();
    }

    static class Builder {
        private ResponseBodyReader<?> defaultResponseBodyReader;
        private Collection<ResponseBodyReader<?>> responseBodyReaders;
        private boolean useDefaultReader = true;

        private Builder() {
        }

        Builder setDefaultResponseBodyReader(ResponseBodyReader<?> defaultResponseBodyReader) {
            this.defaultResponseBodyReader = defaultResponseBodyReader;
            return this;
        }

        Builder addResponseBodyReader(ResponseBodyReader<?> responseBodyReader) {
            if (responseBodyReaders == null) {
                responseBodyReaders = new ArrayList<>();
            }

            this.responseBodyReaders.add(responseBodyReader);

            return this;
        }

        Builder setUseDefaultBodyReader(boolean useDefaultReader) {
            this.useDefaultReader = useDefaultReader;

            return this;
        }

        ResponseBodyReaderConfig build() {
            if (useDefaultReader && defaultResponseBodyReader == null) {
                defaultResponseBodyReader = new DefaultResponseBodyReader<>(BasicDateDeserializeContext.DEFAULT);
            }

            if (responseBodyReaders == null) {
                responseBodyReaders = Collections.emptyList();
            }

            return new ResponseBodyReaderConfig(defaultResponseBodyReader, responseBodyReaders, useDefaultReader);
        }
    }
}
