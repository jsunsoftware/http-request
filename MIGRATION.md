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
repeatable (`HttpEntity#isRepeatable()`) — which covers all built-in
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
        .setDefaultResponseCharset(StandardCharsets.ISO_8859_1)
        .build();
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
        .setMaxPoolSizePerRoute(new HttpHost("https", "hot.example.com", 443), 96)
        .build();
```

Before 4.1.0 a per-route cap only took effect for a plain `http` host with an explicit port and no proxy — see
[Connection pool](#connection-pool) under 4.1.0.

# 4.0.0

## Java baseline raised to Java 17

The library now targets Java 17 bytecode at both compile time and runtime; the build enforcer
rejects JDKs below 17. Consumers still on Java 8 must stay on the `3.6.x` line — that branch
remains the supported Java 8 baseline.

No public API was renamed, removed, or re-typed in this release: source built against `3.6.x`
recompiles unchanged against `4.0.0` once the JDK requirement is met.

## Published as a JPMS module

The artifact is now a named JPMS module `com.jsunsoft.http`. Consumers on the **classpath** see
no difference — the JAR works exactly as before. Consumers using the **modulepath** can now
write `requires com.jsunsoft.http;` in their own `module-info.java` instead of relying on the
automatic module name derived from the artifact id.

Public packages: `com.jsunsoft.http` and `com.jsunsoft.http.annotations`. Apache HttpClient 5
(client + core) and Jackson `databind` / `dataformat-xml` are declared `requires transitive`,
so consumers automatically see the public-API types from those modules (`CloseableHttpClient`,
`Header`, `ObjectMapper`, `XmlMapper`, etc.) without restating the requires themselves.

## Reflective frameworks and your own POJOs

Strong encapsulation does **not** apply to consumer code on the classpath. If you run on the
modulepath and your consumer code uses Jackson, Gson, JAXB, Hibernate, or another reflective
framework on POJOs that you own, the same `opens` rules that apply to all JPMS modules apply to
your consumer module too — that is unchanged by this release and has nothing to do with this
library. You typically need `opens your.app.dto;` in your own `module-info.java`.


# 4.1.0

4.1.0 backports the fixes of 5.0.0 to the Jackson 2 line. No API was removed, but several fixes change behavior you
may depend on.

## Failure classification

A few failures are reported differently. The codes are `ResponseHandler#getCode()` values:

| Failure                                                         | 4.0.0                                                   | 4.1.0                           |
|-----------------------------------------------------------------|---------------------------------------------------------|---------------------------------|
| Malformed or type-mismatched JSON / XML body                    | 503                                                     | 502                             |
| Jackson parse error in a custom `ResponseBodyReader`            | 503                                                     | 502                             |
| Body doesn't match its `Content-Encoding` (e.g. corrupt gzip)   | 503                                                     | 502                             |
| Connection breaks while the body is read                        | 503                                                     | 503 (unchanged)                 |
| Body exceeds `setMaxResponseBodySizeBytes`                      | 502                                                     | 502 (unchanged)                 |
| Calling thread interrupted                                      | 503, `IllegalStateException` or `CancellationException` | `RequestException` thrown       |
| `IOException` while closing the response                        | 500, read result discarded                              | read result kept, WARN logged   |

So a 503 the library synthesizes now always means the connection failed, and a synthesized 502 means the upstream
answered with something unusable. If you alert or branch on these codes, review that logic. To tell a status the
library synthesized from one the server sent, compare `getOriginalCode()` with `getCode()`: they differ when the
library replaced the server's status, and `getOriginalCode()` is `-1` when no response was received at all (a
refused connection, a timeout).

Your own `ResponseBodyReader` gets the same treatment: a `JsonProcessingException` or `CharConversionException` it
throws now becomes a 502 `ResponseHandler`, or a `ResponseBodyProcessingException` from `Response#readEntity`, unless
it was caused by a failure of the stream (a broken connection, the size cap, an interrupt), which keeps its own
classification.

## Interrupted calls throw `RequestException`

