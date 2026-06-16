package com.example.meetings.service;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ICalService - Assignment point 1 (business-logic unit tests).
 *
 * Covered behaviour:
 *  - VCALENDAR header/footer with CRLF line endings;
 *  - STATUS CONFIRMED when all accepted, TENTATIVE while someone is pending;
 *  - PARTSTAT mapping per invite status (ACCEPTED / DECLINED / PENDING -> NEEDS-ACTION);
 *  - RFC 5545 escaping of backslash, ';', ',', newline and carriage return.
 */
class ICalServiceTest {

    private final ICalService icalService = new ICalService();

    private static final Instant START = Instant.parse("2026-06-20T18:30:00Z");
    private static final Instant END   = Instant.parse("2026-06-20T20:00:00Z");

    private User user(String name) {
        return new User(name, name + "@example.pt", "hash");
    }

    /** An empty calendar must still produce a well-formed VCALENDAR document. */
    @Test
    void render_writesValidVCalendarHeaderAndFooter() {
        String ics = icalService.render(user("alice"), List.of());

        assertThat(ics)
                .startsWith("BEGIN:VCALENDAR\r\n")   // RFC 5545 mandates CRLF line endings
                .contains("VERSION:2.0")
                .contains("PRODID:-//meetings-app//EN")
                .contains("X-WR-CALNAME:alice's meetings")
                .endsWith("END:VCALENDAR\r\n");
    }

    /** When every participant has accepted, the event is CONFIRMED; also checks date/title output. */
    @Test
    void render_marksMeetingConfirmedWhenEveryoneAccepted() {
        User alice = user("alice");
        User bob = user("bob");
        Meeting m = new Meeting("Sprint review", "notes", START, END, alice);
        m.addParticipant(new MeetingParticipant(m, alice, InviteStatus.ACCEPTED));
        m.addParticipant(new MeetingParticipant(m, bob, InviteStatus.ACCEPTED));

        String ics = icalService.render(alice, List.of(m));

        assertThat(ics)
                .contains("DTSTART:20260620T183000Z")
                .contains("DTEND:20260620T200000Z")
                .contains("SUMMARY:Sprint review")
                .contains("STATUS:CONFIRMED");
    }

    /** A single still-pending invite keeps the event TENTATIVE. */
    @Test
    void render_marksMeetingTentativeWhenSomeoneStillPending() {
        User alice = user("alice");
        User bob = user("bob");
        Meeting m = new Meeting("Sprint review", null, START, END, alice);
        m.addParticipant(new MeetingParticipant(m, alice, InviteStatus.ACCEPTED));
        m.addParticipant(new MeetingParticipant(m, bob, InviteStatus.PENDING));

        String ics = icalService.render(alice, List.of(m));

        assertThat(ics).contains("STATUS:TENTATIVE");
    }

    /** Each participant's invite status renders as the matching iCal PARTSTAT. */
    @Test
    void render_mapsParticipantStatusToPartStat() {
        User alice = user("alice");
        User bob = user("bob");
        User carol = user("carol");
        Meeting m = new Meeting("PR Review", null, START, END, alice);
        m.addParticipant(new MeetingParticipant(m, alice, InviteStatus.ACCEPTED));
        m.addParticipant(new MeetingParticipant(m, bob, InviteStatus.DECLINED));
        m.addParticipant(new MeetingParticipant(m, carol, InviteStatus.PENDING));

        String ics = icalService.render(alice, List.of(m));

        assertThat(ics)
                .contains("CN=alice;PARTSTAT=ACCEPTED")
                .contains("CN=bob;PARTSTAT=DECLINED")
                .contains("CN=carol;PARTSTAT=NEEDS-ACTION");   // PENDING -> NEEDS-ACTION
    }

    /** Special characters in user text must be escaped so they cannot break the .ics structure. */
    @Test
    void render_escapesSpecialCharactersPerRfc5545() {
        User alice = user("alice");
        // A title with every char escape() touches: backslash, ';', ',', newline and carriage return.
        Meeting m = new Meeting("Demo \\ v2; release, final\r\nline2", null, START, END, alice);
        m.addParticipant(new MeetingParticipant(m, alice, InviteStatus.ACCEPTED));

        String ics = icalService.render(alice, List.of(m));

        // escape() per RFC 5545 §3.3.11: '\' -> "\\"; ';' -> "\;"; ',' -> "\,";
        // newline -> the two chars "\n"; carriage return -> dropped. The line is never split.
        assertThat(ics).contains("SUMMARY:Demo \\\\ v2\\; release\\, final\\nline2");
    }
}
