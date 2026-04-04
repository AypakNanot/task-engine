package com.aypak.taskengine.demo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task Demo Controller 集成测试。
 * Task Demo Controller integration tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("TaskDemoController Integration Tests")
class TaskDemoControllerIntegrationTest {

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
    @DisplayName("Should return help information")
    void shouldReturnHelp() {
        String response = restClient.get()
                .uri(baseUrl + "/api/demo/help")
                .retrieve()
                .body(String.class);

        assertNotNull(response);
        assertTrue(response.contains("Task Engine Demo API"));
        assertTrue(response.contains("/api/demo/cpu"));
        assertTrue(response.contains("/api/demo/io"));
        assertTrue(response.contains("/api/demo/hybrid"));
        assertTrue(response.contains("/api/demo/batch"));
    }

    @Test
    @DisplayName("Should execute CPU-bound task")
    void shouldExecuteCpuTask() {
        TaskExecutionRequest request = new TaskExecutionRequest();
        request.setCount(1);
        request.setDurationMs(100L);

        ResponseEntity<TaskExecutionResponse> response = restClient.post()
                .uri(baseUrl + "/api/demo/cpu")
                .body(request)
                .retrieve()
                .toEntity(TaskExecutionResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        TaskExecutionResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("CPU_BOUND", body.getTaskType());
        assertEquals("submitted", body.getStatus());
    }

    @Test
    @DisplayName("Should execute IO-bound task")
    void shouldExecuteIoTask() {
        TaskExecutionRequest request = new TaskExecutionRequest();
        request.setCount(1);
        request.setDurationMs(50L);

        ResponseEntity<TaskExecutionResponse> response = restClient.post()
                .uri(baseUrl + "/api/demo/io")
                .body(request)
                .retrieve()
                .toEntity(TaskExecutionResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        TaskExecutionResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("IO_BOUND", body.getTaskType());
        assertEquals("submitted", body.getStatus());
    }

    @Test
    @DisplayName("Should execute Hybrid task")
    void shouldExecuteHybridTask() {
        TaskExecutionRequest request = new TaskExecutionRequest();
        request.setCount(1);
        request.setDurationMs(50L);

        ResponseEntity<TaskExecutionResponse> response = restClient.post()
                .uri(baseUrl + "/api/demo/hybrid")
                .body(request)
                .retrieve()
                .toEntity(TaskExecutionResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        TaskExecutionResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("HYBRID", body.getTaskType());
        assertEquals("submitted", body.getStatus());
    }

    @Test
    @DisplayName("Should execute Batch task")
    void shouldExecuteBatchTask() {
        ResponseEntity<TaskExecutionResponse> response = restClient.post()
                .uri(baseUrl + "/api/demo/batch?recordCount=100&count=1")
                .retrieve()
                .toEntity(TaskExecutionResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        TaskExecutionResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("BATCH", body.getTaskType());
        assertEquals("submitted", body.getStatus());
    }

    @Test
    @DisplayName("Should execute multiple CPU tasks")
    void shouldExecuteMultipleCpuTasks() {
        TaskExecutionRequest request = new TaskExecutionRequest();
        request.setCount(5);
        request.setDurationMs(10L);

        ResponseEntity<TaskExecutionResponse> response = restClient.post()
                .uri(baseUrl + "/api/demo/cpu")
                .body(request)
                .retrieve()
                .toEntity(TaskExecutionResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        TaskExecutionResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(5, body.getCount());
        assertEquals("Submitted 5 CPU-bound task(s)", body.getMessage());
    }
}
