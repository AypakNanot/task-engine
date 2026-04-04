package com.aypak.taskengine.circuitbreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CircuitBreaker 单元测试。
 * Unit tests for CircuitBreaker.
 */
@DisplayName("CircuitBreaker Tests")
class CircuitBreakerTest {

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = CircuitBreaker.defaultBreaker();
    }

    @Test
    @DisplayName("Should create default breaker with correct config")
    void shouldCreateDefaultBreakerWithCorrectConfig() {
        CircuitBreaker breaker = CircuitBreaker.defaultBreaker();

        assertNotNull(breaker);
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
        assertEquals(0, breaker.getFailureRate());
    }

    @Test
    @DisplayName("Should allow requests when closed")
    void shouldAllowRequestsWhenClosed() {
        assertTrue(circuitBreaker.allowRequest());
    }

    @Test
    @DisplayName("Should trip to open state after threshold failures")
    void shouldTripToOpenAfterThresholdFailures() {
        // Record minimum requests (10) with high failure rate
        for (int i = 0; i < 10; i++) {
            circuitBreaker.recordFailure();
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertFalse(circuitBreaker.allowRequest());
    }

    @Test
    @DisplayName("Should transition to half-open after timeout")
    void shouldTransitionToHalfOpenAfterTimeout() throws Exception {
        // Trip the breaker
        for (int i = 0; i < 10; i++) {
            circuitBreaker.recordFailure();
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        // Wait for timeout (30 seconds default, use custom breaker for test)
        CircuitBreaker fastBreaker = new CircuitBreaker(5, 0.5, 100, 3);
        for (int i = 0; i < 10; i++) {
            fastBreaker.recordFailure();
        }

        assertEquals(CircuitBreaker.State.OPEN, fastBreaker.getState());

        Thread.sleep(150);

        assertEquals(CircuitBreaker.State.HALF_OPEN, fastBreaker.getState());
        assertTrue(fastBreaker.allowRequest());
    }

    @Test
    @DisplayName("Should close after successful request in half-open state")
    void shouldCloseAfterSuccessInHalfOpenState() {
        CircuitBreaker fastBreaker = new CircuitBreaker(5, 0.5, 100, 3);

        // Trip the breaker
        for (int i = 0; i < 10; i++) {
            fastBreaker.recordFailure();
        }

        // Wait for half-open
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertEquals(CircuitBreaker.State.HALF_OPEN, fastBreaker.getState());

        // Success should close the breaker
        fastBreaker.recordSuccess();

        assertEquals(CircuitBreaker.State.CLOSED, fastBreaker.getState());
    }

    @Test
    @DisplayName("Should reopen after failure in half-open state")
    void shouldReopenAfterFailureInHalfOpenState() {
        CircuitBreaker fastBreaker = new CircuitBreaker(5, 0.5, 100, 3);

        // Trip the breaker
        for (int i = 0; i < 10; i++) {
            fastBreaker.recordFailure();
        }

        // Wait for half-open
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertEquals(CircuitBreaker.State.HALF_OPEN, fastBreaker.getState());

        // Failure should reopen the breaker
        fastBreaker.recordFailure();

        assertEquals(CircuitBreaker.State.OPEN, fastBreaker.getState());
    }

    @Test
    @DisplayName("Should limit requests in half-open state")
    void shouldLimitRequestsInHalfOpenState() {
        CircuitBreaker fastBreaker = new CircuitBreaker(5, 0.5, 50, 2);

        // Trip the breaker
        for (int i = 0; i < 10; i++) {
            fastBreaker.recordFailure();
        }

        // Wait for half-open
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertEquals(CircuitBreaker.State.HALF_OPEN, fastBreaker.getState());

        // Should allow only 2 requests
        assertTrue(fastBreaker.allowRequest());
        assertTrue(fastBreaker.allowRequest());
        assertFalse(fastBreaker.allowRequest());
    }

    @Test
    @DisplayName("Should calculate failure rate correctly")
    void shouldCalculateFailureRateCorrectly() {
        CircuitBreaker breaker = new CircuitBreaker(5, 0.5, 30000, 3);

        breaker.recordSuccess();
        breaker.recordSuccess();
        breaker.recordFailure();
        breaker.recordFailure();

        // 2 failures / 4 total = 0.5
        assertEquals(0.5, breaker.getFailureRate(), 0.01);
    }

    @Test
    @DisplayName("Should return zero failure rate when no requests")
    void shouldReturnZeroFailureRateWhenNoRequests() {
        assertEquals(0, circuitBreaker.getFailureRate());
    }

    @Test
    @DisplayName("Should reset all state on manual reset")
    void shouldResetAllStateOnManualReset() {
        // Trip the breaker
        for (int i = 0; i < 10; i++) {
            circuitBreaker.recordFailure();
        }

        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());

        // Reset
        circuitBreaker.reset();

        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertEquals(0, circuitBreaker.getStats().getFailureCount());
        assertEquals(0, circuitBreaker.getStats().getTotalCount());
        assertEquals(0, circuitBreaker.getFailureRate());
    }

    @Test
    @DisplayName("Should get correct stats")
    void shouldGetCorrectStats() {
        circuitBreaker.recordSuccess();
        circuitBreaker.recordSuccess();
        circuitBreaker.recordFailure();

        CircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStats();

        assertEquals(CircuitBreaker.State.CLOSED, stats.getState());
        assertEquals(1, stats.getFailureCount());
        assertEquals(2, stats.getSuccessCount());
        assertEquals(3, stats.getTotalCount());
    }

    @Test
    @DisplayName("Should not trip below minimum request count")
    void shouldNotTripBelowMinimumRequestCount() {
        // Record failures but below minimum count (10)
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure();
        }

        // Should still be closed even though failure count < threshold
        // but failure rate is 100%
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }

    @Test
    @DisplayName("Should trip when failure count exceeds threshold")
    void shouldTripWhenFailureCountExceedsThreshold() {
        // First record enough successes to meet minimum count
        for (int i = 0; i < 10; i++) {
            circuitBreaker.recordSuccess();
        }

        // Then record failures exceeding threshold
        for (int i = 0; i < 5; i++) {
            circuitBreaker.recordFailure();
        }

        // Total: 10 success + 5 failure = 15
        // Failure rate: 5/15 = 33% < 50%
        // But failure count (5) >= threshold (5), should trip
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }

    @Test
    @DisplayName("Should trip when failure rate exceeds threshold")
    void shouldTripWhenFailureRateExceedsThreshold() {
        // Record 10 requests with > 50% failure
        for (int i = 0; i < 4; i++) {
            circuitBreaker.recordSuccess();
        }
        for (int i = 0; i < 6; i++) {
            circuitBreaker.recordFailure();
        }

        // Total: 10, Failures: 6, Rate: 60% > 50%
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }
}
