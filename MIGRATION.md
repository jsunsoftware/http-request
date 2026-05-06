# Migration between http-request releases

### 1.0.0

For building HttpRequest you should use `addContentType` instead of `contentTypeOfBody`.

### 1.0.2

Replace `ignorableDeserializer` of `ResponseDeserializer` to `toStringDeserializer`

# 2.0.0

The 2.x.x version don't have back compatibility with version 1.x.x.

# 2.1.0

Replace `getType` of `ResponseBodyReaderContext` to `getGenericType`.

The method `getType` of `ResponseBodyReaderContext` now returns Class<T> the type that is to be read from the entity
stream.

Replace `setUseDefaultReader(boolean useDefaultReader)` of `HttpRequestBuilder` to `enableDefaultBodyReader`
or `disableDefaultBodyReader`

# 2.2.x

Rename method `path` of `WebTarget` to `setPath`. The `path` method now adds path to existed path instead of replacing,
for replace use `setPath` method.

# 3.x.x

Replace all `org.apache.http` package's classes by appropriate httpclient5 classes.

Rename anywhere `getAllHeaders` method by `getHeaders`.

Methods `getStatusCode` of classes `Response` and `ResponseHandler` become deprecated use `getCode` instead.

When building http client by `ClientBuilder` the default connect timeout increased from 5 to 10 seconds.

# 3.3.x

Remove methods with explicit body that typically do not include a body e.g `WebTarget.get(String)`,
`WebTarget.head(String)`

Support with `WebTarget.request(HttpMethod, HttpEntity)` still present.

# 3.4.x

Rename `addDefaultDateDeserializationPattern` of `HttpRequestBuilder` has been renamed to
`addResponseDefaultDateDeserializationPattern`.

Removed support default deserialization of joda.time module. To achieve that, provide custom Json or Xml mapper e.g(
`HttpRequestBuilder.setDefaultJsonMapper(mapper)`) or use custom response reader.

# 3.5.x

## Removed

- Removed `setDefaultRequestBodyConverter` of `HttpRequestBuilder`. To achieve that functionality, call
  `disableDefaultBodyReader` and then add a custom reader as the last reader via `addBodyReader`.

## Renamed / moved

- `WebTarget#setUriCharset(Charset)` → `WebTarget#setQueryCharset(Charset)`. The old name implied it
  affected the whole URI, but it only configures query-string percent-encoding (Apache HC5's
  `URIBuilder#setCharset` semantics). URI path segments are always percent-encoded as UTF-8 per
  RFC 3986; if you need non-UTF-8 path encoding, percent-encode the path yourself before passing it
  to `target(...)`.

## `ObjectMapper` handling

- `HttpRequestBuilder.setDefaultJsonMapper(ObjectMapper)` and `setDefaultXmlMapper(ObjectMapper)` now
  take a defensive copy of the supplied mapper at the moment the setter is called. The library never
  mutates the caller's instance, and any mutations the caller applies to the passed mapper **after**
  the setter call are ignored (the builder uses the snapshot captured at setter time). This matters
  when the mapper is a shared bean (for example a Spring-managed singleton): previously the library
  could silently disable `FAIL_ON_UNKNOWN_PROPERTIES` and register additional modules on it.
- `addResponseDefaultDateDeserializationPattern` / `addRequestDefaultDateSerializationPattern` now
  compose correctly with `setDefaultJsonMapper` / `setDefaultXmlMapper`. Previously, providing a
  custom mapper silently discarded any date patterns; they are now installed on the snapshot.

## Retry API (breaking — `@Beta`)

`RetryContext` is `@Beta`. In 3.5.0 the three response-only overloads were **removed** and replaced
with attempt-aware overloads. A 3.4.x custom `RetryContext` will no longer compile until it is
migrated — the compile errors point directly at the methods to rewrite.

| Removed                              | Replace with                                     |
|--------------------------------------|--------------------------------------------------|
| `boolean mustBeRetried(Response)`    | `boolean mustBeRetried(RetryAttempt)`            |
| `int getRetryDelay(Response)`        | `Duration getRetryDelay(RetryAttempt)`           |
| `WebTarget beforeRetry(WebTarget)`   | `WebTarget beforeRetry(RetryAttempt, WebTarget)` |

`RetryAttempt` carries the `Response`, the `HttpMethod`, the resolved `URI`, a 1-based
`attemptNumber`, and a future-reserved `Throwable error` field.

