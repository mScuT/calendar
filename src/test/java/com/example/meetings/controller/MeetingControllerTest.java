package com.example.meetings.controller;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.service.MeetingService;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * REST API integration tests for MeetingController and CalendarController - Assignment point 3.
 *
 * Boots the whole app and calls it through MockMvc as a logged-in user (@WithMockUser). The user
 * must also exist in the database, because the controllers look it up, so two users are created in
 * setUp(). @Transactional rolls back the data after each test. Covered behaviour:
 *  - GET /calendar shows the user's meetings;
 *  - GET and POST /meetings/new (valid input creates and redirects; end before start shows an error);
 *  - POST /meetings/{id}/respond accepts an invite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MeetingControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserService userService;
    @Autowired
    private MeetingService meetingService;
    @Autowired
    private MeetingParticipantRepository participantRepository;

    private static final Instant START = Instant.parse("2026-07-01T18:00:00Z");
    private static final Instant END = Instant.parse("2026-07-01T20:00:00Z");

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = userService.register("alice", "alice@example.pt", "secret");
        bob = userService.register("bob", "bob@example.pt", "secret");
    }

    /** The authenticated user's calendar shows their meetings. */
    @Test
    @WithMockUser(username = "alice")
    void getCalendar_showsTheUsersMeetings() throws Exception {
        meetingService.propose(alice, "Portugal vs Congo", null, START, END, List.of());

        mockMvc.perform(get("/calendar"))
                .andExpect(status().isOk())
                .andExpect(view().name("calendar"))
                .andExpect(model().attribute("meetings", hasSize(1)))
                .andExpect(model().attributeExists("pendingInvites", "user"));
    }

    /** The calendar shows the user's pending invites. */
    @Test
    @WithMockUser(username = "alice")
    void getCalendar_showsPendingInvites() throws Exception {
        // bob organizes a meeting and invites alice, so alice has a pending invite.
        meetingService.propose(bob, "Portugal vs Congo", null, START, END, List.of("alice"));

        mockMvc.perform(get("/calendar"))
                .andExpect(status().isOk())
                .andExpect(view().name("calendar"))
                .andExpect(model().attribute("pendingInvites", hasSize(1)));
    }

    /** GET /meetings/new returns the propose form. */
    @Test
    @WithMockUser(username = "alice")
    void getMeetingsNew_returnsProposeView() throws Exception {
        mockMvc.perform(get("/meetings/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("propose"));
    }

    /** Valid input creates the meeting and redirects to the calendar. */
    @Test
    @WithMockUser(username = "alice")
    void postMeetingsNew_validInput_createsMeetingAndRedirects() throws Exception {
        mockMvc.perform(post("/meetings/new")
                        .param("title", "Portugal vs Congo")
                        .param("start", "2026-07-01T18:00")
                        .param("end", "2026-07-01T20:00")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        assertThat(meetingService.calendarFor(alice)).hasSize(1);
    }

    /** End before start shows the propose view again with an error and saves nothing. */
    @Test
    @WithMockUser(username = "alice")
    void postMeetingsNew_endBeforeStart_returnsProposeViewWithError() throws Exception {
        mockMvc.perform(post("/meetings/new")
                        .param("title", "Portugal vs Congo")
                        .param("start", "2026-07-01T20:00")
                        .param("end", "2026-07-01T18:00")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("propose"))
                .andExpect(model().attributeExists("error"));

        assertThat(meetingService.calendarFor(alice)).isEmpty();
    }

    /** An invitee accepting their invite updates the participant status. */
    @Test
    @WithMockUser(username = "bob")
    void postRespond_accept_marksInviteAccepted() throws Exception {
        Meeting meeting = meetingService.propose(alice, "Portugal vs Congo", null, START, END, List.of("bob"));

        mockMvc.perform(post("/meetings/" + meeting.getId() + "/respond")
                        .param("action", "accept")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        MeetingParticipant invite = participantRepository
                .findByMeetingIdAndUserId(meeting.getId(), bob.getId()).orElseThrow();
        assertThat(invite.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
    }
}