When the calling thread is interrupted — while waiting for a pooled connection, between retries, or (on a virtual
thread) while reading the response body — both APIs now throw `RequestException` and leave the interrupt flag set.
`RequestException` is not a `ResponseException`, so an interrupt now escapes `catch (ResponseException e)`, and the
eager `get(User.class)` throws instead of returning a 503 handler. Treat it as the signal to stop the work, or catch
`RequestException` next to `ResponseException`.

## Connection pool

- `setMaxPoolSize`, `setDefaultMaxPoolSizePerRoute` and `setMaxPoolSizePerRoute` reject values below 1 with
  `IllegalArgumentException`, and a `null` host with `NullPointerException`. Such values used to be ignored silently
  in favor of Apache HC5's defaults (25 total / 5 per route), so a `build()` that now fails points at a setting that
  never had an effect.
- `setMaxPoolSizePerRoute` caps now take effect for https hosts and behind a configured proxy, where they used to be
  ignored silently. Review caps you set earlier: they may limit traffic for the first time. The scheme matters — an
  `HttpHost` without a scheme means `http`, so cap an https host with `new HttpHost("https", "api.example.com", 443)`
  (a port of `-1` means the scheme's default port).

## SSRF guard

- `disallowPrivateAndLoopbackHosts()` now blocks the whole IANA non-global address space, not only loopback,
  link-local, RFC 1918 and IPv6 unique-local addresses — among others carrier-grade NAT `100.64.0.0/10` (used by
  Tailscale, for example), `0.0.0.0/8`, `198.18.0.0/15`, the TEST-NETs, `240.0.0.0/4` and multicast. If an internal
  endpoint you call lives in one of these ranges, allow it with the `disallowPrivateAndLoopbackHosts(Predicate)`
  overload.
- The guard now takes precedence over a `setDnsResolver(...)` call in a
  `addDefaultConnectionManagerBuilderCustomizer(...)`; such a customizer used to disable the guard silently. A custom
  resolver combined with the guard is not supported — use the allow-list overload.

## Request payload logging

With `enableRequestPayloadLogging()`, `Object` payloads (`post(Object)`, `rawRequest(method, Object)`, …) are now
logged too, in their serialized form, after your `addPayloadRedactor(...)` redactors; before 4.1.0 only `String`
payloads were. If you turned payload logging on relying on that gap, register a redactor or turn it off.

## Request body charset

A `charset` declared on the request's `Content-Type` header now decides how `String` and `Object` payloads are
encoded; `setBodyCharset`, `setCharset` and the builder's `setDefaultBodyCharset` apply only when the header declares
none. If your code sets both and they disagree, the body bytes change with this release — they now match the header.

## Size-capped responses

With `setMaxResponseBodySizeBytes(...)` set, closing a response whose body wasn't read to the end no longer reads the
whole remainder: it drains at most 64 KiB within 100 ms and otherwise discards the connection. Expect a few more new
connections if you often stop reading capped responses early; `setMaxResponseDrainSizeBytes` and
`setResponseDrainTimeout` tune this (see the README). A response whose declared `Content-Length` exceeds the cap now
fails before any byte is read. Without a cap nothing changes.

Jackson 2 stops reading a JSON body at the end of its value. With a cap, closing the stream no longer reaches the
connection, so a JSON body read through `Response#readEntity` releases its connection when the `Response` is
closed, not as soon as it is parsed — keep closing every `Response`, ideally with try-with-resources.

## Smaller behavior changes

- `WebTarget#getURI()` / `getURIString()` now include the parameters added with `addParameter(s)`. Code that appended
  them itself (for logging or request signing, say) now gets them twice.
- `FilterSupport.ifNotPassed(...).otherwise(...)` now runs the `otherwise` action only when every filter passed (it
  used to run together with the `ifNotPassed` action). Remove workarounds for the old behavior.
- `ImmutableWebTarget` mutators reject `null` with `NullPointerException`, like `target(...)`, and a `null` charset
  means UTF-8.
- A `RetryContext#beforeRetry` that returns `null` now fails with `NullPointerException`.
- Headers added to a `ClientBuilder` after `build()` no longer reach clients built earlier.
- `UnexpectedResponseException` is no longer deprecated: it is the common base type of
  `UnexpectedStatusCodeException` and `MissingResponseBodyException`, so keep catching it to handle both.
