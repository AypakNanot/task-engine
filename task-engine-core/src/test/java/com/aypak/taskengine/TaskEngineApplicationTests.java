package com.aypak.taskengine;

import com.aypak.taskengine.config.TaskEngineAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TaskEngineAutoConfiguration.class)
class TaskEngineApplicationTests {

    @Test
    void contextLoads() {
    }

}
