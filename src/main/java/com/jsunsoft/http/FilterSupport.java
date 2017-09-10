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

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class FilterSupport<T> {
    private final ResponseHandler<T> responseHandler;
    private final Collection<Predicate<ResponseHandler<T>>> filters = new ArrayList<>();

    private FilterSupport(ResponseHandler<T> responseHandler, Predicate<ResponseHandler<T>> predicate) {
        this.responseHandler = responseHandler;
        filters.add(predicate);
    }

    public FilterSupport<T> filter(Predicate<ResponseHandler<T>> predicate) {
        filters.add(predicate);
        return this;
    }

    public OtherwiseSupport<T> ifPassed(Consumer<ResponseHandler<T>> consumer) {
        if (filters.stream().allMatch(predicate -> predicate.test(responseHandler))) {
            consumer.accept(responseHandler);
            return OtherwiseSupport.createIgnored(responseHandler);
        }
        return OtherwiseSupport.createNotIgnored(responseHandler);
    }


    public OtherwiseSupport<T> ifNotPassed(Consumer<ResponseHandler<T>> consumer) {
        if (filters.stream().anyMatch(predicate -> predicate.negate().test(responseHandler))) {
            consumer.accept(responseHandler);
            return OtherwiseSupport.createNotIgnored(responseHandler);
        }
        return OtherwiseSupport.createIgnored(responseHandler);
    }

    static <T> FilterSupport<T> create(ResponseHandler<T> responseHandler, Predicate<ResponseHandler<T>> predicate) {
        return new FilterSupport<>(responseHandler, predicate);
    }
}
