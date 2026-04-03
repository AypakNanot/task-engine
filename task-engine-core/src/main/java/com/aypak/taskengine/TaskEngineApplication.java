package com.aypak.taskengine;

import com.aypak.taskengine.config.TaskEngineAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Task Engine 主启动类。
 * Main entry point for Task Engine.
 */
@SpringBootApplication
@Import(TaskEngineAutoConfiguration.class)
public class TaskEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskEngineApplication.class, args);
    }
}
