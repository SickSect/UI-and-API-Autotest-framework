package org.ugina.ApiClient.Data;

public class JsonRequestBody implements IRequestBody {
    private final String json;

    public JsonRequestBody(String json) {
        this.json = json;
    }

    @Override
    public String content() {
        return json;
    }

    @Override
    public String contentType() {
        return "application/json";
    }
}
