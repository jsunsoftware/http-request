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
version don't have back compatibility with version 1.x.x. The 1.x.x versions will be supported in branch
`1.x.x_support`.

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


* Added `ResponseHandler.requiredGet()` method.

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
  `WebTarget` produced from the resulting `HttpRequest` starts with — applied to
  targets returned by `target(...)`, `immutableTarget(...)`, and
  `retryableTarget(...)`. Per-target `setQueryCharset` / `setBodyCharset`
  still overrides; passing `null` at builder level keeps the library default (UTF-8).
  Closes the gap where consumers wanting a non-UTF-8 default had to override on every
  per-call target. A `setCharset(Charset)` combo on the builder is intentionally not
  added — explicit per-axis is the only entry point at the builder level, mirroring the
  pre-existing `setDefaultResponseCharset` pattern.

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


# 4.1.0

Backports the fixes and hardening of 5.0.0 to the Jackson 2 line — everything except the move to Jackson 3, the removed
default date patterns and the API removals. The `4.x` line remains the supported Jackson 2 baseline. Several fixes
change behavior; see [MIGRATION.md](MIGRATION.md).

* **Dependency upgrades.** Jackson `2.21.3` → `2.21.8`, Apache HttpClient 5 `5.6.1` → `5.6.5`, `commons-lang3` `3.20.0`
  → `3.21.0`, `slf4j-api` `2.0.17` → `2.0.20`.
* **Body-read failures are classified by where they happen (502 vs. 503).** An unusable payload — malformed or
  type-mismatched JSON or XML, undecodable bytes, or a body that doesn't match its `Content-Encoding` (e.g. corrupt
  gzip) — now surfaces as `ResponseBodyReaderException` (→ HTTP 502 Bad Gateway on the `ResponseHandler`), consistent
  with the size cap's `InvalidContentLengthException`. In 4.0.0 these surfaced as 503 Service Unavailable, the code for
  a broken connection, because they arrived as `IOException`s (Jackson's `JsonProcessingException` extends
  `IOException`). The JSON, XML and text readers now classify a failure identically, by where it happened rather than by
  how Jackson wrapped it: a connection that breaks while the body is read still maps to 503, and the size cap still maps
  to 502.
* **Jackson exceptions from a custom `ResponseBodyReader` are reported like a built-in reader's failure.** A custom
  reader calling Jackson directly reports a parse or mapping error as `JsonProcessingException` and undecodable bytes as
  `CharConversionException`; both are `IOException`s, so they surfaced as a 503 like a broken connection. They are now a
  `ResponseBodyReaderException` — 502 on a `ResponseHandler`, `ResponseBodyProcessingException` from `readEntity` —
  unless caused by a failure of the stream (a broken connection, the size cap, an interrupt), which keeps its own
  classification.
