package com.jsunsoft.http;

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

import java.net.URI;

public interface HttpRequest {


    WebTarget target(URI uri);

    /**
     * @param uri The string to be parsed into a URI
     * @return Target instance
     * @throws NullPointerException     If {@code str} is {@code null}
     * @throws IllegalArgumentException If the given string violates RFC&nbsp;2396
     */

    WebTarget target(String uri);
}
