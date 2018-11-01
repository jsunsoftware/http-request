# Http-Request changes

Check also [MIGRATION.md](MIGRATION.md) for possible compatibility problems.

### 1.0.0
Removed method `contentTypeOfBody` from `HttpRequestBuilder`.

### 1.0.1
Added method `addHttpClientCustomizer` into `HttpRequestBuilder`

### 1.0.2
Removed `inputStreamDeserializer` from `ResponseDeserializer`. **Reason:** Response should be deserialized by `ResponseDeserializer`  
Renamed `ignorableDeserializer` of `ResponseDeserializer` to `toStringDeserializer`