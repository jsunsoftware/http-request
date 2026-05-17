# Http-Request changes

Check also [MIGRATION.md](MIGRATION.md) for possible compatibility problems.

### 1.0.0

Removed method `contentTypeOfBody` from `HttpRequestBuilder`.

### 1.0.1

Added method `addHttpClientCustomizer` into `HttpRequestBuilder`

### 1.0.2

Removed `inputStreamDeserializer` from `ResponseDeserializer`. **Reason:** Response should be deserialized
by `ResponseDeserializer`  
Renamed `ignorableDeserializer` of `ResponseDeserializer` to `toStringDeserializer`

### 2.0.0

Due to the fact that old implementation was designed only for one predefined `URI` and wasn't much flexible the 2.x.x
version don't have back compatibility with version 1.x.x. The 1.x.x versions will be supported in branch `1.x.x_support`
.

# 2.0.1

The method `getContentAsString` of `ResponseBodyReaderContext` become deprecated and will be removed in further
versions.

# 2.1.0

The `ResponseBodyReaderContext` become generic.

# 2.2.0

The `path` method of `WebTarget` now adds path to existed path instead of replacing, for replace use `setPath` method.

# 2.2.2

Headers are available from `ResponseHandler`

# 3.3.x

* Starting with 3.3.0 can be used immutable web target. `HttpRequest.immutableTarget(uri)`


* Now request methods supported an auto-serializing object to body for content types `application/json`
  and `application/xml`


* Added `ResponseHandler.recuiredGet()` method.

# 3.4.1

Added methods `ClientBuilder.enableCookieManagement`, `ClientBuilder.enableAutomaticRetries` and
`ClientBuilder.setConnectionTimeToLive`.

# 3.4.2

Added methods `ClientBuilder.addDefaultConnectionManagerBuilderCustomizer`.

# 3.5.0

* Charset handling was clarified and split:
  * `WebTarget#setCharset(Charset)` now sets both the query-string charset and the request body charset.
  * Added `WebTarget#setQueryCharset(Charset)` to control only the URI query-string percent-encoding charset.
  * Added `WebTarget#setBodyCharset(Charset)` to control only the request body charset used by request body converters.
  * URI path segments are always percent-encoded as UTF-8 per RFC 3986; if you need non-UTF-8 path
    encoding, percent-encode the path yourself before passing it to `target(...)`.
* Default charset for both query-string and body is `UTF-8`.
* `HttpRequestBuilder.setDefaultJsonMapper` / `setDefaultXmlMapper` now take a defensive copy of the
  supplied mapper at the moment the setter is called; the caller's instance is never mutated by the
  library. `addResponseDefaultDateDeserializationPattern` / `addRequestDefaultDateSerializationPattern`
  now compose correctly with a user-supplied mapper — previously the patterns were silently dropped.
* Retry API refresh (`@Beta` — breaking):
  * New `RetryAttempt` type exposes `response`, `method`, `uri`, `attemptNumber`, and `error` to
    retry predicates.
  * `RetryContext` methods now take a `RetryAttempt`: `mustBeRetried(RetryAttempt)`,
    `getRetryDelay(RetryAttempt)` (returns `Duration`), `beforeRetry(RetryAttempt, WebTarget)`.
  * **Removed** the pre-3.5.0 response-only overloads (`mustBeRetried(Response)`,
    `getRetryDelay(Response)`, `beforeRetry(WebTarget)`). 3.4.x custom `RetryContext`
    implementations will no longer compile — see `MIGRATION.md` for the before/after template.
  * The default `mustBeRetried(RetryAttempt)` is idempotency-gated: only retries
    GET/HEAD/OPTIONS/PUT/DELETE/TRACE (RFC 9110 §9.2.2) on 503 by default. Retrying POST/PATCH
    must be an explicit opt-in — see the new factory helper `RetryContext.onAnyMethod5xx(int,
    Duration)`.
  * New factory helper `RetryContext.onIdempotent5xx(int, Duration)` — safe default that retries
    idempotent methods on any 5xx, honoring `Retry-After` when present.
  * Added `HttpMethod.isIdempotent()` per RFC 9110.
* Retries now work for requests with repeatable bodies. Previously, any retryable request whose
  `HttpEntity` had been initialized failed on first retry with *"After initializing the httpEntity
  builder can't be copied."* `HttpUriRequestBuilder` now shares the entity reference with the
  copy when `entity.isRepeatable()` is `true` (covers all built-in body paths: `StringEntity`,
  `ByteArrayEntity`, `FileEntity`, and Jackson-produced bodies). Non-repeatable entities
  (`InputStreamEntity` and similar streaming sources) still cannot be replayed and now fail with
  an actionable error pointing at the fix.
