package com.recipeagent.llm;

import com.recipeagent.agent.AgentTool;
import com.recipeagent.storage.JsonStorage;
import com.recipeagent.storage.StorageException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Controls access to the real LLM client.
 * <p>
 * Proxy pattern: this is the <b>Proxy</b>. It implements the same {@link LLMClient}
 * Subject interface as the Real Subject (ClaudeClient), so RecipeAgent's code does not
 * change. Every call passes through here, which:
 * <ul>
 *   <li>enforces a daily call budget, because every call costs money,</li>
 *   <li>turns any failure (no key, no network, API error) into one
 *       {@link LLMUnavailableException}, so all AI features handle errors the same way,</li>
 *   <li>counts calls and tokens, and saves today's usage so a restart does not reset it.</li>
 * </ul>
 */
public class LLMClientProxy implements LLMClient {

    static final String USAGE_KEY = "llm-usage";

    private final LLMClient real;
    private final int dailyLimit;
    private final JsonStorage storage;
    private final Clock clock;

    private LocalDate day;
    private int callsToday;
    private long inputTokensToday;
    private long outputTokensToday;

    /**
     * @param real       the real client, or null when no API key is configured
     * @param dailyLimit maximum calls per day (more than zero)
     */
    public LLMClientProxy(LLMClient real, int dailyLimit) {
        this(real, dailyLimit, null, Clock.systemDefaultZone());
    }

    /**
     * @param storage where today's usage is saved (null = not saved)
     * @param clock   source of today's date (a fixed clock in tests)
     */
    public LLMClientProxy(LLMClient real, int dailyLimit, JsonStorage storage, Clock clock) {
        if (dailyLimit <= 0) {
            throw new IllegalArgumentException("Daily limit must be more than zero");
        }
        if (clock == null) {
            throw new IllegalArgumentException("Clock must not be null");
        }
        this.real = real;
        this.dailyLimit = dailyLimit;
        this.storage = storage;
        this.clock = clock;
        this.day = LocalDate.now(clock);
        loadUsage();
    }

    /** True if there is a real client and today's budget is not used up. */
    public synchronized boolean isAvailable() {
        return getUnavailableReason() == null;
    }

    /** Why calls are refused right now, or null if they are allowed. */
    public synchronized String getUnavailableReason() {
        rollOverIfNewDay();
        if (real == null) {
            return "No LLM API key is set. Copy .env.example to .env and set LLM_API_KEY.";
        }
        if (callsToday >= dailyLimit) {
            return "The daily limit of " + dailyLimit + " LLM calls has been reached. Try again tomorrow.";
        }
        return null;
    }

    @Override
    public LLMResponse generate(String prompt, List<AgentTool> tools) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be empty");
        }
        synchronized (this) {
            String reason = getUnavailableReason();
            if (reason != null) {
                throw new LLMUnavailableException(reason);
            }
            callsToday++;          // counted before the call: a failed request may still cost money
            saveUsage();
        }
        LLMResponse response;
        try {
            response = real.generate(prompt, tools);
        } catch (LLMUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LLMUnavailableException("LLM unavailable: " + e.getMessage(), e);
        }
        if (response == null) {
            throw new LLMUnavailableException("LLM unavailable: empty reply");
        }
        synchronized (this) {
            inputTokensToday += response.getInputTokens();
            outputTokensToday += response.getOutputTokens();
            saveUsage();
        }
        return response;
    }

    public synchronized int getCallsToday() {
        rollOverIfNewDay();
        return callsToday;
    }

    public int getDailyLimit() {
        return dailyLimit;
    }

    public synchronized int getRemainingCalls() {
        rollOverIfNewDay();
        return Math.max(0, dailyLimit - callsToday);
    }

    public synchronized long getInputTokensToday() {
        rollOverIfNewDay();
        return inputTokensToday;
    }

    public synchronized long getOutputTokensToday() {
        rollOverIfNewDay();
        return outputTokensToday;
    }

    private void rollOverIfNewDay() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) {
            day = today;
            callsToday = 0;
            inputTokensToday = 0;
            outputTokensToday = 0;
        }
    }

    private void loadUsage() {
        if (storage == null) {
            return;
        }
        try {
            UsageRecord saved = storage.load(USAGE_KEY, UsageRecord.class);
            if (saved != null && day.toString().equals(saved.date)) {
                callsToday = Math.max(0, saved.calls);
                inputTokensToday = Math.max(0, saved.inputTokens);
                outputTokensToday = Math.max(0, saved.outputTokens);
            }
        } catch (StorageException e) {
            System.err.println("Warning: could not read LLM usage, starting from zero: " + e.getMessage());
        }
    }

    private void saveUsage() {
        if (storage == null) {
            return;
        }
        try {
            storage.save(USAGE_KEY, new UsageRecord(day.toString(), callsToday,
                    inputTokensToday, outputTokensToday));
        } catch (StorageException e) {
            System.err.println("Warning: could not save LLM usage: " + e.getMessage());
        }
    }
}
