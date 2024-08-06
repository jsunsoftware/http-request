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