* `Response#getContentType()` no longer leaks `UnsupportedCharsetException` (or any other
  `IllegalArgumentException` from `ContentType.parse`) when a server returns a header like
  `Content-Type: text/plain; charset=<charset-not-installed-in-this-JVM>`. The malformed value
  is logged at WARN level and the helper returns `null` — the same as "no Content-Type header."
  Reader chains (`isReadable()` predicates) now fall through cleanly to
  `ResponseBodyReaderNotFoundException` instead of letting an unchecked exception escape from a
  getter.
* Replaced the custom `LimitedInputStream` with `commons-io`'s
  `org.apache.commons.io.input.BoundedInputStream`. The response-body size cap is now enforced
  by configuring `BoundedInputStream` with `setMaxCount(maxBytes + 1)` (so a body of exactly
  `maxBytes` still drains cleanly to EOF) and an `setOnMaxCount` consumer that throws
  `InvalidContentLengthException` the moment the cap is exceeded. The swap closes three latent
  gaps in the previous custom class in one stroke: `skip()` and `read(b, off, len)` no longer
  over-pull the underlying stream past the cap (commons-io clamps both via `toReadLen`),
  `markSupported()` no longer falsely advertises mark support that would corrupt the byte
  counter on `reset()`, and we drop ~70 lines of custom code in favor of a battle-tested upstream
  implementation. `commons-io 2.22.0` is now a compile-scope dependency.
* `StringReader` and `ByteReader` no longer pass `maxLen` to `EntityUtils.toString` /
  `EntityUtils.toByteArray`. The cap is enforced at the byte level by the wrapped
  `BoundedInputStream`; passing a `maxLen` (which is a *character* limit for `toString`) would
  silently truncate the result at a char boundary instead of triggering the byte-cap throw.
* `BoundedHttpEntity#writeTo` is now documented and regression-tested as a load-bearing override
  for the size-cap guarantee: without it `HttpEntityWrapper#writeTo` would delegate straight to
  the wrapped entity, bypassing `BoundedInputStream` and silently spooling oversize bodies through
  callers that use `response.getEntity().writeTo(...)` (e.g. spooling a response to disk). The
  override now also uses `IOUtils.copy` (commons-io) instead of a hand-rolled buffered loop.
* `ClientBuilder` default `defaultMaxPoolSizePerRoute` lowered from `128` to `32`. `maxPoolSize`
  is unchanged at `128`. Previously `perRoute == total` let a single hot host saturate the entire
  pool, leaving parallel requests to other hosts blocked on `connectionRequestTimeout`. The new
  `total / 4` ratio matches industry conventions (Apache HC, AWS SDK, Spring) and preserves
  multi-host fairness. Single-upstream workloads that want the old behavior should call
  `setDefaultMaxPoolSizePerRoute(128)` explicitly — see `MIGRATION.md`.
* `ClientBuilder.proxy(URI)` now rejects URIs that include userinfo (e.g.
  `http://user:pass@proxy.corp`) with `IllegalArgumentException` and a message pointing at Apache
  HC5's `BasicCredentialsProvider`. Previously, the userinfo was silently discarded by the
  `HttpHost` constructor — users who thought they were configuring proxy auth would see no
  credentials sent.
* `ClientBuilder.build()` is now idempotent across repeated calls. Previously, the
  `defaultRequestConfigBuilder` and `defaultConnectionConfigBuilder` were stored as fields and
  user-supplied customizers were applied to those persistent instances on every `build()` —
  state-dependent customizers (e.g. ones that re-register an interceptor or react to current
  builder state) would compound across calls. Each `build()` now snapshots the configured state
  and applies customizers to a fresh `Builder` per call.
* New `setDefaultResponseCharset(Charset)` on `HttpRequestBuilder`. The library now defaults to
  **UTF-8** for response bodies whose `Content-Type` lacks a `charset` parameter (Apache HC5's
  bare default is ISO-8859-1). Server-supplied `charset=...` always wins; the new setting only
  affects the no-charset path. See `MIGRATION.md` for restoring the ISO-8859-1 behavior on
  legacy servers.
