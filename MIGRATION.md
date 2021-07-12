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

Replace `setUseDefaultReader(boolean useDefaultReader)` of `HttpRequestBuilder` to `enableDefaultBodyReader` or `disableDefaultBodyReader`
