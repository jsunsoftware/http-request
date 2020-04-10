package com.jsunsoft.http;

import java.io.IOException;

class DefaultStringResponseBodyReader implements ResponseBodyReader<String> {
    static final ResponseBodyReader<String> INSTANCE = new DefaultStringResponseBodyReader();

    @Override
    public boolean isReadable(ResponseBodyReadableContext bodyReadableContext) {
        return true;
    }

    @Override
    public String read(ResponseBodyReaderContext bodyReaderContext) throws IOException, ResponseBodyReaderException {
        return bodyReaderContext.getContentAsString();
    }
}
