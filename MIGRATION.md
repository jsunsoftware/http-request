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

# 5.0.0-rc1

## Jackson 2.x → 3.x upgrade

`http-request 5.0.0-rc1` depends on **Jackson 3.1.x** (`tools.jackson.*`). The `jackson-databind`
and `jackson-dataformat-xml` Maven groupIds change from `com.fasterxml.jackson.{core,dataformat}`
to `tools.jackson.{core,dataformat}`; the JPMS module names change correspondingly
(`tools.jackson.databind`, `tools.jackson.core`, `tools.jackson.dataformat.xml`). `jackson-annotations`
stays on `com.fasterxml.jackson.core:jackson-annotations` (2.21) — the annotations artifact is
intentionally shared between Jackson 2.x and 3.x consumers.

If your code references the library's default mapper or imports `ObjectMapper` / `XmlMapper` via
this library, update your imports from `com.fasterxml.jackson.databind.ObjectMapper` to
`tools.jackson.databind.ObjectMapper`, and from `com.fasterxml.jackson.dataformat.xml.XmlMapper`
to `tools.jackson.dataformat.xml.XmlMapper`. The same `com.fasterxml.jackson.*` → `tools.jackson.*`
rewrite applies to features (`SerializationFeature`, `DeserializationFeature`, …), the streaming
core (`JsonParser`, `JsonFactory`, …), and the type-token (`tools.jackson.core.type.TypeReference`).
Annotations (`@JsonProperty`, `@JsonInclude`, `@JsonFormat`, `@JsonRootName`, …) keep their old
package and require no source change.

### Built-in modules — drop your own registrations

`jackson-module-parameter-names`, `jackson-datatype-jdk8`, and `jackson-datatype-jsr310` are
folded into `jackson-databind` in Jackson 3. Their standalone artifacts no longer exist as
separate dependencies and their module classes (`ParameterNamesModule`, `Jdk8Module`,
`JavaTimeModule`) are unnecessary — remove any `registerModule(...)` calls for them. The
`java.time.*` types, `Optional<T>` etc., and parameter-name based constructor binding all work
out of the box.

### Mutable → immutable mapper

Jackson 3 mappers are immutable. Configuration goes through a builder
(`JsonMapper.builder().…build()`, `XmlMapper.builder().…build()`); a built mapper cannot be
reconfigured. Practical consequences if you pass a custom mapper into the library:

- `ObjectMapper#copy()` is gone — use `mapper.rebuild().build()` to clone with overrides.
- `registerModule(...)`, `setSerializationInclusion(...)`, `setDefaultPropertyInclusion(...)`,
  `configOverride(...)` on a *built* mapper are all removed; they exist only on the builder
  (`addModule(...)`, `changeDefaultPropertyInclusion(...)`, `withConfigOverride(...)`).
- `HttpRequestBuilder.setDefaultJsonMapper(...)` / `setDefaultXmlMapper(...)` no longer take a
  defensive `.copy()` of the supplied mapper. The mapper is immutable, so defensive copying is
  no longer meaningful — the library stores the reference directly and `rebuild()`-s a fresh
  derivative whenever a per-config date pattern is registered.

### Exceptions are unchecked

Jackson 3 made `JacksonException` extend `RuntimeException` (it was `IOException` in 2.x). The
library's body reader continues to surface deserialization failures the same way callers always
saw them: parse / mapping failures land as `ResponseBodyReaderException` (→ HTTP 502 Bad
Gateway on `ResponseHandler`), and stream-level IO failures (including the library's own
`InvalidContentLengthException`) propagate as `IOException` and end up on the same routing path
they did pre-5.0.0. If you've written a custom `ResponseBodyReader<T>` that calls Jackson
directly, you may now have unreachable `catch (JsonProcessingException e)` blocks — those
exceptions are unchecked in Jackson 3 and the `throws` clauses can be removed.

### Default-toggle changes you may notice

