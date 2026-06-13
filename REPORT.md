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

## 4. Remaining work (TODO)

- [ ]  Unit tests of the business logic, with mocks (Point 1)
- [ ]  Integration tests with the 3rd party sources (Point 2)
- [ ]  Integration tests at the REST API level (Point 3)
- [ ]  Integration tests with the concrete database (Point 4)
- [ ]  End-to-end tests with Selenium (Point 5)
- [ ]  Continuous Integration (Point 6)
- [ ]  Code changes report and explanation
- [ ]  Bug detection write-up (Assessment, point c)
- [ ]  Conclusion