* New opt-in SSRF guard `ClientBuilder.disallowPrivateAndLoopbackHosts()`. When enabled, the
  client's DNS resolver rejects any host that resolves to loopback / unspecified / link-local /
  RFC 1918 / IPv6 unique-local addresses. The check is plumbed through Apache HC5's
  `DnsResolver`, so it fires for every host the client touches — including hosts reached via
  3xx redirects, not only the URL the caller passed to `target(...)`. The same DNS lookup that
  produces the connection IP is the one being filtered, closing the time-of-check / time-of-use
  gap a URL-only check would have. Specifically catches user-controlled URLs pointing at
  cloud-metadata endpoints (e.g. `169.254.169.254`). Combine with network-layer egress filtering
  for full defence-in-depth.
* New TLS knobs on `ClientBuilder`: `setTlsVersions(String...)` enforces a TLS version allow-list
  (e.g. `"TLSv1.3", "TLSv1.2"`); `setCipherSuites(String...)` opts out of weak / deprecated
  ciphers. Both delegate to Apache HC5's `ClientTlsStrategyBuilder` and use JVM defaults when
  not called.
* New HTTP/1.1 head-size knobs on `ClientBuilder`: `setMaxHeaderCount(int)` caps the per-message
  header count; `setMaxLineLength(int)` caps any single line (status line or header). Bounds
  memory consumption when talking to a hostile / buggy server that emits an unbounded header
  list — complements `setMaxResponseBodySizeBytes` (which only protects the body, not the head).
  Negative values mean "use Apache HC5's built-in default."
* New `HttpRequestBuilder.addPayloadRedactor(Function<String, String>)` for masking secrets in
  request bodies before they are logged via `enableRequestPayloadLogging()`. Redactors compose
  left-to-right; a buggy redactor that throws is caught and swapped for a `[redaction-failed]`
  placeholder rather than killing the in-flight request. Redactors are only invoked when
  payload logging is enabled.
* Successful `HEAD` responses are no longer remapped to 502. Apache HC5 returns `null`
  `HttpEntity` for `HEAD` responses (HTTP forbids a response body on `HEAD`), and the previous
  code blindly treated `hasBody(statusCode) && entity == null` as a server error. The check now
  excludes `HEAD`; for non-`HEAD` requests the existing safety net is kept (Apache HC5 always
  sets a, possibly length-0, entity for `hasBody(...)` statuses on those methods, so this branch
  effectively only fires on genuinely malformed responses).
* New `RetryContext.withMaxHonoredRetryAfter(RetryContext, Duration)` opt-in clamp on
  `Retry-After`. Wraps any `RetryContext` and caps the honoured retry delay, defending against
  a misbehaving or hostile upstream returning `Retry-After: 99999` (~28 hours) and stalling the
  calling thread. Default behaviour without the wrapper is unchanged — the library is
  RFC-compliant and honours whatever the server says.
* New `ClientBuilder.disallowPrivateAndLoopbackHosts(Predicate<InetAddress>)` overload for the
  SSRF guard. The predicate is an allow-list escape hatch: it is invoked for each resolved
  address that would otherwise be blocked, and a return of `true` permits the address through.
  Useful when you need to block public-internet SSRF surface but still talk to a specific
  internal endpoint (e.g. an internal config service on `10.0.7.42`). Passing `null` for the
  predicate is equivalent to the no-arg overload.
* New `HttpRequestBuilder.setDefaultQueryCharset(Charset)` and
  `HttpRequestBuilder.setDefaultBodyCharset(Charset)`. Set the initial value that every
  {@code WebTarget} produced from the resulting {@code HttpRequest} starts with — applied to
  targets returned by {@code target(...)}, {@code immutableTarget(...)}, and
  {@code retryableTarget(...)}. Per-target {@code setQueryCharset} / {@code setBodyCharset}
  still overrides; passing {@code null} at builder level keeps the library default (UTF-8).
  Closes the gap where consumers wanting a non-UTF-8 default had to override on every
  per-call target. {@code setCharset(Charset)} combo on the builder is intentionally not
  added — explicit per-axis is the only entry point at the builder level, mirroring the
  pre-existing {@code setDefaultResponseCharset} pattern.

# 3.6.0

* Upgraded Apache HttpClient 5 (`httpclient5`) to `5.6.1`. Notable upstream behaviour
  change: the default `HostnameVerificationPolicy` is now `BUILTIN` — a `HostnameVerifier`
  set via `ClientBuilder.hostnameVerifier(...)` alone is silently ignored. Pair the call
  with `hostnameVerificationPolicy(HostnameVerificationPolicy.CLIENT)` to keep the
  verifier authoritative (or use `trustAllHosts()`, which already wires both). See the
  Javadoc on `ClientBuilder.hostnameVerifier(...)` for the full note. No other API or
  behaviour changes.

