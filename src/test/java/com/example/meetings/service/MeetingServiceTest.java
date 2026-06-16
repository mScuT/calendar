package com.example.meetings.service;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MeetingService - Assignment point 1 (business-logic unit tests).
 *
 * MeetingService is the main service. Its three repositories are mocked so the scheduling
 * rules are tested in isolation. Covered behaviour:
 *  - propose(): rejects end <= start; organizer auto-accepts while invitees are PENDING;
 *    removes duplicate invitees and skips the organizer and blank names; unknown invitee throws;
 *  - respond(): only ACCEPTED/DECLINED allowed; throws when the user has no invite; updates status;
 *  - copyFromDiscovered(): defaults to a 2h duration when the event has no end; builds the description;
 *  - calendarForIcalToken(): throws on an unknown token.
 */
@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock private MeetingRepository meetingRepository;
    @Mock private MeetingParticipantRepository participantRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private MeetingService meetingService;

    private static final Instant START = Instant.parse("2026-06-20T18:30:00Z");
    private static final Instant END   = Instant.parse("2026-06-20T20:00:00Z");

    private static User user(String name) {
        return new User(name, name + "@example.pt", "hash");
    }

    /** Make save() echo its argument so a test can inspect the Meeting that would be persisted. */
    private void stubSaveEchoesMeeting() {
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static MeetingParticipant participantOf(Meeting m, String username) {
        return m.getParticipants().stream()
                .filter(p -> p.getUser().getUsername().equals(username))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no participant for " + username));
    }

    // ----- propose -----

    /** End time must be strictly after start; otherwise nothing is created or saved. */
    @Test
    void propose_rejectsEndNotAfterStart() {
        User alice = user("alice");
        Instant start = Instant.parse("2026-06-20T20:00:00Z");
        Instant endBeforeStart = Instant.parse("2026-06-20T18:30:00Z");

        assertThatThrownBy(() ->
                meetingService.propose(alice, "Sync", null, start, endBeforeStart, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("after start");

        verify(meetingRepository, never()).save(any());
    }

    /** The organizer auto-accepts (ACCEPTED); every invitee starts PENDING. */
    @Test
    void propose_organizerAutoAcceptsAndInviteesArePending() {
        User alice = user("alice");
        User bob = user("bob");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        stubSaveEchoesMeeting();

        Meeting m = meetingService.propose(alice, "Sync", "desc", START, END, List.of("bob"));

        assertThat(m.getParticipants()).hasSize(2);
        assertThat(participantOf(m, "alice").getStatus()).isEqualTo(InviteStatus.ACCEPTED);
        assertThat(participantOf(m, "bob").getStatus()).isEqualTo(InviteStatus.PENDING);
        assertThat(m.getOrganizer()).isSameAs(alice);
    }

    /** Duplicate names, the organizer's own name, and blank/null entries are all ignored. */
    @Test
    void propose_deduplicatesInviteesAndSkipsOrganizerAndBlanks() {
        User alice = user("alice");
        User bob = user("bob");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        stubSaveEchoesMeeting();

        List<String> messy = Arrays.asList("bob", "bob", "alice", "", "   ", null);
        Meeting m = meetingService.propose(alice, "Sync", null, START, END, messy);

        // Only alice (organizer) + bob survive.
        assertThat(m.getParticipants()).hasSize(2);
        // bob is looked up exactly once despite appearing twice; the organizer is never looked up.
        verify(userRepository).findByUsername("bob");
        verify(userRepository, never()).findByUsername("alice");
    }

    /** An invitee that does not exist stops the whole proposal. */
    @Test
    void propose_throwsOnUnknownInvitee() {
        User alice = user("alice");
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                meetingService.propose(alice, "Sync", null, START, END, List.of("unknown")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown invitee");

        verify(meetingRepository, never()).save(any());
    }

    // ----- respond -----

    /** A response can only be ACCEPTED or DECLINED - PENDING (or anything else) is rejected. */
    @Test
    void respond_rejectsStatusOtherThanAcceptedOrDeclined() {
        assertThatThrownBy(() ->
                meetingService.respond(1L, user("alice"), InviteStatus.PENDING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACCEPTED or DECLINED");

        verify(participantRepository, never()).findByMeetingIdAndUserId(any(), any());
    }

    /** Responding without an existing invite throws. */
    @Test
    void respond_throwsWhenNoInviteExists() {
        User alice = user("alice");
        when(participantRepository.findByMeetingIdAndUserId(1L, alice.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.respond(1L, alice, InviteStatus.ACCEPTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No invite found");
    }

    /** A valid response updates the participant's status. */
    @Test
    void respond_updatesParticipantStatus() {
        User alice = user("alice");
        Meeting meeting = new Meeting("Sync", null, START, END, alice);
        MeetingParticipant invite = new MeetingParticipant(meeting, alice, InviteStatus.PENDING);
        when(participantRepository.findByMeetingIdAndUserId(1L, alice.getId()))
                .thenReturn(Optional.of(invite));

        meetingService.respond(1L, alice, InviteStatus.ACCEPTED);

        assertThat(invite.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
    }

    // ----- copyFromDiscovered -----

    /** An event without an end time defaults to a 2-hour duration; the user is the only attendee. */
    @Test
    void copyFromDiscovered_defaultsToTwoHoursWhenNoEnd() {
        User alice = user("alice");
        DiscoveredEvent event = new DiscoveredEvent(
                "Ticketmaster", "ext1", "Concert", null, START, null, "http://x", "Arena");
        stubSaveEchoesMeeting();

        Meeting m = meetingService.copyFromDiscovered(alice, event);

        assertThat(m.getStartTime()).isEqualTo(START);
        assertThat(m.getEndTime()).isEqualTo(START.plus(Duration.ofHours(2)));
        assertThat(m.getParticipants()).hasSize(1);
        assertThat(participantOf(m, "alice").getStatus()).isEqualTo(InviteStatus.ACCEPTED);
    }

    /** The description includes event description, venue, source and url; a given end time is kept. */
    @Test
    void copyFromDiscovered_buildsDescriptionAndKeepsEnd() {
        User alice = user("alice");
        DiscoveredEvent event = new DiscoveredEvent(
                "SeatGeek", "ext2", "Game", "Portugal vs Rep. Congo", START, END, "http://game", "Stadium");
        stubSaveEchoesMeeting();

        Meeting m = meetingService.copyFromDiscovered(alice, event);

        assertThat(m.getEndTime()).isEqualTo(END); // explicit end kept, not defaulted
        assertThat(m.getDescription())
                .contains("Portugal vs Rep. Congo")
                .contains("Venue: Stadium")
                .contains("Source: SeatGeek")
                .contains("(http://game)");
    }

    // ----- calendarForIcalToken -----

    /** An unknown iCal token is rejected. */
    @Test
    void calendarForIcalToken_throwsOnUnknownToken() {
        when(userRepository.findByIcalToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> meetingService.calendarForIcalToken("bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid iCal token");
    }
}
