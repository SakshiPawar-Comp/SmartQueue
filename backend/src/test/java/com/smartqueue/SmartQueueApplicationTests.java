package com.smartqueue;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartqueue.platform.PlatformController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SmartQueueApplicationTests {

    @Autowired
    private PlatformController platformController;

    @Test
    void contextLoads() {
        assertThat(platformController).isNotNull();
    }
}
