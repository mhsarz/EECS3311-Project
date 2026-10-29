package com.recipeagent.llm;

import com.recipeagent.agent.AgentTool;
import com.recipeagent.storage.JsonStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LLMClientProxyTest {

    @TempDir
    Path tmp;

    /** A clock the test can move forward. */
    private static class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-29T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** Counts calls and returns a fixed answer, or throws if told to fail. */
    private static class FakeClient implements LLMClient {
        int calls;
        RuntimeException failWith;

        @Override
        public LLMResponse generate(String prompt, List<AgentTool> tools) {
            calls++;
            if (failWith != null) {
                throw failWith;
            }
            return new LLMResponse("answer", null, null, "end_turn", 100, 20);
        }
    }

    @Test
    void forwardsCallsWithinBudget() {
        FakeClient real = new FakeClient();
        LLMClientProxy proxy = new LLMClientProxy(real, 3);
        assertTrue(proxy.isAvailable());
        assertEquals("answer", proxy.generate("hi", List.of()).getText());
        assertEquals(1, real.calls);
        assertEquals(1, proxy.getCallsToday());
        assertEquals(2, proxy.getRemainingCalls());
    }

    @Test
    void refusesCallsAfterDailyLimit() {
        FakeClient real = new FakeClient();
        LLMClientProxy proxy = new LLMClientProxy(real, 2);
        proxy.generate("1", null);
        proxy.generate("2", null);

        assertFalse(proxy.isAvailable());
        LLMUnavailableException e = assertThrows(LLMUnavailableException.class,
                () -> proxy.generate("3", null));
        assertTrue(e.getMessage().contains("daily limit of 2"));
        assertEquals(2, real.calls);          // the real client was not called again
    }

    @Test
    void budgetResetsOnNewDay() {
        TestClock clock = new TestClock();
        LLMClientProxy proxy = new LLMClientProxy(new FakeClient(), 1, null, clock);
        proxy.generate("1", null);
        assertFalse(proxy.isAvailable());

        clock.now = clock.now.plusSeconds(24 * 3600);
        assertTrue(proxy.isAvailable());
        assertEquals(0, proxy.getCallsToday());
        proxy.generate("2", null);
    }

    @Test
    void anyFailureBecomesLLMUnavailable() {
        FakeClient real = new FakeClient();
        real.failWith = new LLMException("Anthropic API returned HTTP 529 (overloaded_error: busy)");
        LLMClientProxy proxy = new LLMClientProxy(real, 5);

        LLMUnavailableException e = assertThrows(LLMUnavailableException.class,
                () -> proxy.generate("hi", null));
        assertTrue(e.getMessage().startsWith("LLM unavailable"));
        assertTrue(e.getMessage().contains("529"));

        real.failWith = new IllegalStateException("bug in parsing");
        assertThrows(LLMUnavailableException.class, () -> proxy.generate("hi", null));
        assertEquals(2, proxy.getCallsToday());   // failed calls still count toward the budget
    }

    @Test
    void noApiKeyMeansUnavailable() {
        LLMClientProxy proxy = new LLMClientProxy(null, 10);
        assertFalse(proxy.isAvailable());
        assertTrue(proxy.getUnavailableReason().contains("LLM_API_KEY"));
        assertThrows(LLMUnavailableException.class, () -> proxy.generate("hi", null));
        assertEquals(0, proxy.getCallsToday());
    }

    @Test
    void emptyPromptIsRejectedWithoutUsingBudget() {
        LLMClientProxy proxy = new LLMClientProxy(new FakeClient(), 10);
        assertThrows(IllegalArgumentException.class, () -> proxy.generate(" ", null));
        assertEquals(0, proxy.getCallsToday());
    }

    @Test
    void countsTokens() {
        LLMClientProxy proxy = new LLMClientProxy(new FakeClient(), 10);
        proxy.generate("a", null);
        proxy.generate("b", null);
        assertEquals(200, proxy.getInputTokensToday());
        assertEquals(40, proxy.getOutputTokensToday());
    }

    @Test
    void usageSurvivesRestartOnSameDayOnly() {
        TestClock clock = new TestClock();
        JsonStorage storage = new JsonStorage(tmp);
        LLMClientProxy first = new LLMClientProxy(new FakeClient(), 2, storage, clock);
        first.generate("1", null);
        first.generate("2", null);

        LLMClientProxy restarted = new LLMClientProxy(new FakeClient(), 2, storage, clock);
        assertEquals(2, restarted.getCallsToday());
        assertEquals(200, restarted.getInputTokensToday());
        assertFalse(restarted.isAvailable());

        clock.now = clock.now.plusSeconds(24 * 3600);
        LLMClientProxy nextDay = new LLMClientProxy(new FakeClient(), 2, storage, clock);
        assertEquals(0, nextDay.getCallsToday());
        assertTrue(nextDay.isAvailable());
    }

    @Test
    void rejectsBadSettings() {
        assertThrows(IllegalArgumentException.class, () -> new LLMClientProxy(new FakeClient(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new LLMClientProxy(new FakeClient(), 1, null, null));
    }

    @Test
    void proxyAndRealClientShareTheSameInterface() {
        LLMClient client = new LLMClientProxy(new FakeClient(), 5);
        assertEquals("answer", client.generate("hi", null).getText());
    }
}
