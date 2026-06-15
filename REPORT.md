# Report - VVS 2025/2026, Assignment 2

**Student:** Miguel Cut - number **56339**
**SUT:** Calendar - a meeting scheduler built with Spring Boot (MVC pattern)
**Base repository:** [https://github.com/alcides/calendar](https://github.com/alcides/calendar) · **Working branch:** `vvs02-56339`

---

## 1. Introduction

**Calendar** is a small Spring Boot meeting scheduler: you register an account, propose meetings,
accept invites, subscribe to a personal iCal feed, and discover real events from Ticketmaster,
SeatGeek and Lisbon's Agenda Cultural to copy onto your calendar.

This report documents the testing work done on this application for Assignment 2: the test setup,
any modifications made to make it testable, and the tests written at the unit, integration and
end-to-end levels.

## 2. Test setup

Test dependencies added to `pom.xml` (all `scope=test`):


| Dependency                 | What it provides                                                                     |
| -------------------------- | ------------------------------------------------------------------------------------ |
| `spring-boot-starter-test` | JUnit 5, Mockito, AssertJ and MockMvc                                                |
| `spring-security-test`     | authenticating requests and CSRF tokens in web tests (`@WithMockUser`, `csrf()`)     |
| `wiremock-standalone`      | a fake HTTP server for testing the event providers without calling the real database |
| `selenium-java`            | for the end-to-end tests                                                             |

## 3. SUT modifications

**TODO**

## 4. Unit tests of the business logic (Point 1)

JUnit 5 + AssertJ tests, with no Spring context, database or network. Mockito is used for the
classes that have dependencies (added as those classes are tested).

### ICalService

`ICalService` renders the VCALENDAR (`.ics`) feed and has no dependencies. `ICalServiceTest` covers:

- `render_writesValidVCalendarHeaderAndFooter` — the document starts with `BEGIN:VCALENDAR`, ends
  with `END:VCALENDAR`, and carries `VERSION:2.0`, `PRODID` and the owner's calendar name, all
  CRLF-terminated.
- `render_marksMeetingConfirmedWhenEveryoneAccepted` — `STATUS:CONFIRMED` when every participant
  accepted (also checks `DTSTART`/`DTEND`/`SUMMARY` formatting).
- `render_marksMeetingTentativeWhenSomeoneStillPending` — `STATUS:TENTATIVE` when at least one
  invitee is still pending.
- `render_mapsParticipantStatusToPartStat` — each attendee's invite status maps to the right value
  (`ACCEPTED` / `DECLINED` / `PENDING` → `NEEDS-ACTION`).
- `render_escapesSpecialCharactersPerRfc5545` — `\`, `;` and `,` are backslash-escaped, a newline
  becomes the two characters `\n`, and a carriage return is dropped. The test title contains all of
  them; the carriage return is included on purpose so the `\r`-stripping branch of `escape()` is
  exercised (otherwise that line could be deleted and the test would still pass).

## 5. Remaining work (TODO)

- [ ]  Unit tests of the business logic, with mocks (Point 1)
- [ ]  Integration tests with the 3rd party sources (Point 2)
- [ ]  Integration tests at the REST API level (Point 3)
- [ ]  Integration tests with the concrete database (Point 4)
- [ ]  End-to-end tests with Selenium (Point 5)
- [ ]  Continuous Integration (Point 6)
- [ ]  Code changes report and explanation
- [ ]  Bug detection write-up (Assessment, point c)
- [ ]  Conclusion
