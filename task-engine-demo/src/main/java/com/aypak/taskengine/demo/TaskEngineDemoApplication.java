package com.aypak.taskengine.demo;

import com.aypak.taskengine.config.TaskEngineAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * Task Engine Demo 应用入口。
 * Task Engine Demo Application entry point.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.aypak.taskengine.demo")
@Import(TaskEngineAutoConfiguration.class)
public class TaskEngineDemoApplication {

    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(TaskEngineDemoApplication.class, args);
    }
}
