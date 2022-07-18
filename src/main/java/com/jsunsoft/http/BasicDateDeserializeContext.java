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

import java.util.Collections;
import java.util.Map;

class BasicDateDeserializeContext implements DateDeserializeContext {

    private final Map<Class<?>, String> dateTypeToPattern;

    BasicDateDeserializeContext(Map<Class<?>, String> dateTypeToPattern) {
        this.dateTypeToPattern = Collections.unmodifiableMap(dateTypeToPattern);
    }


    @Override
    public Map<Class<?>, String> getDateTypeToPattern() {
        return dateTypeToPattern;
    }
}
