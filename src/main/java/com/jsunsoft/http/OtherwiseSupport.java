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

import java.util.function.Consumer;

public class OtherwiseSupport<T> {

    private final ResponseHandler<T> responseHandler;
    private final boolean ignored;

    private OtherwiseSupport(ResponseHandler<T> responseHandler, boolean ignored) {
        this.responseHandler = responseHandler;
        this.ignored = ignored;
    }

    /**
     * Support action otherwise.
     * If some condition fails then invoke the specified consumer with the responseHandler.
     *
     * @param consumer block to be executed if first condition failed code.
     */
    public void otherwise(Consumer<ResponseHandler<T>> consumer) {
        if (!ignored) {
            consumer.accept(responseHandler);
        }
    }

    static <T> OtherwiseSupport<T> createIgnored(ResponseHandler<T> responseHandler) {
        return new OtherwiseSupport<>(responseHandler, true);
    }

    static <T> OtherwiseSupport<T> createNotIgnored(ResponseHandler<T> responseHandler) {
        return new OtherwiseSupport<>(responseHandler, false);
    }
}