**Behavior change** — the default `mustBeRetried(RetryAttempt)` is now *idempotency-gated*: it
only retries when `HttpMethod.isIdempotent()` is `true` (GET/HEAD/OPTIONS/PUT/DELETE/TRACE per
RFC 9110 §9.2.2). Previously the default retried any method on 503. The change intentionally
prevents duplicate-write bugs on POST/PATCH: a 5xx can be returned by a proxy while the backend
already committed, the response can be lost on the return path, or processing can fail after
partial commit. If your API supports idempotency keys and retrying POST/PATCH is safe, override
`mustBeRetried(RetryAttempt)` or use `RetryContext.onAnyMethod5xx(...)`.

### Before / after — minimal custom `RetryContext`

```java
// 3.4.x
new RetryContext() {
    @Override public int getRetryCount() { return 3; }
    @Override public boolean mustBeRetried(Response r) { return r.getCode() == 503; }
    @Override public int getRetryDelay(Response r) { return 2; }
    @Override public WebTarget beforeRetry(WebTarget t) { return t; }
};

// 3.5.0
new RetryContext() {
    @Override public int getRetryCount() { return 3; }
    @Override public boolean mustBeRetried(RetryAttempt a) {
        return a.getResponse() != null && a.getResponse().getCode() == 503;
    }
    @Override public Duration getRetryDelay(RetryAttempt a) { return Duration.ofSeconds(2); }
    @Override public WebTarget beforeRetry(RetryAttempt a, WebTarget t) { return t; }
};
```

### Prefer the bundled helpers

Two factory helpers cover the common cases:

```java
// Safe default — retries idempotent methods on any 5xx, honors Retry-After when present.
RetryContext safe = RetryContext.onIdempotent5xx(3, Duration.ofSeconds(2));

// Opt-in for non-idempotent retries; only use with idempotency-key-aware backends.
RetryContext anyMethod = RetryContext.onAnyMethod5xx(3, Duration.ofSeconds(2));
```

### Retrying requests with bodies

Before 3.5.0 any retryable request that had its `HttpEntity` initialized would fail on retry with
the generic message *"After initializing the httpEntity builder can't be copied."*

In 3.5.0 the retry transport replays requests whose entity is
{@link org.apache.hc.core5.http.HttpEntity#isRepeatable() repeatable} — which covers all built-in
body paths: `rawRequest(POST, String)` and `request(POST, String)` (wrap in `StringEntity`),
`post(Object)` (wrap in a `StringEntity` via Jackson), plus any `ByteArrayEntity` / `FileEntity`
the caller constructs directly. No action needed for these cases; retries just work.

Non-repeatable entities (`InputStreamEntity` and similar one-shot streaming sources) still cannot
be replayed. Attempting to retry one now fails with an actionable error that names the cause and
points at the fix:

> Cannot copy request builder: the HttpEntity is non-repeatable (e.g. InputStreamEntity) and cannot
> be re-sent on retry. Wrap the body in a repeatable entity such as StringEntity, ByteArrayEntity,
> or FileEntity.

A `RetryContext#withEntityFactory(Supplier<HttpEntity>)` hook for streaming bodies may be added in
a later release on demand.

### Default response-charset fallback is now UTF-8

When a server returns a response without a `charset` parameter on the `Content-Type` header,
Apache HC5's default behavior is to decode the body as ISO-8859-1. The library now defaults that
fallback to UTF-8 — the right choice in 2026 and consistent with how every modern HTTP client
behaves. If the server's `Content-Type` does carry a `charset=...`, that always wins regardless
of this setting.

If you talk to a legacy server that emits ISO-8859-1 bytes without an explicit charset header,
restore the old behavior with:

```java
HttpRequestBuilder.create(httpClient)
        .

setDefaultResponseCharset(StandardCharsets.ISO_8859_1)
        .

build();
```

### Connection pool default (`defaultMaxPoolSizePerRoute`)

The default per-route cap on `ClientBuilder` was lowered from `128` to `32`. The total pool cap is
unchanged at `128`. Rationale: when `defaultMaxPoolSizePerRoute == maxPoolSize` a single hot host
can saturate the entire pool, leaving parallel requests to other hosts blocked on
`connectionRequestTimeout` — the new ratio (`total / 4`) preserves multi-host fairness while keeping
typical microservice workloads well within budget. Industry conventions land in the same range
(Apache HC `200 / 50` in Spring's typical config, AWS SDK `50 / per-service`, etc.).

If your workload genuinely talks to a single upstream and was implicitly relying on the old `128`
per-route cap, restore it explicitly:

```java
CloseableHttpClient client = ClientBuilder.create()
        .setDefaultMaxPoolSizePerRoute(128)
        .build();
```

Or use the per-route override for hot hosts only:

```java
CloseableHttpClient client = ClientBuilder.create()
        .setMaxPoolSizePerRoute(new HttpHost("hot.example.com"), 96)
        .build();
```