* **An interrupted caller gets `RequestException` (behavior change).** When the calling thread is interrupted, both the
  `ResponseHandler` and the `Response` API now throw `RequestException` and leave the thread's interrupt flag set.
  Previously the outcome depended on where the interrupt hit: before the request was sent (a raw `CancellationException`
  from HttpCore's cancelled connection lease), while waiting for a pooled connection (a synthetic 503), between retries
  (`IllegalStateException`), or while reading the body on a virtual thread (a synthetic 503).
  `ResponseHandler`-returning methods such as `get(User.class)` now throw in this case instead of returning a handler.
* **A failure while closing a response no longer discards the result.** On the `ResponseHandler` API, an `IOException`
  from closing the response replaced the already-read result with a synthetic 500 handler. The result now stands, and
  the close failure is logged at WARN.
* **Bounded close-drain for size-capped responses (fixes an unbounded cap-bypassing read).**
  With `setMaxResponseBodySizeBytes` configured, `Response.close()` previously ended up reading the entire unread
  remainder (Apache HC5's own `response.close()` self-exhausts the raw entity stream, bypassing the cap) — a hostile
  oversized body was read to the end on close. `close()` now drains through the cap-enforcing stream under a dual
  bound — at most a configurable byte allowance within a hard wall-clock timeout (the drain runs on a shared daemon
  thread, JDK `KeepAliveStreamCleaner`-style) — and otherwise detaches the entity and discards the connection instead
  of reading further. New `@Beta` knobs on `HttpRequestBuilder`: `setMaxResponseDrainSizeBytes(long)` (default
  `65536`; `0` = never drain; negative = unlimited, bounded only by the cap and the timeout) and
  `setResponseDrainTimeout(Duration)` (default 100 ms; must be positive). Both require the response body size cap and
  are rejected at `build()` without it. Uncapped behavior is unchanged (full consume on close).
* **Remaining size-cap bypasses closed.** A reader that closes the body stream early (Jackson after a parse error,
  `EntityUtils` after a cap violation) no longer makes Apache HC5 read the rest of the body beneath the cap: the
  remainder is left to the bounded drain above, and a body read to its end still releases its connection (Jackson 2
  stops reading a JSON body at the end of its value, so with a size cap that connection is released when the `Response`
  is closed). A declared `Content-Length` above the cap fails with `InvalidContentLengthException` before a single byte
  is read, and repeated `getContent()` calls on a capped entity share one byte budget instead of starting a fresh one
  each. For `Content-Encoding: gzip` / `deflate` bodies the cap counts decoded bytes. **Known limitation** (present
  since the cap was introduced in 3.5.0): bytes a server sends after the end of the compressed data are not bounded in
  size or time — Apache HC5 reads and discards them when the decoded stream is closed.
* **`ClientBuilder.setMaxPoolSizePerRoute` now actually caps the routes it names.** The cap was registered as a bare
  `new HttpRoute(host)`, which never matched the runtime route shape the pool is keyed by: a portless `HttpHost` failed
  `build()` with Apache's "Target port must not be negative", and an **https host's cap was silently ignored on every
  HC5 version** (the registered route lacked the secure flag), as was any cap when a proxy was configured (missing proxy
  hop). Registration now mirrors the runtime route planner: scheme-default port resolved, secure flag derived from the
  scheme, configured proxy included. An `HttpHost` without a scheme means `http`. Caps under `useDefaultProxy()` or a
  custom route planner remain build-time-unknowable and are documented as such.
* **Pool-size setters validate their input (behavior change for misconfigurations).** `ClientBuilder.setMaxPoolSize` /
  `setDefaultMaxPoolSizePerRoute` / `setMaxPoolSizePerRoute` now throw `IllegalArgumentException` for values `< 1` (and
  `NullPointerException` for a null host). Previously such values were accepted and then silently ignored by Apache
  HC5's pool builder, which fell back to its internal defaults (25 total / 5 per route) — a configured
  `setMaxPoolSize(0)` quietly produced a 25-connection pool.
* **No more spurious WARN for body-less non-success responses.** A typed request receiving an entity-less non-success
  response (e.g. a 304 with redirect handling disabled — the default) no longer attempts to read error text: readers
  are not consulted for a body that isn't there and `getErrorText()` is `null`. The outcome is still traced — the
  "Unexpected Response …" log line fires for every non-success response (WARN for 400, DEBUG otherwise), with or
  without a body. The related "Can't find body reader … when http entity is null" log in `readEntity` is demoted from
  WARN to DEBUG — returning `null` for an entity-less response is the documented contract, not an anomaly.
* **Verbose logging no longer leaks credentials or payloads (security).** With TRACE enabled on
  `com.jsunsoft.http`, several log points printed secrets verbatim, bypassing the configured payload redactor. All are
  closed: `HttpUriRequestBuilder.toString()` (the "Executing request: …" TRACE) masks `Authorization`,
  `Proxy-Authorization`, `Cookie` and `Set-Cookie` header values as `<redacted>` and prints the entity's class instead
  of its content; `ResponseHandler.toString()` (the "Executed result: …" TRACE) reports the content's presence/type and
  a header count instead of dumping the body and every header value; the request-serialization and response-result
  traces log types, not values; and the response reader's whole-body "Content is: …" TRACE is removed. Non-success error
  text (the `getErrorText()` diagnostic channel) and request URIs/query parameters are still logged — don't put secrets
  in query strings.
* **`enableRequestPayloadLogging` now covers `Object` payloads.** The `post(Object)` / `rawRequest(method, Object)`
  family — the main JSON path — previously never produced the redacted payload log; only the `String` overloads did. The
  serialized wire form is now routed through the composed redactors, exactly like `String` payloads. A repeatable
  serialized entity is read back for logging (briefly holding a second in-memory copy); a non-repeatable one is never
  consumed — a placeholder is logged instead. Raw `HttpEntity` bodies remain excluded from payload logging (also when
  passed through the `Object` overloads): an entity may be arbitrarily large or binary, and reading it back for a log
  line could materialize it on the heap.
* **SSRF guard covers the full IANA non-global address space (security).** `disallowPrivateAndLoopbackHosts()`
  previously rejected only loopback / unspecified / link-local / RFC 1918 / IPv6 unique-local addresses — a request to
  e.g. `100.64.0.1` (carrier-grade NAT space) sailed past the guard and attempted a real connection. The resolver now
  also blocks `0.0.0.0/8`, CGNAT `100.64.0.0/10`, `192.0.0.0/24`, the TEST-NETs (`192.0.2.0/24`, `198.51.100.0/24`,
  `203.0.113.0/24`), benchmarking `198.18.0.0/15`, reserved `240.0.0.0/4` (incl. broadcast), multicast, the NAT64
  local-use prefix `64:ff9b:1::/48`, and IPv6 forms embedding an IPv4 address (NAT64 `64:ff9b::/96`,
  IPv4-mapped/-compatible) when the embedded IPv4 is itself non-global — `64:ff9b::<public v4>` still resolves, so
  legitimate NAT64 clients keep working. The allow-list predicate overload applies to the new ranges too. The javadoc
  now documents the guard's limitations: with a proxy configured, the proxy — not the guarded resolver — resolves the
  target host, and the IPv4 address embedded in 6to4 (`2002::/16`) and Teredo (`2001::/32`) addresses is not decoded.
* **`basicAuth(String, char[])` zeroes the password on every path (security).** The zeroing `finally` wrapped only the
  final header registration: a username rejected for containing `':'` (or any encoding failure) returned with the
  caller's password array — and every intermediate secret buffer — intact. The whole body now runs in `try/finally`; the
  array and all intermediate copies are zeroed whether the method completes normally or throws. Only a `null`
  password (NPE, nothing to zero) is exempt. Callers must not reuse the array after the call, even a failed one — which
  the documented contract ("zeroed before this method returns") always implied.
* **The SSRF guard can no longer be silently replaced by a customizer (behavior change).**
  `disallowPrivateAndLoopbackHosts()` used to install its guarded DNS resolver before the connection-manager customizers
  ran, so a `setDnsResolver(...)` inside `addDefaultConnectionManagerBuilderCustomizer(...)` silently disabled the
  guard. The guard is now installed after customizers and takes precedence: enabling it means resolution goes through
  the guarded system-default resolver, full stop. Anyone who relied on a customizer-supplied resolver alongside the
  guard was running without SSRF protection without knowing it — if specific internal endpoints must stay reachable, use
  the `disallowPrivateAndLoopbackHosts(Predicate)` allow-list overload; a custom resolver combined with the guard is not
  supported.
* **`getDuration()` is now measured with the monotonic clock.** Request duration was computed from
  `System.currentTimeMillis()`, so an NTP wall-clock step mid-request skewed it (even negative). The entire timing
  path — request start stamp, `ResponseHandler.getDuration()`, and the elapsed-time debug logs — now uses
  `System.nanoTime()`.
* **A `charset` on the request's `Content-Type` header now decides the body encoding.** `String` and `Object`
  payloads were always encoded with the body charset (`setBodyCharset` / `setCharset`, the builder's
  `setDefaultBodyCharset`, or UTF-8), so a request whose header declared `charset=ISO-8859-1` went out with UTF-8 bytes.
  A charset declared on the header now wins; the body charset applies when the header declares none. An unparseable
  `Content-Type` value is ignored with a WARN.
* **`getURI()` / `getURIString()` include the parameters added with `addParameter(s)`.** They returned the URI
  without them, unlike the request actually sent. (An entity-less POST/PUT sends its parameters as a form body, so its
  URI on the wire doesn't carry them.)
* **`FilterSupport.ifNotPassed(...).otherwise(...)` runs the right branch.** The `otherwise` action ran together with
  the `ifNotPassed` action when a filter failed, and never ran when every filter passed. It now runs only when every
  filter passed.
* **`HttpRequestBuilder` can be reused after `build()`.** `addDefaultHeader(s)`, `addDefaultRequestParameter` and
  `addRequestBodyConverter` threw `UnsupportedOperationException` when first called after a `build()`; the documented
  build → mutate → build reuse now works.
* **`RetryableWebTarget` closes the response when a `RetryContext` callback throws.** An exception from
  `mustBeRetried`, `getRetryDelay` or `beforeRetry` propagated with the last response still open, leaking its pooled
  connection. A `null` returned by `beforeRetry` now fails fast with a `NullPointerException` naming it.
* **`ClientBuilder` hands HttpClient a snapshot of its default headers.** Headers added with `addDefaultHeader(s)`
  after `build()` no longer reach clients built earlier.
* **`ImmutableWebTarget` handles `null` like `target(...)`.** `path`, `setPath` and the header and parameter mutators
  reject `null` with `NullPointerException`, and a `null` passed to its charset setters falls back to UTF-8 instead of
  encoding form bodies as ISO-8859-1.
* **`UnexpectedResponseException` is no longer `@Deprecated`.** It is the superclass of its two designated replacements
  (`UnexpectedStatusCodeException`, `MissingResponseBodyException`), so the advertised removal could never happen — it
  is now documented as the permanent base type; catch it to handle both. 5.0.0 also removes the never-thrown
  `InvalidMimeTypeException` and the deprecated `ResponseBodyReader` statics; 4.x keeps them.
* **Documentation now matches behavior.** `ResponseHandler#getOriginalCode()` and
  `ResponseException#getOriginalStatusCode()` document the `-1` they return when no response was received (the
  `getOriginalCode()` javadoc claimed `getCode()`). `getErrorText()` javadoc describes what is actually returned
  (non-success body, failure exception messages, or both; `null` when none) instead of a stale "connection failed but
  server sent useful data" description with a bogus `@throws UnsupportedOperationException`; `ifHasContent` documents
  `NullPointerException` for a null consumer (what `ArgsCheck.notNull` actually throws); `removeHeader`'s
  null-validation message names the right parameter; `WebTarget` javadoc typos fixed. The `WebTarget` javadoc now states
  that a `target(...)` instance serves a single request (a body, header or parameter set on it is re-sent by a later
  call), that `addParameter` on an entity-less POST/PUT sends a form body instead of a query string, and that
  `trace(...)` with a body throws `IllegalStateException`; `ClientBuilder` documents that its default headers follow
  every redirect. The "Basic authentication is being used" log is demoted from WARN to INFO — it fired unconditionally
  even for HTTPS-only clients.
