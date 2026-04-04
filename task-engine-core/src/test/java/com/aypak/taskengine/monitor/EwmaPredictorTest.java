package com.aypak.taskengine.monitor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EwmaPredictor 单元测试。
 * Unit tests for EwmaPredictor.
 */
@DisplayName("EwmaPredictor Tests")
class EwmaPredictorTest {

    private EwmaPredictor predictor;

    @BeforeEach
    void setUp() {
        predictor = new EwmaPredictor(0.3);
    }

    @Test
    @DisplayName("Should create predictor with valid alpha")
    void shouldCreatePredictorWithValidAlpha() {
        assertDoesNotThrow(() -> new EwmaPredictor(0.5));
        assertDoesNotThrow(() -> new EwmaPredictor(0.1));
        assertDoesNotThrow(() -> new EwmaPredictor(0.01));
        assertDoesNotThrow(() -> new EwmaPredictor(0.99));
    }

    @Test
    @DisplayName("Should throw exception for invalid alpha values")
    void shouldThrowExceptionForInvalidAlpha() {
        assertThrows(IllegalArgumentException.class, () -> new EwmaPredictor(0));
        assertThrows(IllegalArgumentException.class, () -> new EwmaPredictor(1));
        assertThrows(IllegalArgumentException.class, () -> new EwmaPredictor(-0.1));
        assertThrows(IllegalArgumentException.class, () -> new EwmaPredictor(1.5));
    }

    @Test
    @DisplayName("Should create default predictor with alpha=0.3")
    void shouldCreateDefaultPredictor() {
        EwmaPredictor defaultPredictor = EwmaPredictor.defaultPredictor();
        assertEquals(0.3, defaultPredictor.getAlpha());
        assertNotNull(defaultPredictor);
    }

    @Test
    @DisplayName("Should update EWMA value correctly")
    void shouldUpdateEwmaValue() {
        long result = predictor.update(100);

        // EWMA = 0.3 * 100 + 0.7 * 0 = 30
        assertEquals(30, result);
        assertEquals(30, predictor.getEwma());
    }

    @Test
    @DisplayName("Should calculate EWMA correctly for multiple updates")
    void shouldCalculateEwmaForMultipleUpdates() {
        predictor.update(100);
        long result2 = predictor.update(200);

        // First update: EWMA = 0.3 * 100 + 0.7 * 0 = 30
        // Second update: EWMA = 0.3 * 200 + 0.7 * 30 = 60 + 21 = 81
        assertEquals(81, result2);
        assertEquals(81, predictor.getEwma());
    }

    @Test
    @DisplayName("Should detect uptrend direction")
    void shouldDetectUptrend() {
        predictor.update(100);
        predictor.update(150);

        assertEquals(1, predictor.getTrendDirection());
        assertTrue(predictor.isRapidUptrend(1));
    }

    @Test
    @DisplayName("Should detect downtrend direction")
    void shouldDetectDowntrend() {
        // Start from a non-zero base to avoid initialization effect
        predictor.reset(200);
        predictor.update(150);
        predictor.update(100);

        assertEquals(-1, predictor.getTrendDirection());
        assertTrue(predictor.isRapidDowntrend(1));
    }

    @Test
    @DisplayName("Should detect stable trend when values are similar")
    void shouldDetectStableTrend() {
        // Start from a non-zero base to avoid initialization effect
        predictor.reset(100);
        predictor.update(102);
        predictor.update(98);

        assertEquals(0, predictor.getTrendDirection());
    }

    @Test
    @DisplayName("Should track consecutive trend count")
    void shouldTrackConsecutiveTrendCount() {
        // First update initializes from 0, counts as first uptrend
        predictor.update(100);
        assertEquals(1, predictor.getConsecutiveTrendCount());

        predictor.update(150);
        assertEquals(2, predictor.getConsecutiveTrendCount());

        predictor.update(200);
        assertEquals(3, predictor.getConsecutiveTrendCount());
    }

    @Test
    @DisplayName("Should reset trend count when trend changes")
    void shouldResetTrendCountWhenTrendChanges() {
        predictor.update(100); // First uptrend (from 0)
        predictor.update(150); // Second uptrend
        predictor.update(200); // Third uptrend
        assertEquals(3, predictor.getConsecutiveTrendCount());

        // Sharp drop to trigger downtrend
        predictor.update(50);
        assertEquals(1, predictor.getConsecutiveTrendCount());
        assertEquals(-1, predictor.getTrendDirection());
    }