# 4.0.0

* **Java baseline raised to Java 17.** Bytecode target moves from Java 8 to Java 17;
  the build enforcer now rejects JDKs below 17. The `3.6.x` line remains the supported
  Java 8 baseline. No public API was renamed, removed, or re-typed — source built
  against `3.6.x` recompiles unchanged on `4.0.0`. See `MIGRATION.md`.
* **Published as a JPMS module `com.jsunsoft.http`.** Adds `src/main/java/module-info.java`
  declaring the module with `exports com.jsunsoft.http` and `exports com.jsunsoft.http.annotations`.
  Apache HttpClient 5 (client + core) and Jackson `databind` / `dataformat-xml` are
  `requires transitive`, so modulepath consumers see the public-API types from those modules
  without re-declaring them. Classpath consumers are unaffected.
* Internal modernization to Java 9–17 idioms (no behaviour change): defensive collection
  copies now use `List.copyOf` / `Set.copyOf`; `HttpMethod.isIdempotent()` is a switch
  expression with compile-time-exhaustive enum cases; pattern-matching `instanceof`
  applied in `BasicResponseHandler`, `BasicWebTarget`, and `TypeReference`.
* Build: `maven-compiler-plugin` configuration collapsed to a single `<release>17</release>`
  (previously a dual main/test execution); added an explicit `maven-surefire-plugin` with
  `<useModulePath>false</useModulePath>` so tests run on the classpath without forcing the
  production module to `opens` its package to reflective frameworks.

# 5.0.0-rc1

* **Jackson 2.x → 3.x.** `jackson-databind` and `jackson-dataformat-xml` move from
  `com.fasterxml.jackson.{core,dataformat}` to `tools.jackson.{core,dataformat}` (3.1.3);
  JPMS module names follow (`tools.jackson.databind`, `tools.jackson.core`,
  `tools.jackson.dataformat.xml`). `jackson-annotations` stays on
  `com.fasterxml.jackson.core:jackson-annotations` (2.21) — the annotations artifact is
  intentionally shared between Jackson 2.x and 3.x consumers. `jackson-datatype-jdk8`,
  `jackson-datatype-jsr310`, and `jackson-module-parameter-names` are folded into
  `jackson-databind` in 3.0 and their standalone declarations are dropped. The Jackson 3 BOM
  is imported in `dependencyManagement` so all `tools.jackson.*` coordinates resolve to a
  single coherent release train. See **[MIGRATION.md](MIGRATION.md)** for the full upgrade
  guide — built-in modules, immutable-mapper consequences, exception-hierarchy changes,
  default-toggle flips, and the `HEURISTIC` single-arg constructor binding behaviour.
* **Mapper handling refactored for Jackson 3's immutable model.** `HttpRequestBuilder`'s
  `setDefaultJsonMapper(ObjectMapper)` and `setDefaultXmlMapper(ObjectMapper)` no longer
  take a defensive `.copy()` — Jackson 3 mappers are immutable, so the supplied reference is
  stored directly; per-config date-pattern overrides applied via
  `addResponseDefaultDateDeserializationPattern` /
  `addRequestDefaultDateSerializationPattern` are installed on a fresh derivative produced
  via `ObjectMapper#rebuild()`, leaving the caller's mapper untouched.
* **Parse-failure routing tightened.** Malformed-JSON / type-mismatch responses now surface
  as `ResponseBodyReaderException` (→ HTTP 502 Bad Gateway on the `ResponseHandler`),
  consistent with `InvalidContentLengthException`. In 4.0.0 these incidentally surfaced as
  503 Service Unavailable because Jackson 2.x's `JsonProcessingException` extended
  `IOException`; Jackson 3 fixed the hierarchy and the library follows the corrected
  semantics. Stream-level IO failures still route to 503; size-cap failures still route to 502.
* Jackson 3 default-toggle changes are accepted as-is rather than papered over with
  `builderWithJackson2Defaults()` — see MIGRATION.md for the per-toggle table (most are
  strict/safe wins; the visible deltas are `READ/WRITE_ENUMS_USING_TO_STRING` and
  `SORT_PROPERTIES_ALPHABETICALLY`).