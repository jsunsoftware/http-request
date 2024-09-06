/*
 * Copyright (c) 2022. Benik Arakelyan
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

import java.util.Map;

/**
 * Interface representing a context for date deserialization.
 * Provides a mapping between date types and their corresponding date patterns.
 */
public interface DateDeserializeContext {

    /**
     * Gets the mapping of date types to their corresponding date patterns.
     *
     * @return a map where the key is the date type class and the value is the date pattern string.
     */
    Map<Class<?>, String> getDateTypeToPattern();
}
