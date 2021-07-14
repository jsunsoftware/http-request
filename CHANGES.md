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
