# http-request

This lib is built on apache http client for sending rest requests.

Main purpose of the **http-request**, create simple rest client quickly, manage your response very simple and don't worry about Exceptions. 

**http-request** Features: 
Building your HttpRequest requires no more than 5 minutes. <br/>
Used builder pattern to create HttpRequest. <br/>
HttpRequest is designed as one instance to one URI. <br/>
HttpRequest is immutable (thread safe after build). <br/>
There are overloaded methods of execute() for sending request. <br/>
All exceptions are wrapped: <br/>
If connection failure -> status code is a 503(SC_SERVICE_UNAVAILABLE),
If deserialization  of response body is failed -> status code is a 502(SC_BAD_GATEWAY). <br/>
After request ResponseHandler instance is provided to manipulate response data. <br/>
Supported:
* converting response to the type which you want. <br/>
* ignore response body if you interested in only status code. <br/>
* converting from Json. <br/>
* converting from Xml. <br/>
Optimized performance. <br/>

Full API documentation is available [here](http://javadoc.io/doc/com.jsunsoft.http/http-request).


**Note: HttpRequest objects are immutable they can be shared after build.**

### How to use

**Retrieve the Status Code from the Http Response**
After sending the Http request – we get back an instance of com.jsunsoft.http.ResponseHandler – <br/>
which allows us to access the status line of the response, and implicitly the Status Code:
```java
HttpRequest<?> httpRequest = HttpRequestBuilder.createGet("https://www.jsunsoft.com/").build();
ResponseHandler responseHandler = httpRequest.execute();
int statusCode = responseHandler.getStatusCode();
```

**Building HttpRequest by default options**

```java
HttpRequest<SomeTypeToConvertResponseBody> httpRequest = RestClient.createGet(uriString,  SomeTypeToConvertResponseBody.class).build();
```
If you want to ignore the convert of response body, you must build it so:
```java
HttpRequest<?> httpRequest = RestClient.createGet(uri).build();
```
If you want to convert response body to Generic class (example List<T>) by some type you must build it so:

```java
HttpRequest<List<SomeType>> httpRequest = RestClient.createGet(uri,  new TypeReference<List<SomeType>>(){}).build();
ResponseHandler<List<SomeType>> responseHandler = httpRequest.execute();

List<SomeType> someTypes = responseHandler.get(); //see javadoc of get method
or
List<SomeType> someTypes = responseHandler.orElse(Collections.emptyList());
```

**Perform simple http request**
Perform request and get the body of the response without deserialization

```java
HttpRequest<String> httpRequest = HttpRequestBuilder.create(someHttpMethod, "https://www.jsunsoft.com/", String.class)
                                                .responseDeserializer(ResponseDeserializer.ignorableDeserializer()).build();
String responseBody = httpRequest.execute().get(); // see javadoc of get method
```
**Perform simple http get request**
```java
HttpRequest<String> httpRequest = HttpRequestBuilder.createGet("https://www.jsunsoft.com/", String.class)
                                                .responseDeserializer(ResponseDeserializer.ignorableDeserializer()).build();
String responseBody = httpRequest.execute(requestParameters).get(); // see documentation of get method
or
String responseBody = httpRequest.executeWithQuery(queryString).get(); // //queryString example "param1=param1&param2=param2"
```

**Perform simple http post request**
```java
HttpRequest<String> httpRequest = HttpRequestBuilder.createPost("https://www.jsunsoft.com/", String.class)
                                                .responseDeserializer(ResponseDeserializer.ignorableDeserializer()).build();
String responseBody = httpRequest.execute(requestParameters).get(); // see javadoc of get method
```

**Build HttpRequest and  add HEADERS which should be send always.**
```java

HttpRequestBuilder.create(HttpMethod.PUT, "https://www.jsunsoft.com/").addDefaultHeader(someHeader).build();
HttpRequestBuilder.create(HttpMethod.PUT, "https://www.jsunsoft.com/").addDefaultHeaders(someHeaderCollection).build();
HttpRequestBuilder.create(HttpMethod.PUT, "https://www.jsunsoft.com/").addDefaultHeaders(someHeaderArray).build();
HttpRequestBuilder.create(HttpMethod.PUT, "https://www.jsunsoft.com/").addDefaultHeader(headerName, headerValue).build();
```
**Configure connection pool**
By default connection pool size of apache http client is 2. I changed the parameter to default value to 128. To set custom value you can:
```java
HttpRequestBuilder.create(someHttpMethod, someUri).maxPoolPerRoute(someIntValue).build();
or
ConnectionConfig connectionConfigInstance = ConnectionConfig.create().maxPoolPerRoute(someIntValue);
HttpRequestBuilder.create(someHttpMethod, someUri).connectionConfig(connectionConfigInstance).build();
```

**How to set proxy** <br/>

```java
HttpRequest httpRequest = HttpRequestBuilder.create(someHttpMethod, someUri).proxy(host, port).build();
```

**Timeouts**
```text
socketTimeOut is 30000ms
connectionRequestTimeout is 30000ms
connectTimeout is 5000ms;
```
For more information see (https://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html)
or (http://www.baeldung.com/httpclient-timeout)

To change default timeouts you can:
```java
HttpRequestBuilder.create(someHttpMethod, someUri)
                                .connectTimeout(intValue)
                                .socketTimeOut(intValue)
                                .connectionRequestTimeout(intValue).build();
```

**Following redirects**

By default redirecting are disabled.
to enable you can:

```java
HttpRequestBuilder.createGet(someUri).enableLaxRedirectStrategy().build();
or
HttpRequestBuilder.createGet(someUri).enableDefaultRedirectStrategy().build();
or set custom redirect strategy
HttpRequestBuilder.createGet(someUri).setRedirectStrategy(yourRedirectStrategyInstance).build();
```
**Ignore SSL certificate**

```java
HttpRequest<?> httpRequest = HttpRequestBuilder.createGet("https://mms.nw.ru/")
            .trustAllCertificates()
            .trustAllHosts()
            .addDefaultHeader("accept", "application/json")
            .build();

int statusCode = httpRequest.execute().getStatusCode(); // 200
```

**Basic Authentication**

```java
HttpRequestBuilder.createGet(someUri)
            .basicAuth("username_admin", "secret_password").build();

int statusCode = httpRequest.execute().getStatusCode(); //200
```

**Customize CloseableHttpClient before the http-request is built**

```java
HttpRequestBuilder.createGet(someUri)
                .addHttpClientCustomizer(httpClientBuilder -> /* here you can customize your client*/)
                .build();
```
For example if you want to add the Keep-Alive:

```java
HttpRequestBuilder.createGet(someUri)
                .addHttpClientCustomizer(httpClientBuilder -> httpClientBuilder.setKeepAliveStrategy((response, context) -> {
                    //your code;
                }))
                .build();
```

**Real world example how http-request simple and useful**.

No try/catch, No if/else

```java
import com.jsunsoft.http.*;
import java.util.List;
import org.apache.http.entity.ContentType;

public class Rest{
    private static final HttpRequest<List<String>> httpRequest =
     HttpRequestBuilder.createGet("https://www.jsunsoft.com/", new TypeReference<java.util.List<String>>() {})
     .addContentType(ContentType.APPLICATION_JSON).build();
     
     public void send(String jsonData){
         httpRequest.executeWithBody(jsonData).ifSuccess(this::whenSuccess).otherwise(this::whenNotSuccess);
     }
     
     private void whenSuccess(ResponseHandler<List<String>> responseHandler){
         //When predicate of filter returns true, calls whenHasContent else calls whenHasNotContent
         responseHandler.filter(ResponseHandler::hasContent).ifPassed(this::whenHasContent).otherwise(this::whenHasNotContent);
     }
     
     private void whenNotSuccess(ResponseHandler<List<String>> responseHandler){
         //For demo. You can handle what you want
          System.err.println("Error code: " + responseHandler.getStatusCode() + ", error message: " + responseHandler.getErrorText());
     }
     
     private void whenHasContent(ResponseHandler<List<String>> responseHandler){
         //For demo. 
         List<String> responseBody = responseHandler.get();
         System.out.println(responseBody);
     }
     
     private void whenHasNotContent(ResponseHandler<List<String>> responseHandler){
         //For demo. 
           System.out.println("Response is success but body is missing. Response code: " + responseHandler.getStatusCode());
     }
}
```

To use from maven add this snippet to the pom.xml `dependencies` section:

```xml
<dependency>
  <groupId>com.jsunsoft.http</groupId>
  <artifactId>http-request</artifactId>
  <version>1.0.0</version>
</dependency>
```

Pull requests are welcome.