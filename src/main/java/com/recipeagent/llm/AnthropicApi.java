package com.recipeagent.llm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Thin HTTP client for the Anthropic Messages API (POST /v1/messages).
 * <p>
 * Adapter pattern: this is the <b>Adaptee</b>. It speaks the API's own format: a JSON
 * request with "model", "max_tokens", "messages" and "tools", and a JSON reply whose
 * "content" is a list of "text" and "tool_use" blocks. RecipeAgent never sees this
 * format; ClaudeClient converts it.
 * <p>
 * The API key is only sent in the x-api-key header and is never included in error messages.
 */
public class AnthropicApi {

    public static final URI DEFAULT_ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");
    public static final String API_VERSION = "2023-06-01";
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);

    private final String apiKey;
    private final URI endpoint;
    private final HttpClient http;

    public AnthropicApi(String apiKey) {
        this(apiKey, DEFAULT_ENDPOINT);
    }

    /** Uses another endpoint, e.g. a local test server. */
    public AnthropicApi(String apiKey, URI endpoint) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key is required");
        }
        if (endpoint == null) {
            throw new IllegalArgumentException("Endpoint is required");
        }
        this.apiKey = apiKey.trim();
        this.endpoint = endpoint;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    /**
     * Sends a Messages API request.
     *
     * @param params the request body
     * @return the reply body as JSON
     * @throws LLMException if the API cannot be reached or returns an error
     */
    public JsonObject createMessage(JsonObject params) {
        HttpResponse<String> response;
        try {
            response = http.send(buildRequest(params),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new LLMException("Could not reach the Anthropic API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LLMException("Request to the Anthropic API was interrupted", e);
        }
        return parseResponse(response.statusCode(), response.body());
    }

    HttpRequest buildRequest(JsonObject params) {
        if (params == null) {
            throw new IllegalArgumentException("Request parameters must not be null");
        }
        return HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("x-api-key", apiKey)
                .header("anthropic-version", API_VERSION)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(params.toString(), StandardCharsets.UTF_8))
                .build();
    }

    /** Turns an HTTP status and body into the reply JSON, or throws with the API's error message. */
    static JsonObject parseResponse(int status, String body) {
        JsonObject json = null;
        try {
            JsonElement el = JsonParser.parseString(body == null ? "" : body);
            if (el.isJsonObject()) {
                json = el.getAsJsonObject();
            }
        } catch (JsonParseException e) {
            // handled below
        }
        if (status != 200) {
            String detail = "no details";
            if (json != null && json.has("error") && json.get("error").isJsonObject()) {
                JsonObject err = json.getAsJsonObject("error");
                String type = err.has("type") ? err.get("type").getAsString() : "error";
                String msg = err.has("message") ? err.get("message").getAsString() : "";
                detail = type + ": " + msg;
            }
            throw new LLMException("Anthropic API returned HTTP " + status + " (" + detail + ")");
        }
        if (json == null) {
            throw new LLMException("Anthropic API returned a reply that is not a JSON object");
        }
        return json;
    }
}
