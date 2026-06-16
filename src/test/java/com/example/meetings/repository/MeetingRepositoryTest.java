package com.example.meetings.repository;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Database integration tests for MeetingRepository - Assignment point 4.
 *
 * A meeting is on a user's calendar when they organize it OR take part with a status other than DECLINED.
 * We build three meetings with different statuses (and start times out of order) and check both who sees
 * what and the ordering by start time.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MeetingRepositoryTest {

    @Autowired
    private MeetingRepository meetingRepository;
    @Autowired
    private TestEntityManager entityManager;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = entityManager.persist(new User("alice", "alice@example.pt", "hash"));
        bob = entityManager.persist(new User("bob", "bob@example.pt", "hash"));

        // alice organizes; starts last (Jul 10).
        Meeting organized = meeting("Portugal vs Congo", Instant.parse("2026-07-10T18:00:00Z"), alice);
        organized.addParticipant(new MeetingParticipant(organized, alice, InviteStatus.ACCEPTED));
        organized.addParticipant(new MeetingParticipant(organized, bob, InviteStatus.ACCEPTED));

        // bob organizes; alice DECLINED; starts in the middle (Jul 5).
        Meeting declinedByAlice = meeting("Spain vs Brazil", Instant.parse("2026-07-05T18:00:00Z"), bob);
        declinedByAlice.addParticipant(new MeetingParticipant(declinedByAlice, bob, InviteStatus.ACCEPTED));
        declinedByAlice.addParticipant(new MeetingParticipant(declinedByAlice, alice, InviteStatus.DECLINED));

        // bob organizes; alice PENDING; starts first (Jul 1).
        Meeting pendingForAlice = meeting("France vs Germany", Instant.parse("2026-07-01T18:00:00Z"), bob);
        pendingForAlice.addParticipant(new MeetingParticipant(pendingForAlice, bob, InviteStatus.ACCEPTED));
        pendingForAlice.addParticipant(new MeetingParticipant(pendingForAlice, alice, InviteStatus.PENDING));

        entityManager.persist(organized);
        entityManager.persist(declinedByAlice);
        entityManager.persist(pendingForAlice);
        entityManager.flush();
    }

    private static Meeting meeting(String title, Instant start, User organizer) {
        return new Meeting(title, null, start, start.plusSeconds(3600), organizer);
    }

    /** A user's calendar holds meetings they organize or join (not DECLINED), ordered by start. */
    @Test
    void findCalendarMeetings_includesOrganizedAndNonDeclined_orderedByStart() {
        assertThat(meetingRepository.findCalendarMeetings(alice))
                .extracting(Meeting::getTitle)
                // France vs Germany (Jul 1, pending) before Portugal vs Congo (Jul 10, organizer);
                // Spain vs Brazil is left out because alice declined it.
                .containsExactly("France vs Germany", "Portugal vs Congo");
    }

    /** A meeting a user declined still stays on the organizer's calendar. */
    @Test
    void findCalendarMeetings_keepsDeclinedMeetingOnTheOrganizerCalendar() {
        assertThat(meetingRepository.findCalendarMeetings(bob))
                .extracting(Meeting::getTitle)
                // bob sees all three (ordered by start), including the one alice declined.
                .containsExactly("France vs Germany", "Spain vs Brazil", "Portugal vs Congo");
    }
}
