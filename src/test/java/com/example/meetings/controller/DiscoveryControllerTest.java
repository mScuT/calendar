package com.example.meetings.controller;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.discover.DiscoveryService;
import com.example.meetings.discover.EventProvider;
import com.example.meetings.model.User;
import com.example.meetings.service.MeetingService;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * REST API integration tests for DiscoveryController - Assignment point 3.
 *
 * DiscoveryService is replaced with a mock (@MockBean) so the test never calls the real event APIs.
 * MeetingService and the database are real. Covered behaviour:
 *  - GET /discover with a query puts the search results in the model;
 *  - POST /discover/copy adds the event to the user's calendar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserService userService;
    @Autowired
    private MeetingService meetingService;
    @MockBean
    private DiscoveryService discoveryService;

    private User alice;

    @BeforeEach
    void setUp() {
        alice = userService.register("alice", "alice@example.pt", "secret");
    }

    /** A query puts the search results into the model. */
    @Test
    @WithMockUser(username = "alice")
    void getDiscover_withQuery_putsResultsInModel() throws Exception {
        EventProvider configured = mock(EventProvider.class);
        when(configured.isConfigured()).thenReturn(true);
        when(discoveryService.providers()).thenReturn(List.of(configured));
        DiscoveredEvent event = new DiscoveredEvent("Ticketmaster", "ev1", "Portugal vs Congo",
                null, Instant.parse("2026-07-01T18:00:00Z"), null, "https://x", "MetLife Stadium");
        when(discoveryService.search("portugal")).thenReturn(List.of(event));

        mockMvc.perform(get("/discover").param("q", "portugal"))
                .andExpect(status().isOk())
                .andExpect(view().name("discover"))
                .andExpect(model().attribute("q", "portugal"))
                .andExpect(model().attribute("results", hasSize(1)));
    }

    /** Copying a discovered event adds it to the user's calendar. */
    @Test
    @WithMockUser(username = "alice")
    void postDiscoverCopy_addsEventToCalendar() throws Exception {
        mockMvc.perform(post("/discover/copy")
                        .param("source", "Ticketmaster")
                        .param("externalId", "ev1")
                        .param("title", "Portugal vs Congo")
                        .param("start", "2026-07-01T18:00:00Z")
                        .param("url", "https://x")
                        .param("venue", "MetLife Stadium")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        assertThat(meetingService.calendarFor(alice)).hasSize(1);
    }
}