Jackson 3 silently flipped several feature defaults. The library deliberately accepts the 3.x
baseline rather than restoring the old 2.x toggles via `builderWithJackson2Defaults()` —
absorbing the new defaults is the principled move for a major-version bump, and most of the
changes are strict/safe wins. The user-visible deltas:

- **`FAIL_ON_TRAILING_TOKENS`** — now ON. The parser throws on content after the parsed value
  instead of silently ignoring it. If you've been deserializing concatenated JSON documents
  (`{"a":1}{"b":2}`) into a single call, you'll need to `.disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)`
  on a custom mapper.
- **`FAIL_ON_NULL_FOR_PRIMITIVES`** — now ON. JSON `null` mapped to a primitive field now
  throws instead of silently writing `0` / `false`. If a server returns `{"count": null}` for
  an `int count` field, that field must change to `Integer` or you must disable the feature.
- **`READ_ENUMS_USING_TO_STRING` / `WRITE_ENUMS_USING_TO_STRING`** — both now ON. Enums
  (de)serialize via `toString()` instead of `name()`. If your enums override `toString()`,
  the serialized JSON value changes. Disable on a custom mapper, or remove the `toString()`
  override, or annotate with `@JsonProperty` per constant.
- **`SORT_PROPERTIES_ALPHABETICALLY`** — now ON. Serialized JSON property order is now
  alphabetical by default. Most consumers (and HTTP servers) are order-insensitive, but
  golden-file tests and request log diffs will change.
- **`USE_GETTERS_AS_SETTERS`** — now OFF. Collection-typed getters with no setter are no
  longer auto-used to populate the collection. If you've been relying on this pattern,
  re-enable on a custom mapper or add an explicit setter.
- **`ALLOW_FINAL_FIELDS_AS_MUTATORS`** — now OFF. Final fields are no longer auto-discovered
  as mutators; configure a constructor or factory for those classes.
- **`DEFAULT_VIEW_INCLUSION`** — now OFF. Properties without a `@JsonView` annotation are no
  longer included by default in any view.

The library's own explicit override is still applied on top of the 3.x baseline: the default
mapper has `NON_NULL` value-inclusion *and* `NON_NULL` content-inclusion. (The other 4.0.0
library overrides — `FAIL_ON_EMPTY_BEANS` off, `FAIL_ON_UNKNOWN_PROPERTIES` off,
`WRITE_DATES_AS_TIMESTAMPS` off — happen to already be the Jackson 3 defaults, so they no
longer need explicit toggles.)

### Single-argument constructor binding

Jackson 2.x had no built-in single-arg constructor mode default — earlier library versions
registered `new ParameterNamesModule(JsonCreator.Mode.PROPERTIES)`, which forced every
auto-detected single-arg constructor into PROPERTIES mode. Jackson 3 has a built-in
`ConstructorDetector` with a `HEURISTIC` default: if the bean exposes a property whose name
matches the constructor parameter, the constructor is treated as PROPERTIES-based; otherwise
it falls back to DELEGATING. For ordinary REST DTOs (`class Foo { Foo(String name); String getName(); }`)
the two modes are observationally identical. The divergence is for "opaque wrapper" DTOs with
no getter or field matching the parameter name — e.g.:

```java
public final class TraceId {
  private final String raw;

  public TraceId(String raw) {
    this.raw = raw;
  }

  @Override
  public String toString() {
    return raw;
  }
  // no getRaw()
}
```

Under the old library mode (`PROPERTIES`) Jackson required `{"raw": "abc"}` to deserialize this
type. Under the Jackson 3 `HEURISTIC` default, Jackson accepts the raw string `"abc"` instead
(DELEGATING). If your code depends on the old `{"raw": "abc"}` shape for wrappers like this,
register your own mapper with
`JsonMapper.builder().constructorDetector(ConstructorDetector.USE_PROPERTIES_BASED).build()`
and pass it via `HttpRequestBuilder.setDefaultJsonMapper(...)`, or annotate the constructor
with `@JsonCreator(mode = JsonCreator.Mode.PROPERTIES)`.