/*
 * Copyright (c) 2021. Benik Arakelyan
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

class ResponseBodyReaders {
    private static final ResponseBodyReader<String> STRING_READER = new DefaultStringResponseBodyReader();

    private static final ResponseBodyReader<String> WHEN_NON_SUCCESS_STRING_READER = new DefaultStringResponseBodyReader() {
        @Override
        public boolean isReadable(ResponseBodyReadableContext bodyReadableContext) {
            return super.isReadable(bodyReadableContext) && bodyReadableContext.isNonSuccess();
        }
    };

    private static final ResponseBodyReader<String> WHEN_SUCCESS_STRING_READER = new DefaultStringResponseBodyReader() {
        @Override
        public boolean isReadable(ResponseBodyReadableContext bodyReadableContext) {
            return super.isReadable(bodyReadableContext) && bodyReadableContext.isSuccess();
        }
    };


    private ResponseBodyReaders() {
    }

    static ResponseBodyReader<String> stringReader() {
        return STRING_READER;
    }

    static ResponseBodyReader<String> whenNonSuccessStringReader() {
        return WHEN_NON_SUCCESS_STRING_READER;
    }

    static ResponseBodyReader<String> whenSuccessStringReader() {
        return WHEN_SUCCESS_STRING_READER;
    }
}
