package com.jsunsoft.http.annotations;

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


import java.lang.annotation.*;

/**
 * Signifies that a public API (public class, method, or field) is subject to incompatible
 * changes, or even removal, in a future release. An API bearing this annotation is exempt from
 * the compatibility guarantees of its containing library — concretely, the signature, default
 * behavior, exception types, and method names of {@code @Beta} APIs may change between minor
 * (3.x → 3.y) and patch (3.5.0 → 3.5.1) releases without a deprecation cycle.
 * <p>
 * The annotation says nothing about the quality or performance of the API; only that it is not
 * "API-frozen."
 * <p>
 * It is generally safe for <i>applications</i> to depend on beta APIs, at the cost of some
 * extra work during upgrades. It is generally inadvisable for <i>libraries</i> — whose users'
 * classpaths are outside the library developer's control — to depend on someone else's beta
 * API; an application picking up the dependency transitively can be broken silently when the
 * provider library upgrades.
 *
 * <h2>Retention</h2>
 *
 * Retained at runtime so static-analysis tools (ErrorProne, IDE inspections, dependency-check
 * plugins) can flag usage of {@code @Beta} APIs in downstream code.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({
        ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR,
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.TYPE
})
@Documented
public @interface Beta {
}
