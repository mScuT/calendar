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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Database integration tests for MeetingParticipantRepository - Assignment point 4.
 *
 * alice has two invites: PENDING in one meeting and ACCEPTED in another. The tests check the two
 * finder queries and the cascade: we only ever save the meetings, and their participants are saved
 * with them (Meeting has cascade = ALL).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MeetingParticipantRepositoryTest {

    @Autowired
    private MeetingParticipantRepository participantRepository;
    @Autowired
    private TestEntityManager entityManager;

    private User alice;
    private Meeting pendingMeeting;

    @BeforeEach
    void setUp() {
        alice = entityManager.persist(new User("alice", "alice@example.pt", "hash"));
        User bob = entityManager.persist(new User("bob", "bob@example.pt", "hash"));

        // bob organizes; alice is PENDING here.
        pendingMeeting = meeting("Portugal vs Congo", bob);
        pendingMeeting.addParticipant(new MeetingParticipant(pendingMeeting, bob, InviteStatus.ACCEPTED));
        pendingMeeting.addParticipant(new MeetingParticipant(pendingMeeting, alice, InviteStatus.PENDING));

        // bob organizes; alice is ACCEPTED here.
        Meeting acceptedMeeting = meeting("Spain vs Brazil", bob);
        acceptedMeeting.addParticipant(new MeetingParticipant(acceptedMeeting, bob, InviteStatus.ACCEPTED));
        acceptedMeeting.addParticipant(new MeetingParticipant(acceptedMeeting, alice, InviteStatus.ACCEPTED));

        entityManager.persist(pendingMeeting);
        entityManager.persist(acceptedMeeting);
        entityManager.flush();
    }

    private static Meeting meeting(String title, User organizer) {
        Instant start = Instant.parse("2026-07-01T18:00:00Z");
        return new Meeting(title, null, start, start.plusSeconds(3600), organizer);
    }

    /** findByUserAndStatus returns only the invites with that status. */
    @Test
    void findByUserAndStatus_returnsOnlyTheMatchingStatus() {
        List<MeetingParticipant> pending = participantRepository.findByUserAndStatus(alice, InviteStatus.PENDING);

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getMeeting().getTitle()).isEqualTo("Portugal vs Congo");
        // alice's other invite is ACCEPTED, so a DECLINED search finds nothing.
        assertThat(participantRepository.findByUserAndStatus(alice, InviteStatus.DECLINED)).isEmpty();
    }

    /** findByMeetingIdAndUserId returns the one participant for that meeting and user. */
    @Test
    void findByMeetingIdAndUserId_returnsTheRightParticipant() {
        Optional<MeetingParticipant> found =
                participantRepository.findByMeetingIdAndUserId(pendingMeeting.getId(), alice.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(InviteStatus.PENDING);
        // a meeting/user combo that does not exist returns empty.
        assertThat(participantRepository.findByMeetingIdAndUserId(pendingMeeting.getId(), 9999L)).isEmpty();
    }

    /** Saving a meeting also saves its participants (cascade). */
    @Test
    void savingMeetingCascadesItsParticipants() {
        // We never saved a participant directly; two meetings with two participants each gives four.
        assertThat(participantRepository.count()).isEqualTo(4);
    }
}
