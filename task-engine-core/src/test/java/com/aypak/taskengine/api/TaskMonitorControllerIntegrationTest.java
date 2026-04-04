package com.aypak.taskengine.api;

import com.aypak.taskengine.config.TaskEngineProperties;
import com.aypak.taskengine.executor.SharedPoolManager;
import com.aypak.taskengine.executor.TaskEngineImpl;
import com.aypak.taskengine.monitor.MetricsCollector;
import com.aypak.taskengine.monitor.PoolStatsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 线程池监控 API 集成测试。
 * Thread pool monitoring API integration tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("TaskMonitorController API Integration Tests")
class TaskMonitorControllerIntegrationTest {

    @LocalServerPort
    private int port;

    private RestClient restClient;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        restClient = RestClient.create();
        baseUrl = "http://localhost:" + port;
    }

    @Test
    @DisplayName("Should return all thread pools status")
    void shouldReturnAllThreadPoolsStatus() {
        ResponseEntity<List> response = restClient.get()
                .uri(baseUrl + "/monitor/task/pools")
                .retrieve()
                .toEntity(List.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<?> pools = response.getBody();
        assertNotNull(pools);
        // 应该有 4 个池（CPU_BOUND, IO_BOUND, HYBRID, BATCH）- SCHEDULED 不包含在内
        assertTrue(pools.size() >= 4, "Should have at least 4 thread pools");
    }

    @Test
    @DisplayName("Should return specific thread pool status")
    void shouldReturnSpecificThreadPoolStatus() {
        ResponseEntity<PoolStatsResponse> response = restClient.get()
                .uri(baseUrl + "/monitor/task/pool/CPU_BOUND")
                .retrieve()
                .toEntity(PoolStatsResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        PoolStatsResponse pool = response.getBody();
        assertNotNull(pool);
        assertEquals("CPU_BOUND", pool.getTaskType());
        assertTrue(pool.getCorePoolSize() > 0);
        assertTrue(pool.getMaxPoolSize() > 0);
    }

    @Test
    @DisplayName("Should return 400 for invalid task type")
    void shouldReturn400ForInvalidTaskType() {
        try {
            restClient.get()
                    .uri(baseUrl + "/monitor/task/pool/INVALID_TYPE")
                    .retrieve()
                    .toBodilessEntity();
            fail("Should throw HttpClientErrorException");
        } catch (Exception e) {
            assertTrue(e instanceof org.springframework.web.client.HttpClientErrorException);
            assertEquals(HttpStatus.BAD_REQUEST,
                    ((org.springframework.web.client.HttpClientErrorException) e).getStatusCode());
        }
    }

    @Test
    @DisplayName("Should return pool with correct utilization fields")
    void shouldReturnPoolWithCorrectUtilizationFields() {
        ResponseEntity<PoolStatsResponse> response = restClient.get()
                .uri(baseUrl + "/monitor/task/pool/IO_BOUND")
                .retrieve()
                .toEntity(PoolStatsResponse.class);

        PoolStatsResponse pool = response.getBody();
        assertNotNull(pool);

        // 验证所有字段都存在
        assertNotNull(pool.getTaskType());
        assertTrue(pool.getCorePoolSize() > 0);
        assertTrue(pool.getMaxPoolSize() > 0);
        assertTrue(pool.getActiveThreads() >= 0);
        assertTrue(pool.getQueueSize() >= 0);
        assertTrue(pool.getQueueCapacity() >= 0);
        assertTrue(pool.getQueueUtilization() >= 0);
        assertTrue(pool.getThreadUtilization() >= 0);
        assertFalse(pool.isShuttingDown());
        assertFalse(pool.isTerminated());
    }

    @Test
    @DisplayName("Should return all pool types")
    void shouldReturnAllPoolTypes() {
        ResponseEntity<List> response = restClient.get()
                .uri(baseUrl + "/monitor/task/pools")
                .retrieve()
                .toEntity(List.class);

        List<?> pools = response.getBody();
        assertNotNull(pools);

        // 验证包含所有预期的池类型
        boolean hasCpuBound = false;
        boolean hasIoBound = false;
        boolean hasHybrid = false;
        boolean hasBatch = false;

        for (Object pool : pools) {
            if (pool instanceof java.util.Map) {
                String taskType = (String) ((java.util.Map<?, ?>) pool).get("taskType");
                if ("CPU_BOUND".equals(taskType)) hasCpuBound = true;
                if ("IO_BOUND".equals(taskType)) hasIoBound = true;
                if ("HYBRID".equals(taskType)) hasHybrid = true;
                if ("BATCH".equals(taskType)) hasBatch = true;
            }
        }

        assertTrue(hasCpuBound, "Should have CPU_BOUND pool");
        assertTrue(hasIoBound, "Should have IO_BOUND pool");
        assertTrue(hasHybrid, "Should have HYBRID pool");
        assertTrue(hasBatch, "Should have BATCH pool");
    }

    @Test
    @DisplayName("Should not include SCHEDULED type in pools endpoint")
    void shouldNotIncludeScheduledTypeInPoolsEndpoint() {
        ResponseEntity<List> response = restClient.get()
                .uri(baseUrl + "/monitor/task/pools")
                .retrieve()
                .toEntity(List.class);

        List<?> pools = response.getBody();
        assertNotNull(pools);

        // 验证 SCHEDULED 类型不在返回结果中
        for (Object pool : pools) {
            if (pool instanceof java.util.Map) {
                String taskType = (String) ((java.util.Map<?, ?>) pool).get("taskType");
                assertNotEquals("SCHEDULED", taskType, "SCHEDULED should not be in pools response");
            }
        }
    }
}
