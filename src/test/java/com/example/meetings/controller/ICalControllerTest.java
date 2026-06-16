package com.example.meetings.controller;

import com.example.meetings.model.User;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST API integration tests for ICalController - Assignment point 3.
 *
 * The /ical feed is public (no login), so these tests need no @WithMockUser. They use the real
 * services and test database. This class has the same setup as the other controller tests (no
 * @MockBean), so it reuses the same cached Spring context. Covered behaviour:
 *  - a valid token returns a text/calendar VCALENDAR feed;
 *  - an unknown token returns 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ICalControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserService userService;

    private User alice;

    @BeforeEach
    void setUp() {
        alice = userService.register("alice", "alice@example.pt", "secret");
    }

    /** A valid iCal token returns the calendar feed. */
    @Test
    void getIcalFeed_validToken_returnsCalendar() throws Exception {
        mockMvc.perform(get("/ical/" + alice.getIcalToken() + ".ics"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andExpect(content().string(containsString("BEGIN:VCALENDAR")));
    }

    /** An unknown iCal token returns 404. */
    @Test
    void getIcalFeed_unknownToken_returns404() throws Exception {
        mockMvc.perform(get("/ical/not-a-real-token.ics"))
                .andExpect(status().isNotFound());
    }
}
