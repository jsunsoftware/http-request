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

import java.util.Map;
import java.util.Objects;

public final class NameValuePairImpl implements NameValuePair {

    private final String name;
    private final String value;

    /**
     * The value may be null.
     *
     * @param name  The name.
     * @param value The value.
     */
    public NameValuePairImpl(final String name, final String value) {
        this.name = ArgsCheck.notNull(name, "Name");
        this.value = value;
    }

    public NameValuePairImpl(Map.Entry<String, String> entry) {
        this.name = ArgsCheck.notNull(entry.getKey(), "Key of entry");
        this.value = entry.getValue();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getValue() {
        return this.value;
    }

    @Override
    public String toString() {
        return "NameValuePairImpl{" +
                "name='" + name + '\'' +
                ", value='" + value + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        NameValuePairImpl that = (NameValuePairImpl) o;

        return Objects.equals(name, that.name) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }
}
