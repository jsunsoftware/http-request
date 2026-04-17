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