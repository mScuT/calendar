package com.example.meetings;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Boots the whole Spring context with the test profile (in-memory H2). This proves the
 * test database configuration works before the REST and database integration tests build on it.
 */
@SpringBootTest
@ActiveProfiles("test")
class MeetingsApplicationTests {

    @Test
    void contextLoads() {
    }
}
