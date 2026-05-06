A powerful, fluent Java wrapper built on top of Apache HTTP Client 5 that simplifies making REST API calls.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Resource Management](#resource-management)
- [Usage Examples](#usage-examples)
  - [Creating an HttpRequest](#creating-an-httprequest)
  - [Making Simple Requests](#making-simple-requests)
  - [Working with Request Parameters](#working-with-request-parameters)
  - [Working with Headers](#working-with-headers)
  - [Handling Responses](#handling-responses)
  - [Error Handling](#error-handling)
  - [Working with JSON and XML](#working-with-json-and-xml)
- [Advanced Features](#advanced-features)
  - [Connection Pooling](#connection-pooling)
  - [Timeouts](#timeouts)
  - [SSL Configuration](#ssl-configuration)
  - [Authentication](#authentication)
  - [Redirects](#redirects)
  - [Retry Mechanism](#retry-mechanism)
  - [Custom Response Body Readers](#custom-response-body-readers)
  - [Limiting Response Body Size](#limiting-response-body-size)
  - [Character Encoding](#character-encoding)
  - [Security &amp; Hardening](#security--hardening)
  - [Debugging](#debugging)
- [Why use http-request?](#why-use-http-request)
- [API Documentation](#api-documentation)
- [Contributing](#contributing)
- [License](#license)

## Overview

The http-request library is designed to create a simple REST client quickly, manage responses easily, and handle
exceptions gracefully. It provides a fluent API that makes HTTP requests more readable and maintainable.

The main purpose is to simplify the process of making HTTP requests while providing powerful features for handling
responses, managing connections, and processing data, building on top of the robust and performant Apache HttpClient 5.

## Features

- **Fluent API**: Build and execute HTTP requests with a clean, chainable API.
- **Thread-safe**: `HttpRequest` objects are immutable and thread-safe after building.
- **Simplified Error Handling**: All exceptions are wrapped with meaningful status codes.
- **Simplified Resource Management**: Automatic closing of responses with `ResponseHandler` and `AutoCloseable` client
  resources.
- **Flexible Response Processing**:
  - Convert responses to Java objects automatically.
  - Support for JSON and XML deserialization.
  - Type-safe generic response handling with `TypeReference`.
- **Connection Management**:
  - Simplified connection pooling.
  - Configurable timeouts.
  - Easy SSL configuration.
- **Advanced Features**:
  - Flexible retry mechanism for failed requests.
  - Custom response body readers and request body converters.
  - Secure password handling for basic authentication.
  - Request duration monitoring.

## Requirements

- Java 8 or higher
- Dependencies (automatically managed by build tools):
  - Apache HttpClient 5.x
  - Jackson (for JSON/XML processing)
  - SLF4J (for logging)

### Transport: HTTP/1.1 only

The library is built on Apache HC5's classic synchronous `CloseableHttpClient`, which speaks
HTTP/1.1. HTTP/2 / HTTP/3 are not supported through this library — those require Apache HC5's
async client (`CloseableHttpAsyncClient`), which is not yet wired in. If your service needs
HTTP/2 multiplexing or h2-only upstreams, choose a different client. Sync HTTP/1.1 is the
right answer for the vast majority of REST APIs.

## Installation

### Maven

Add this dependency to your `pom.xml`:

```xml

<dependency>
  <groupId>com.jsunsoft.http</groupId>
  <artifactId>http-request</artifactId>
  <version>3.5.0-rc3</version>
</dependency>
```

### Gradle

Add this dependency to your `build.gradle`:

```groovy
implementation 'com.jsunsoft.http:http-request:3.5.0-rc3'
```

## Quick Start

Here's a simple example to get you started:

```java
import com.jsunsoft.http.*;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

public class QuickStart {
  public static void main(String[] args) {
    // Create an HTTP client and manage its resources with try-with-resources
    try (CloseableHttpClient httpClient = ClientBuilder.create().build()) {

      // Build an HTTP request
      HttpRequest httpRequest = HttpRequestBuilder.create(httpClient).build();

      // Make a GET request and get the response
      try (Response response = httpRequest.target("https://api.example.com/users/1").get()) {
        // Check the status code
        int statusCode = response.getCode();
        System.out.println("Status code: " + statusCode);

        if (response.isSuccess()) {
          // Read the response as a string
          String responseBody = response.readEntity(String.class);
          System.out.println("Response body: " + responseBody);
        }
      }
    }
  }
}
```

## Resource Management

This library simplifies resource management to prevent connection leaks.

### Concurrency and Thread Safety

- **HttpRequest**: immutable and thread-safe after building; reuse it across threads.
- **WebTarget from `target(...)`**: mutable and **not** thread-safe. Build it, configure it, and
  fire its request from the *same* thread — typically all in one fluent expression. Do not store
  a configured `target(...)` instance in a field that multiple threads write to.
- **WebTarget from `immutableTarget(...)`**: safe to share and reuse across threads. Each
  fluent call returns a *new* instance, so concurrent threads each see their own snapshot.

The mutable `target(...)` is faster (no allocation per fluent step) but mutations on it leak
across threads if the same instance is reused.

#### Choosing between `target()` and `immutableTarget()`

A simple rule covers ~99% of cases:

| Use case                                                                | Pick                   |
|-------------------------------------------------------------------------|------------------------|
| One-shot request from a single thread (`build → fire → discard`)        | `target(uri)`          |
| Configured once, fired from many threads (e.g. a service-bean field)    | `immutableTarget(uri)` |
| Forking variants of the same configuration (auth + tenant + then split) | `immutableTarget(uri)` |
| Tight loops where allocation of a per-call wrapper matters              | `target(uri)`          |

```java
// Request-scoped: each call builds and discards.
ResponseHandler<User> rh = httpRequest.target("https://api.example.com/users/1")
                .addHeader("X-Tenant", currentTenantId())
                .get(User.class);

// Shared / multi-threaded: every fluent call returns a *new* WebTarget,
// so two threads adding different headers don't trample each other.
private final WebTarget userApi = httpRequest.immutableTarget("https://api.example.com/users");

User loadUser(String id, String tenant) {
  return userApi.path(id).addHeader("X-Tenant", tenant).get(User.class).orElseThrow();
}
```

If you're not sure, default to `immutableTarget(...)` — the per-call allocation cost is
negligible compared to the network round-trip, and the thread-safety footgun is gone.

### 1. Using `ResponseHandler` (Recommended for most cases)

When you use methods that return a `ResponseHandler<T>` (e.g., `get(User.class)`), the response is consumed and closed
automatically. This is the most convenient and safest way to handle responses.

```java
// The response is closed automatically after the operation.
User user = httpRequest.target("https://api.example.com/users/1")
                .get(User.class)
                .orElseThrow();
```

### 2. Using `Response` with try-with-resources

When you need direct access to the `Response` object, always use a try-with-resources statement to ensure it's closed,
even if exceptions occur.

```java
try(Response response = httpRequest.target(uri).get()){
    int statusCode = response.getCode();
    SomeType body = response.readEntity(SomeType.class);
} // response.close() is called automatically
```

### 3. Managing the `HttpClient`

`CloseableHttpClient` manages the connection pool and should be shared across your application. It's a heavy-weight
object. It's crucial to close it when your application shuts down.

```java
// Use try-with-resources to manage the client lifecycle
try(CloseableHttpClient httpClient = ClientBuilder.create().build()){
        // ... use the client
}
```

## Usage Examples

### Creating an `HttpRequest`

The `HttpRequestBuilder` is used to create and configure `HttpRequest` instances. `HttpRequest` objects are immutable
and thread-safe, so you can create one and reuse it.

```java
// Create a simple HttpRequest
HttpRequest httpRequest = HttpRequestBuilder.create(httpClient).build();

// Create an HttpRequest with default headers and content type
HttpRequest httpRequest = HttpRequestBuilder.create(httpClient)
        .addDefaultHeader("User-Agent", "MyApp/1.0")
        .addContentType(ContentType.APPLICATION_JSON)
        .build();

// Create an HttpRequest with basic authentication
HttpRequest httpRequest = HttpRequestBuilder.create(httpClient)
        .basicAuth("username", "password")
        .build();
```

### Making Simple Requests

The library provides convenient methods for all standard HTTP verbs.

- **`get()`**, **`post()`**, **`put()`**, etc., returning a `ResponseHandler<T>` (eager processing): The response is
  automatically read and converted.
- **`get()`**, **`post()`**, **`put()`**, etc., returning a `Response` (lazy processing): You have direct control over
  the response stream.

```java
// Eager GET: Convert response body to a User object
ResponseHandler<User> userHandler = httpRequest.target("https://api.example.com/users/1").get(User.class);
User user = userHandler.orElseThrow(); // Throws exception if request was not successful

// Eager POST with a JSON payload
String jsonPayload = "{\"name\":\"John\",\"email\":\"john@example.com\"}";
ResponseHandler<String> postHandler = httpRequest.target("https://api.example.com/users").post(jsonPayload, String.class);
System.out.println("Response: " + postHandler.get());

// Lazy PUT: Get the Response object and handle it manually
try(
    Response putResponse = httpRequest.target("https://api.example.com/users/1").put(jsonPayload)){
            System.out.println("Status code: " + putResponse.getCode());
}
```

### Working with Request Parameters

You can add query parameters to your requests easily.

```java
// Add parameters
Response response = httpRequest.target("https://api.example.com/users")
                .addParameter("page", "1")
                .addParameter("limit", "10")
                .get();

// Add multiple parameters from a map
Map<String, String> params = new HashMap<>();
params.put("limit","10");
params.put("sort","name");

Response response = httpRequest.target("https://api.example.com/users")
        .addParameters(params)
        .get();

// Add parameters from a query string
Response response = httpRequest.target("https://api.example.com/users")
        .addParameters("page=1&limit=10&sort=name")
        .get();
```

### Working with Headers

You can add headers to individual requests or set default headers on the `HttpRequestBuilder`.

```java
// Add a header to a single request
Response response = httpRequest.target("https://api.example.com/users")
                .addHeader("X-API-Key", "your-api-key")
                .get();

// Add multiple headers
Response response = httpRequest.target("https://api.example.com/users")
        .addHeader("X-API-Key", "your-api-key")
        .addHeader("Accept-Language", "en-US")
        .get();

// Update or remove headers
Response response = httpRequest.target("https://api.example.com/users")
        .addHeader("Accept", "application/json")
        .updateHeader("Accept", "application/xml")
        .removeHeader("X-Temporary")
        .get();

// Set a default header for all requests made with this httpRequest instance
HttpRequest httpRequestWithAuth = HttpRequestBuilder.create(httpClient)
        .addDefaultHeader("Authorization", "Bearer your-token")
        .build();
```

### Handling Responses

The `ResponseHandler` provides a powerful and fluent way to process responses.

```java
ResponseHandler<User> handler = httpRequest.target("https://api.example.com/users/123").get(User.class);

// Chain success and error handling
handler.ifSuccess(h ->System.out.println("User: "+h.get().getName()))
        .otherwise(h ->System.err.println("Error: "+h.getErrorText()));

// Using filter and conditional processing
handler.filter(ResponseHandler::hasContent).ifPassed(h ->System.out.println("User: "+h.get().getName()))
        .otherwise(h ->System.out.println("No content"));

// Get the result or a default value
User user = handler.orElse(new User("Default"));

// Get the request duration
Duration duration = handler.getDuration();
System.out.println("Request took: "+duration.toMillis() +"ms");

// Status codes: mapped vs original (ResponseHandler only)
int code = handler.getCode(); // May be mapped for failures (e.g., 503/502)
int originalCode = handler.getOriginalCode(); // -1 if no response was received
```

### Error Handling

The library wraps exceptions and provides convenient ways to handle errors.

- **Connection failures** → `RequestException` (often with status code 503)
- **Deserialization failures** → `ResponseBodyProcessingException` (often with status code 502)
- **Non-2xx status codes** → `UnexpectedStatusCodeException`

```java
try{
// Throws UnexpectedStatusCodeException for non-2xx responses
// Throws MissingResponseBodyException if the body is empty but required
User user = httpRequest.target("https://api.example.com/users/999")
        .get(User.class)
        .requiredGet();
}catch(ResponseException e){
        System.err.println("Response failed: " + e.getMessage());
        
        if(e instanceof UnexpectedStatusCodeException){
            System.err.println("Status code: " + ((UnexpectedStatusCodeException) e).getStatusCode());
        }
}
```

### Working with JSON and XML

The library automatically handles JSON and XML serialization/deserialization using Jackson.

```java
// POST a Java object as JSON
User newUser = new User("John Doe", "john@example.com");
Response response = httpRequest.target("https://api.example.com/users")
        .post(newUser); // Content-Type is automatically handled for Objects

// GET and parse a generic type (List<User>)
List<User> users = httpRequest.target("https://api.example.com/users")
        .get(new TypeReference<List<User>>() {
        })
        .orElseThrow();

// Custom date formats for JSON/XML
HttpRequest httpRequest = HttpRequestBuilder.create(httpClient)
        .addResponseDefaultDateDeserializationPattern(LocalDate.class, "yyyy-MM-dd")
        .addResponseDefaultDateDeserializationPattern(LocalDateTime.class, "yyyy-MM-dd HH:mm:ss")
        .addRequestDefaultDateSerializationPattern(LocalDateTime.class, "yyyy-MM-dd'T'HH:mm:ss")
        .build();
```

Date patterns also compose cleanly with a user-supplied `ObjectMapper`:

```java
// Your shared application ObjectMapper (e.g. a Spring bean) is used as-is…
ObjectMapper appMapper = /* injected from your app */;

HttpRequest httpRequest = HttpRequestBuilder.create(httpClient)
        .setDefaultJsonMapper(appMapper)
        // …and the date patterns are installed on a defensive copy of it; `appMapper` itself is not mutated.
        .addResponseDefaultDateDeserializationPattern(LocalDate.class, "yyyy-MM-dd")
        .build();
```

#### Strict vs. lenient deserialization

When you don't supply an `ObjectMapper` of your own, the library default disables Jackson's
`FAIL_ON_UNKNOWN_PROPERTIES` — unknown JSON fields are silently dropped. This favours
forward-compatibility (the server can roll out new fields without breaking existing clients) but
it also masks typos in field names and silent API drift while you're developing. To opt into
the stricter Jackson default, supply your own `ObjectMapper`:

```java
HttpRequest httpRequest = HttpRequestBuilder.create(httpClient)
        // A bare new ObjectMapper() inherits Jackson's default — strict on unknowns.
        .setDefaultJsonMapper(new ObjectMapper())
        .build();
```

The library never mutates your supplied mapper, so its `FAIL_ON_UNKNOWN_PROPERTIES`,
`FAIL_ON_NULL_FOR_PRIMITIVES`, registered modules, etc. all flow through unchanged.

```

## Advanced Features

### Connection Pooling

Configure the connection pool using `ClientBuilder`.

```java
CloseableHttpClient httpClient = ClientBuilder.create()
        .setMaxPoolSize(200) // Total connections
        .setDefaultMaxPoolSizePerRoute(50) // Connections per host
        .setMaxPoolSizePerRoute("api.example.com", 100) // Specific host
        .build();
```

By default, raw Apache HttpClient uses small pool limits (for example, 2 per route). `ClientBuilder` raises
these to **128 total / 32 per route** — sized for typical microservice workloads that talk to a handful of
upstream hosts. The `total / perRoute` ratio of roughly 4× preserves multi-host fairness: a hot route can't
saturate the entire pool and starve traffic to other hosts. Override either knob based on your traffic
pattern (a high-throughput aggregator hitting many hosts at once will want a higher total; a service that
talks to a single upstream may want to raise per-route to match).

You can also configure a proxy:

```java
CloseableHttpClient httpClient = ClientBuilder.create()
        .proxy("proxy.mycorp.local", 8080)
        .build();
```

Or customize the underlying `HttpClientBuilder`:

```java
CloseableHttpClient httpClient = ClientBuilder.create()
        .addHttpClientCustomizer(builder ->
                builder.setKeepAliveStrategy((response, context) -> /* your strategy */ 30_000))
        .build();
```

### Timeouts

Configure various timeouts for your HTTP client.

```java
CloseableHttpClient httpClient = ClientBuilder.create()
        .setConnectTimeout(5000)               // Connection timeout in ms
        .setResponseTimeout(30000)             // Response timeout in ms
        .setConnectionRequestTimeout(30000)    // Time to wait for a pooled connection
        .setSocketTimeout(30000)               // Socket timeout in ms
        .build();
```

Default timeouts used by `ClientBuilder`:

- Response timeout: 30000ms
- Connection request timeout: 30000ms
- Connect timeout: 10000ms
- Socket timeout: 30000ms

Timeouts can be overridden per request:

```java
HttpRequest httpRequest = HttpRequestBuilder.create(httpClient).build();

httpRequest.target(uri)
        .setRequestConfig(customRequestConfig)
        .get();
```

### SSL Configuration

Easily configure SSL settings.

```java
// Trust all certificates (INSECURE: for testing only)
CloseableHttpClient httpClient = ClientBuilder.create()
                .trustAllCertificates()
                .trustAllHosts()
                .build();
```

#### TLS version and cipher pinning

By default the JDK negotiates the best mutually supported TLS version. For hardened
deployments (PCI-DSS, FedRAMP, internal compliance) you may need to enforce TLS 1.2+ and
opt out of legacy cipher suites:

```java
CloseableHttpClient httpClient = ClientBuilder.create()
        // Reject TLS 1.0 / 1.1 — server must speak 1.2 or 1.3.
        .setTlsVersions("TLSv1.3", "TLSv1.2")
        // Restrict to a hand-picked cipher allow-list (subset of the JDK's supported set).
        .setCipherSuites(
                "TLS_AES_256_GCM_SHA384",
                "TLS_AES_128_GCM_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
        .build();
```

If neither setter is called, the JDK defaults apply (which already exclude TLS 1.0 / 1.1 on
modern JVMs). The values are passed straight through to Apache HC5's `ClientTlsStrategyBuilder`
— a name not supported by the JVM will surface as a TLS handshake failure at first request.

### Authentication

The library supports basic authentication securely.

```java
// Securely provide password as a char array (will be cleared after use)
HttpRequest httpRequest = HttpRequestBuilder.create(httpClient)
                .basicAuth("user", new char[]{'s', 'e', 'c', 'r', 'e', 't'})
                .build();
```

### Redirects

Configure how redirects are handled. By default, they are disabled.

```java
// Enable a standard redirect strategy
CloseableHttpClient httpClient = ClientBuilder.create()
                .enableDefaultRedirectStrategy()
                .build();

// Enable lax redirect strategy
CloseableHttpClient httpClient = ClientBuilder.create()
        .enableLaxRedirectStrategy()
        .build();

// Set a custom redirect strategy
CloseableHttpClient httpClient = ClientBuilder.create()
        .setRedirectStrategy(customRedirectStrategy)
        .build();
```

### Retry Mechanism

The library offers two ways to handle retries:

#### 1. `RetryableWebTarget` (Recommended)

Use one of the bundled policies, or implement `RetryContext` for full control.

```java
// Safe default: retry idempotent methods (GET/HEAD/OPTIONS/PUT/DELETE/TRACE) on any 5xx up to 3
// times with a 2-second delay. Honors the Retry-After response header when present.
RetryContext retryContext = RetryContext.onIdempotent5xx(3, Duration.ofSeconds(2));

Response response = httpRequest.retryableTarget("https://api.example.com/status/503", retryContext)
        .get();
```

> **Why idempotent-only by default?** Retrying a `POST` on a 5xx response is unsafe in general —
> the 5xx may have been returned by a proxy while the backend already committed the change, the
> response may have been lost on the return path, or processing may have failed after a partial
> write. Retrying blindly can create duplicate resources. The default policy refuses to retry
> `POST` and `PATCH` for this reason.

If your API is designed around idempotency keys (e.g. Stripe-style `Idempotency-Key` headers) and
retrying `POST`/`PATCH` on 5xx is genuinely safe, opt in explicitly:

```java
RetryContext retryContext = RetryContext.onAnyMethod5xx(3, Duration.ofSeconds(2));
```

For fine-grained policies, implement `RetryContext` directly. The attempt passed to the predicate
carries the response, the HTTP method, the URI, and a 1-based attempt number:

```java
RetryContext custom = new RetryContext() {
    @Override public int getRetryCount() { return 3; }

    @Override
    public boolean mustBeRetried(RetryAttempt attempt) {
        if (attempt.getResponse() == null) return false;
        // Only retry GET on 5xx, and only up to attempt 3 total (1 original + 2 retries).
        return attempt.getMethod() == HttpMethod.GET
                && attempt.getResponse().getCode() >= 500
                && attempt.getAttemptNumber() < 3;
    }

    @Override
    public Duration getRetryDelay(RetryAttempt attempt) {
        // Exponential backoff: 200ms, 400ms, 800ms, ...
        return Duration.ofMillis(100L << attempt.getAttemptNumber());
    }

    @Override
    public WebTarget beforeRetry(RetryAttempt attempt, WebTarget webTarget) {
        // E.g. rotate auth token before the next attempt.
        return webTarget.updateHeader(HttpHeaders.AUTHORIZATION, refreshToken());
    }
};
```

#### 2. Apache HttpClient's Automatic Retries

Enable the default, less flexible retry mechanism from the underlying client.

```java
CloseableHttpClient httpClient = ClientBuilder.create()
        .enableAutomaticRetries()
        .build();
```

### Custom Response Body Readers

Create custom readers for special response formats.

```java
HttpRequest httpRequest = HttpRequestBuilder.create(httpClient)
        .addBodyReader(new MyCustomReader())
        .build();
```

You can also implement a `ResponseBodyReader` directly and (optionally) disable default readers:

```java
HttpRequest httpRequest = HttpRequestBuilder.create(closeableHttpClient)
        // .disableDefaultBodyReader() // Disable defaults if needed
        .addBodyReader(new ResponseBodyReader<Map<String, String>>() {
            @Override
            public boolean isReadable(ResponseBodyReadableContext ctx) {
                return ctx.getType() == Map.class;
            }

            @Override
            public ResponseData read(ResponseBodyReaderContext<Map<String, String>> ctx)
                    throws IOException, ResponseBodyReaderException {
                return new ObjectMapper()
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .readValue(ctx.getContent(), ctx.getGenericType());
            }
        })
        .build();
```

### Limiting Response Body Size

To protect your application from OutOfMemoryErrors caused by unexpectedly large HTTP responses, you can set a maximum
size for the response body. This feature is now consistently applied to all response types (String, JSON/XML objects,
etc.).

By default, the response body size is **unlimited**. You can configure a limit on the `HttpRequestBuilder`.

The limit is enforced by wrapping the response entity as soon as the response is received. If the response size exceeds
this limit during reading, an `InvalidContentLengthException` is thrown. This exception is then wrapped and propagated
to you.

- When using a `ResponseHandler`, methods like `.orElseThrow()` will throw a `ResponseException`. The cause of this
  exception will be the `InvalidContentLengthException`.

**Important:** Even when this limit is exceeded, the underlying HTTP connection is **always safely closed and released
back to the pool**. The library ensures resources are cleaned up correctly to prevent connection leaks.

```java
// Configure a client with a 1KB limit
HttpRequest httpRequest = HttpRequestBuilder.create(httpClient)
                .setMaxResponseBodySizeBytes(1024)
                .build();

// This endpoint returns a 2KB response
server.stubFor(get(urlEqualTo("/large-response"))
        .willReturn(aResponse().withStatus(200).withBody("a".repeat(2048))));

try{
    httpRequest.target(httpUri("/large-response"))
        .get(String.class)
        .orElseThrow();

}catch(ResponseException e){
        System.out.println("Correctly caught expected exception: "+e.getClass().getName());

        // Check the cause to see if it was due to the size limit
        if(e.getCause() instanceof InvalidContentLengthException){
            System.out.println("Cause was InvalidContentLengthException, as expected.");
        // Handle the error appropriately
        }
}
```

> **A Note on Connection Handling:** The library's closing behavior changes depending on whether a size limit is set.
> - **Without a size limit:** The library attempts to fully consume the response body before closing. This is a
    performance optimization that allows the underlying connection to be kept alive and reused for subsequent requests
    to the same host.
> - **With a size limit:** The library **intentionally does not** consume the rest of the body. It immediately closes
    the connection to avoid downloading potentially huge amounts of unwanted data. While this may prevent the connection
    from being reused, it guarantees safety and responsiveness, and the connection is always correctly returned to the
    pool.

### Character Encoding

Specify charsets for query-string percent-encoding and for request bodies. URI path segments are
always percent-encoded as UTF-8, per RFC 3986 — pre-encode them yourself if you need a different
encoding.

```java
httpRequest.target("https://api.example.com/search")
    .setQueryCharset(StandardCharsets.UTF_8) // For query-string parameter encoding
    .setBodyCharset(StandardCharsets.ISO_8859_1) // For request body
    .addParameter("q","你好")
    .post("some-body");
```

#### Response decoding charset

When a server returns a body without a `charset=...` parameter on its `Content-Type` header,
this library decodes it as **UTF-8** by default — overriding Apache HC5's bare default of
ISO-8859-1, which is rarely what modern APIs want. If you need to talk to a legacy upstream
that relies on the ISO-8859-1 fallback, restore the old behaviour explicitly:

```java
HttpRequest httpRequest = HttpRequestBuilder.create(httpClient)
        .setDefaultResponseCharset(StandardCharsets.ISO_8859_1)
        .build();
```

A server-supplied `charset=...` parameter always wins; this setting only affects responses
where no charset is declared.

### Security &amp; Hardening

The library ships a small set of opt-in hardening knobs — pick the ones that match your threat
model. None are enabled by default; enabling them is one method call each.

#### SSRF guard (Server-Side Request Forgery)

If your service constructs request URIs from user-supplied input — even partially — an
attacker can use that surface to scan internal networks, hit cloud-metadata endpoints
(`169.254.169.254` on AWS / GCP / Azure exposes IAM credentials), or pivot into private
subnets. Enable the SSRF guard to refuse every host that resolves to loopback,
unspecified, link-local, RFC 1918 (private IPv4), or IPv6 unique-local addresses:

```java
CloseableHttpClient httpClient = ClientBuilder.create()
        .disallowPrivateAndLoopbackHosts()
        .build();
```

The check is plumbed through Apache HC5's `DnsResolver`, which means it fires on every host
the client touches — including hosts reached via 3xx redirects, not only the URL you passed
to `target(...)`. The same DNS lookup that produces the connection IP is the one being
filtered, closing the time-of-check / time-of-use gap a URL-only check would have.

If you need to talk to a specific internal endpoint despite the guard (e.g. an internal
config service on `10.0.7.42`), use the allow-list overload:

```java
InetAddress configService = InetAddress.getByName("10.0.7.42");
CloseableHttpClient httpClient = ClientBuilder.create()
        .disallowPrivateAndLoopbackHosts(addr -> addr.equals(configService))
        .build();
```

The predicate runs on every resolved address that would otherwise be blocked; returning
`true` lets it through, `false` keeps the deny.

#### URI scheme allow-list

Lock the client to HTTP/HTTPS only — refuse `file://`, `jar://`, `data:` and other schemes
that an attacker might smuggle in:

```java
HttpRequest httpRequest = HttpRequestBuilder.create(httpClient)
        .allowHttpAndHttpsOnly()
        .build();

httpRequest.

target("file:///etc/passwd"); // throws IllegalArgumentException
```

#### HTTP/1.1 head-size limits

Bound memory consumption when talking to a hostile or buggy server that sends an unbounded
header list:

```java
CloseableHttpClient httpClient = ClientBuilder.create()
        .setMaxHeaderCount(200)        // reject responses with > 200 headers
        .setMaxLineLength(16 * 1024)   // reject any header line > 16 KiB
        .build();
```

These pair with `setMaxResponseBodySizeBytes(...)` (described above) — the body limit
protects you from oversized payloads, the head limits protect you from oversized header
sets.

#### Payload redaction for request logging

When `enableRequestPayloadLogging()` is on, the library logs the outgoing request body at
DEBUG. To prevent secrets (API keys, OAuth tokens, PII) from landing in those logs, register
one or more redactors:

```java
HttpRequest httpRequest = HttpRequestBuilder.create(httpClient)
        .enableRequestPayloadLogging()
        // Mask the value of an "apiKey" JSON field.
        .addPayloadRedactor(body -> body.replaceAll(
                "\"apiKey\"\\s*:\\s*\"[^\"]+\"",
                "\"apiKey\":\"***\""))
        // Mask any "password" form field.
        .addPayloadRedactor(body -> body.replaceAll(
                "(password=)[^&]+",
                "$1***"))
        .build();
```

Redactors compose left-to-right (each one's output feeds the next). If a redactor throws,
the library swaps in `[redaction-failed]` rather than killing the in-flight request — so a
buggy redactor degrades to "no logged body" instead of "no request sent." Redactors are
only invoked when payload logging is enabled.

> **Important.** When payload logging is on but no redactor is registered, the library logs
> the body verbatim. If your traffic ever carries credentials, register a redactor *or* turn
> payload logging off in production.

### Debugging

Enable request payload logging for easier debugging.

```java
HttpRequest httpRequest = HttpRequestBuilder.create(httpClient)
        .enableRequestPayloadLogging()
        .build();
```

> Pair this with `addPayloadRedactor(...)` (see [Security &amp; Hardening](#security--hardening))
> when the body might carry credentials.

## Why use http-request?

While Apache HttpClient is a powerful and flexible library, `http-request` provides a higher-level, fluent API that
makes common tasks simpler, safer, and more readable.

| Feature                  | Pure Apache HttpClient 5                                          | `http-request` Library                                                                             |
|--------------------------|-------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| **Request Creation**     | Manually create `ClassicRequestBuilder`, set URI, method, entity. | Fluent, chainable API: `httpRequest.target(uri).get()`.                                            |
| **JSON/XML Handling**    | Manually use `ObjectMapper` to serialize/deserialize entities.    | Automatic conversion: `post(userObject)`, `get(User.class)`.                                       |
| **Resource Management**  | Manually close `CloseableHttpResponse` and `CloseableHttpClient`. | Automatic with `ResponseHandler` or simple with `try-with-resources`.                              |
| **Error Handling**       | Check status codes manually, handle `IOException`s.               | Fluent `ifSuccess().otherwise()` chains, specific exceptions like `UnexpectedStatusCodeException`. |
| **Configuration**        | Verbose builders for client, request config, connection manager.  | Simplified `ClientBuilder` and `HttpRequestBuilder` for common settings.                           |
| **Generics (`List<T>`)** | Manually handle `TypeReference` with `ObjectMapper`.              | Built-in support: `get(new TypeReference<List<User>>() {})`.                                       |
| **Retry Logic**          | Implement a custom `HttpRequestRetryStrategy`.                    | Simple and powerful `RetryableWebTarget` with `RetryContext`.                                      |
| **Readability**          | Can become verbose and procedural.                                | Clean, declarative, and easy to read.                                                              |

In short, `http-request` accelerates development, reduces boilerplate, and helps you write more maintainable and robust
HTTP client code.

## API Documentation

Full API documentation is available [here](http://javadoc.io/doc/com.jsunsoft.http/http-request).

Additional documentation:

- [Release Changes](CHANGES.md)
- [Migration Guide](MIGRATION.md)

### A note on `@Beta`

Methods, types, and parameters annotated with `@com.jsunsoft.http.annotations.Beta` are
explicitly **excluded** from the library's compatibility guarantees. Their signatures, default
behavior, exception types, and even names may change between minor (3.x → 3.y) and patch
(3.5.0 → 3.5.1) releases without a deprecation cycle. The annotation says nothing about quality
or performance — only that the API is not yet "frozen."

It's generally safe for *applications* to depend on `@Beta` APIs, at the cost of some extra work
during upgrades. It's generally inadvisable for *libraries* (whose users' classpaths are outside
the library developer's control) to do so — an application picking up the dependency
transitively can break silently when the library upgrades.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

Supported by [JetBrains](https://www.jetbrains.com/?from=http-request)