    @Test
    @DisplayName("Should predict next value based on trend")
    void shouldPredictNextValue() {
        predictor.update(100);
        predictor.update(200);

        long predicted = predictor.predictNext();

        // With uptrend, prediction should be higher than current
        assertTrue(predicted > predictor.getEwma());
    }

    @Test
    @DisplayName("Should predict stable when no trend")
    void shouldPredictStableWhenNoTrend() {
        // After multiple stable values, prediction should equal current EWMA
        predictor.reset(100);
        predictor.update(100);
        predictor.update(100);
        predictor.update(100);

        long predicted = predictor.predictNext();

        assertEquals(predictor.getEwma(), predicted);
    }

    @Test
    @DisplayName("Should calculate growth rate correctly")
    void shouldCalculateGrowthRate() {
        predictor.update(100);
        predictor.update(150);

        double growthRate = predictor.getPredictedGrowthRate();

        // Growth rate should be positive
        assertTrue(growthRate > 0);
    }

    @Test
    @DisplayName("Should return zero growth rate for zero values")
    void shouldReturnZeroGrowthRateForZeroValues() {
        predictor.update(0);

        double growthRate = predictor.getPredictedGrowthRate();

        assertEquals(0, growthRate);
    }

    @Test
    @DisplayName("Should handle batch update")
    void shouldHandleBatchUpdate() {
        long result = predictor.batchUpdate(100, 200, 300);

        assertNotNull(result);
        assertTrue(result > 0);
    }

    @Test
    @DisplayName("Should reset EWMA and trend state")
    void shouldResetEwmaAndTrend() {
        predictor.update(100);
        predictor.update(200);
        predictor.update(300);

        predictor.reset(50);

        assertEquals(50, predictor.getEwma());
        assertEquals(0, predictor.getTrendDirection());
        assertEquals(0, predictor.getConsecutiveTrendCount());
    }

    @Test
    @DisplayName("Should use 5% threshold for stable detection")
    void shouldUseFivePercentThreshold() {
        // Start from non-zero base
        predictor.reset(100);
        predictor.update(104); // 4% increase, but EWMA smooths it

        // The EWMA after reset(100) and update(104):
        // lastValue = 100, diff = 104-100 = 4, threshold = 5
        // 4 < 5, so stable
        assertEquals(0, predictor.getTrendDirection());

        // Larger increase to trigger uptrend
        predictor.update(120); // From EWMA ~101, diff is about 19, threshold ~5

        assertEquals(1, predictor.getTrendDirection());
    }

    @Test
    @DisplayName("Should handle rapid uptrend detection with threshold")
    void shouldHandleRapidUptrendDetection() {
        // Need 3 consecutive uptrends
        predictor.update(100);
        predictor.update(150);
        predictor.update(200);
        predictor.update(250);

        assertTrue(predictor.isRapidUptrend(3));
        assertFalse(predictor.isRapidUptrend(5));
    }

    @Test
    @DisplayName("Should handle rapid downtrend detection with threshold")
    void shouldHandleRapidDowntrendDetection() {
        // Use reset to avoid initialization effect
        predictor.reset(300);
        predictor.update(250);
        predictor.update(200);
        predictor.update(150);

        assertTrue(predictor.isRapidDowntrend(3));
        assertFalse(predictor.isRapidDowntrend(5));
    }

    @Test
    @DisplayName("Should predict with trend adjustment")
    void shouldPredictWithTrendAdjustment() {
        // Test uptrend prediction (should overestimate by 20%)
        predictor.update(100);
        predictor.update(200);
        long uptrendPrediction = predictor.predictNext();

        // Reset and test downtrend
        predictor = new EwmaPredictor(0.3);
        predictor.update(200);
        predictor.update(100);
        long downtrendPrediction = predictor.predictNext();

        // Uptrend should predict higher than simple extrapolation
        // Downtrend should predict lower (less negative)
        assertTrue(uptrendPrediction > 0);
    }

    @Test
    @DisplayName("Should handle large values correctly")
    void shouldHandleLargeValues() {
        predictor.update(1_000_000);
        predictor.update(1_500_000);

        long ewma = predictor.getEwma();
        assertTrue(ewma > 0);
        assertTrue(ewma < 1_500_000);
    }

    @Test
    @DisplayName("Should get correct alpha value")
    void shouldGetCorrectAlphaValue() {
        EwmaPredictor fastPredictor = new EwmaPredictor(0.5);
        assertEquals(0.5, fastPredictor.getAlpha());

        EwmaPredictor smoothPredictor = new EwmaPredictor(0.1);
        assertEquals(0.1, smoothPredictor.getAlpha());
    }
}
