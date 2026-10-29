package com.recipeagent.llm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicApiTest {

    @Test
    void requestHasRequiredHeaders() {
        AnthropicApi api = new AnthropicApi("test-key");
        HttpRequest req = api.buildRequest(new JsonObject());
        assertEquals("POST", req.method());
        assertEquals(AnthropicApi.DEFAULT_ENDPOINT, req.uri());
        assertEquals("test-key", req.headers().firstValue("x-api-key").orElseThrow());
        assertEquals(AnthropicApi.API_VERSION,
                req.headers().firstValue("anthropic-version").orElseThrow());
        assertEquals("application/json", req.headers().firstValue("content-type").orElseThrow());
    }

    @Test
    void rejectsMissingKey() {
        assertThrows(IllegalArgumentException.class, () -> new AnthropicApi(" "));
        assertThrows(IllegalArgumentException.class, () -> new AnthropicApi(null));
    }

    @Test
    void parsesSuccessfulReply() {
        JsonObject json = AnthropicApi.parseResponse(200, "{\"content\":[]}");
        assertTrue(json.has("content"));
    }

    @Test
    void errorReplyIncludesApiMessageButNotKey() {
        String body = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\","
                + "\"message\":\"invalid x-api-key\"}}";
        LLMException e = assertThrows(LLMException.class,
                () -> AnthropicApi.parseResponse(401, body));
        assertTrue(e.getMessage().contains("401"));
        assertTrue(e.getMessage().contains("authentication_error: invalid x-api-key"));
    }

    @Test
    void nonJsonRepliesAreErrors() {
        assertThrows(LLMException.class, () -> AnthropicApi.parseResponse(200, "<html>oops</html>"));
        assertThrows(LLMException.class, () -> AnthropicApi.parseResponse(200, "[1,2]"));
        LLMException e = assertThrows(LLMException.class,
                () -> AnthropicApi.parseResponse(502, "Bad Gateway"));
        assertTrue(e.getMessage().contains("502"));
    }

    @Test
    void sendsRequestToServerAndReadsReply() throws IOException {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedKey = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            receivedKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            byte[] reply = "{\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, reply.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(reply);
            }
        });
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/messages");
            AnthropicApi api = new AnthropicApi("local-key", uri);
            JsonObject params = new JsonObject();
            params.addProperty("model", "test-model");

            JsonObject reply = api.createMessage(params);

            assertEquals("hi", reply.getAsJsonArray("content").get(0)
                    .getAsJsonObject().get("text").getAsString());
            assertEquals("local-key", receivedKey.get());
            assertEquals("test-model", JsonParser.parseString(receivedBody.get())
                    .getAsJsonObject().get("model").getAsString());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unreachableServerGivesLLMException() {
        // Port 1 on localhost is almost never open, so the connection is refused.
        AnthropicApi api = new AnthropicApi("k", URI.create("http://127.0.0.1:1/v1/messages"));
        assertThrows(LLMException.class, () -> api.createMessage(new JsonObject()));
    }
}
