package com.recipeagent.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.recipeagent.agent.AgentTool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeClientTest {

    /** Stands in for the real API: remembers the request and returns a fixed reply. */
    private static class FakeApi extends AnthropicApi {
        JsonObject lastParams;
        final String reply;

        FakeApi(String reply) {
            super("fake-key");
            this.reply = reply;
        }

        @Override
        public JsonObject createMessage(JsonObject params) {
            lastParams = params;
            return JsonParser.parseString(reply).getAsJsonObject();
        }
    }

    private static AgentTool tool(String name, String schema) {
        return new AgentTool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return "Test tool " + name;
            }

            @Override
            public String getInputSchema() {
                return schema;
            }

            @Override
            public String execute(String args) {
                return "ok";
            }
        };
    }

    private static final String TEXT_REPLY = "{\"content\":[{\"type\":\"text\",\"text\":\"Hello\"},"
            + "{\"type\":\"text\",\"text\":\"there\"}],\"stop_reason\":\"end_turn\","
            + "\"usage\":{\"input_tokens\":12,\"output_tokens\":5}}";

    @Test
    void buildsRequestWithModelPromptAndTools() {
        FakeApi api = new FakeApi(TEXT_REPLY);
        ClaudeClient client = new ClaudeClient(api, "claude-test", 500);
        String schema = "{\"type\":\"object\",\"properties\":{\"item\":{\"type\":\"string\"}}}";

        client.generate("What can I cook?", List.of(tool("cart", schema)));

        JsonObject p = api.lastParams;
        assertEquals("claude-test", p.get("model").getAsString());
        assertEquals(500, p.get("max_tokens").getAsInt());
        JsonObject msg = p.getAsJsonArray("messages").get(0).getAsJsonObject();
        assertEquals("user", msg.get("role").getAsString());
        assertEquals("What can I cook?", msg.get("content").getAsString());
        JsonArray tools = p.getAsJsonArray("tools");
        assertEquals(1, tools.size());
        JsonObject t = tools.get(0).getAsJsonObject();
        assertEquals("cart", t.get("name").getAsString());
        assertEquals("Test tool cart", t.get("description").getAsString());
        assertEquals("string", t.getAsJsonObject("input_schema").getAsJsonObject("properties")
                .getAsJsonObject("item").get("type").getAsString());
    }

    @Test
    void omitsToolsWhenNoneGiven() {
        FakeApi api = new FakeApi(TEXT_REPLY);
        new ClaudeClient(api, "m", 100).generate("hi", List.of());
        assertFalse(api.lastParams.has("tools"));
        new ClaudeClient(api, "m", 100).generate("hi", null);
        assertFalse(api.lastParams.has("tools"));
    }

    @Test
    void convertsTextReply() {
        LLMResponse r = new ClaudeClient(new FakeApi(TEXT_REPLY), "m", 100).generate("hi", null);
        assertFalse(r.hasToolCall());
        assertEquals("Hello\nthere", r.getText());
        assertEquals("end_turn", r.getStopReason());
        assertEquals(12, r.getInputTokens());
        assertEquals(5, r.getOutputTokens());
    }

    @Test
    void convertsToolUseReply() {
        String reply = "{\"content\":[{\"type\":\"text\",\"text\":\"Adding eggs.\"},"
                + "{\"type\":\"tool_use\",\"id\":\"t1\",\"name\":\"cart\",\"input\":{\"item\":\"eggs\",\"qty\":12}},"
                + "{\"type\":\"tool_use\",\"id\":\"t2\",\"name\":\"pantry\",\"input\":{}}],"
                + "\"stop_reason\":\"tool_use\"}";
        LLMResponse r = new ClaudeClient(new FakeApi(reply), "m", 100).generate("add eggs", null);
        assertTrue(r.hasToolCall());
        assertEquals("cart", r.getToolName());
        JsonObject args = JsonParser.parseString(r.getToolArgs()).getAsJsonObject();
        assertEquals("eggs", args.get("item").getAsString());
        assertEquals(12, args.get("qty").getAsInt());
        assertEquals("Adding eggs.", r.getText());
        assertEquals(0, r.getInputTokens());
    }

    @Test
    void truncatedReplyIsDetected() {
        String reply = "{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"title\\\":\"}],"
                + "\"stop_reason\":\"max_tokens\"}";
        assertTrue(new ClaudeClient(new FakeApi(reply), "m", 100).generate("x", null).isTruncated());
    }

    @Test
    void replyWithoutContentIsAnError() {
        ClaudeClient c = new ClaudeClient(new FakeApi("{\"id\":\"x\"}"), "m", 100);
        assertThrows(LLMException.class, () -> c.generate("hi", null));
    }

    @Test
    void rejectsBadInput() {
        ClaudeClient c = new ClaudeClient(new FakeApi(TEXT_REPLY), "m", 100);
        assertThrows(IllegalArgumentException.class, () -> c.generate(" ", null));
        assertThrows(IllegalArgumentException.class,
                () -> c.generate("hi", List.of(tool("bad name!", "{}"))));
        assertThrows(IllegalArgumentException.class,
                () -> c.generate("hi", List.of(tool("cart", "not json"))));
        assertThrows(IllegalArgumentException.class,
                () -> c.generate("hi", List.of(tool("cart", "[]"))));
        assertThrows(IllegalArgumentException.class, () -> new ClaudeClient(new FakeApi(TEXT_REPLY), " ", 100));
        assertThrows(IllegalArgumentException.class, () -> new ClaudeClient(new FakeApi(TEXT_REPLY), "m", 0));
        assertThrows(IllegalArgumentException.class, () -> new ClaudeClient(null, "m", 100));
    }

    @Test
    void responseFactories() {
        assertFalse(LLMResponse.text("hi").hasToolCall());
        LLMResponse t = LLMResponse.toolCall("cart", null);
        assertTrue(t.hasToolCall());
        assertEquals("{}", t.getToolArgs());
        assertNull(LLMResponse.text("hi").getToolArgs());
    }

    @Test
    void attachedPdfIsSentAsADocumentBlockBeforeTheText() {
        java.util.concurrent.atomic.AtomicReference<java.time.Duration> timeout = new java.util.concurrent.atomic.AtomicReference<>();
        FakeApi api = new FakeApi(TEXT_REPLY) {
            @Override
            public JsonObject createMessage(JsonObject params, java.time.Duration t) {
                timeout.set(t);
                return createMessage(params);
            }
        };
        ClaudeClient client = new ClaudeClient(api, "claude-test", 1000);
        byte[] pdf = "%PDF-1.7 pages".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        LLMResponse r = client.generate("List the sale items.", List.of(), List.of(Attachment.pdf(pdf, "nf.pdf")));
        assertEquals("Hello\nthere", r.getText());
        assertEquals(AnthropicApi.FILE_REQUEST_TIMEOUT, timeout.get());

        JsonArray content = api.lastParams.getAsJsonArray("messages").get(0).getAsJsonObject().getAsJsonArray("content");
        assertEquals(2, content.size());
        JsonObject doc = content.get(0).getAsJsonObject();
        assertEquals("document", doc.get("type").getAsString());
        JsonObject source = doc.getAsJsonObject("source");
        assertEquals("base64", source.get("type").getAsString());
        assertEquals("application/pdf", source.get("media_type").getAsString());
        assertEquals(java.util.Base64.getEncoder().encodeToString(pdf), source.get("data").getAsString());
        assertEquals("ephemeral", doc.getAsJsonObject("cache_control").get("type").getAsString());
        assertEquals("text", content.get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("List the sale items.", content.get(1).getAsJsonObject().get("text").getAsString());

        // without files the content stays a plain string, as before
        client.generate("Hi", List.of(), List.of());
        assertEquals("Hi", api.lastParams.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());
    }

    @Test
    void clientsThatCannotSendFilesSaySo() {
        LLMClient plain = (p, t) -> LLMResponse.text("ok");
        assertEquals("ok", plain.generate("hi", List.of(), List.of()).getText());
        byte[] pdf = {'%', 'P', 'D', 'F', '-'};
        LLMException e = assertThrows(LLMException.class,
                () -> plain.generate("hi", List.of(), List.of(Attachment.pdf(pdf, "a.pdf"))));
        assertTrue(e.getMessage().contains("cannot read attached files"));
        assertThrows(IllegalArgumentException.class, () -> Attachment.pdf(new byte[0], "x.pdf"));
        assertEquals("a.pdf (application/pdf, 5 bytes)", Attachment.pdf(pdf, "a.pdf").toString());
    }
}
