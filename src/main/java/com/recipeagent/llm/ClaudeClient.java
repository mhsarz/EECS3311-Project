package com.recipeagent.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.recipeagent.agent.AgentTool;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Talks to Claude through the Anthropic Messages API.
 * <p>
 * Adapter pattern: this is the <b>Adapter</b>. It implements the {@link LLMClient}
 * Target that RecipeAgent expects, converts generate(prompt, tools) into the API's
 * request format for {@link AnthropicApi#createMessage}, and converts the API's
 * content blocks back into an {@link LLMResponse}. All format conversion lives here,
 * so changing the model or provider does not touch the agent.
 */
public class ClaudeClient implements LLMClient {

    /**
     * Upper limit for one reply. Newer models think before answering and the thinking counts
     * towards this limit, so it is set well above the size of a recipe or a few flyer pages;
     * only the tokens actually used are paid for. It stays under 21,333, above which the API
     * expects streaming because long replies can hit HTTP timeouts.
     */
    public static final int DEFAULT_MAX_TOKENS = 16000;
    private static final Pattern TOOL_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final AnthropicApi sdk;
    private final String model;
    private final int maxTokens;

    public ClaudeClient(String apiKey, String model) {
        this(new AnthropicApi(apiKey), model, DEFAULT_MAX_TOKENS);
    }

    public ClaudeClient(AnthropicApi sdk, String model, int maxTokens) {
        if (sdk == null) {
            throw new IllegalArgumentException("AnthropicApi must not be null");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model name is required");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be more than zero");
        }
        this.sdk = sdk;
        this.model = model.trim();
        this.maxTokens = maxTokens;
    }

    public String getModel() {
        return model;
    }

    @Override
    public LLMResponse generate(String prompt, List<AgentTool> tools) {
        JsonObject params = toParams(prompt, tools);
        JsonObject reply = sdk.createMessage(params);
        return toResponse(reply);
    }

    /**
     * Sends the prompt with files (F10 PDF flyers). Each file becomes a "document" content
     * block before the prompt text, as the Messages API recommends; Claude reads a PDF's text
     * and a picture of each page, so flyers that are only images work too.
     */
    @Override
    public LLMResponse generate(String prompt, List<AgentTool> tools, List<Attachment> files) {
        if (files == null || files.isEmpty()) {
            return generate(prompt, tools);
        }
        JsonObject params = toParams(prompt, tools, files);
        JsonObject reply = sdk.createMessage(params, AnthropicApi.FILE_REQUEST_TIMEOUT);
        return toResponse(reply);
    }

    /** Builds the Messages API request body. */
    JsonObject toParams(String prompt, List<AgentTool> tools) {
        return toParams(prompt, tools, List.of());
    }

    /** Builds the request body; with files, the message content is a list of blocks. */
    JsonObject toParams(String prompt, List<AgentTool> tools, List<Attachment> files) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be empty");
        }
        JsonObject params = new JsonObject();
        params.addProperty("model", model);
        params.addProperty("max_tokens", maxTokens);

        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        if (files == null || files.isEmpty()) {
            message.addProperty("content", prompt);
        } else {
            JsonArray content = new JsonArray();
            for (Attachment f : files) {
                JsonObject source = new JsonObject();
                source.addProperty("type", "base64");
                source.addProperty("media_type", f.getMediaType());
                source.addProperty("data", f.toBase64());
                JsonObject doc = new JsonObject();
                doc.addProperty("type", "document");
                doc.add("source", source);
                // Cache the document: a long flyer is read in several calls with the same PDF,
                // and cached input costs a fraction of the normal price.
                JsonObject cache = new JsonObject();
                cache.addProperty("type", "ephemeral");
                doc.add("cache_control", cache);
                content.add(doc);
            }
            JsonObject text = new JsonObject();
            text.addProperty("type", "text");
            text.addProperty("text", prompt);
            content.add(text);
            message.add("content", content);
        }
        JsonArray messages = new JsonArray();
        messages.add(message);
        params.add("messages", messages);

        if (tools != null && !tools.isEmpty()) {
            JsonArray toolArray = new JsonArray();
            for (AgentTool t : tools) {
                toolArray.add(toToolJson(t));
            }
            params.add("tools", toolArray);
        }
        return params;
    }

    private static JsonObject toToolJson(AgentTool tool) {
        String name = tool.getName();
        if (name == null || !TOOL_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid tool name '" + name
                    + "' (use 1-64 letters, digits, '_' or '-')");
        }
        JsonElement schema;
        try {
            schema = JsonParser.parseString(tool.getInputSchema());
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Tool '" + name + "' has an invalid input schema", e);
        }
        if (!schema.isJsonObject()) {
            throw new IllegalArgumentException("Tool '" + name + "' input schema must be a JSON object");
        }
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("description", tool.getDescription() == null ? "" : tool.getDescription());
        json.add("input_schema", schema);
        return json;
    }

    /**
     * Converts a Messages API reply. Text blocks are joined; if the model asked for
     * tools, the first tool_use block becomes the tool call (the agent runs one at a time).
     */
    static LLMResponse toResponse(JsonObject reply) {
        if (reply == null || !reply.has("content") || !reply.get("content").isJsonArray()) {
            throw new LLMException("Reply from Claude has no content");
        }
        StringBuilder text = new StringBuilder();
        String toolName = null;
        String toolArgs = null;
        for (JsonElement el : reply.getAsJsonArray("content")) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject block = el.getAsJsonObject();
            String type = block.has("type") ? block.get("type").getAsString() : "";
            if (type.equals("text") && block.has("text")) {
                if (text.length() > 0) {
                    text.append("\n");
                }
                text.append(block.get("text").getAsString());
            } else if (type.equals("tool_use") && toolName == null && block.has("name")) {
                toolName = block.get("name").getAsString();
                toolArgs = block.has("input") ? block.get("input").toString() : "{}";
            }
        }
        String stop = reply.has("stop_reason") && !reply.get("stop_reason").isJsonNull()
                ? reply.get("stop_reason").getAsString() : "";
        int in = 0;
        int out = 0;
        if (reply.has("usage") && reply.get("usage").isJsonObject()) {
            JsonObject usage = reply.getAsJsonObject("usage");
            in = usage.has("input_tokens") ? usage.get("input_tokens").getAsInt() : 0;
            out = usage.has("output_tokens") ? usage.get("output_tokens").getAsInt() : 0;
        }
        return new LLMResponse(text.toString(), toolName, toolArgs, stop, in, out);
    }
}
