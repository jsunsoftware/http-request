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

import org.apache.http.NameValuePair;

final class Constants {
    static final NameValuePair[] EMPTY_NAME_VALUE_PAIRS = new NameValuePair[0];
    static final String CONNECTION_WAS_ABORTED = "Connection was aborted";

    private Constants() {
        throw new AssertionError("No com.jsunsoft.http.Constants instances for you!");
    }
}
