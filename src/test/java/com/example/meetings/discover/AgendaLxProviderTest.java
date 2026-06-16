package com.example.meetings.discover;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for AgendaLxProvider - Assignment point 2 (integration with 3rd party sources).
 *
 * A WireMock server replaces the real AgendaLx (Lisbon) API: it returns fixed JSON. This provider
 * is the trickiest: the response is a JSON array, the date comes from an "occurences" list, the
 * time is read from free text like "qua: 21h30" (falling back to 20:00), the description is HTML
 * that gets cleaned, and there is no API key (isConfigured is always true). Covered behaviour:
 *  - valid JSON is parsed (date, time, HTML-cleaned description, venue from a map);
 *  - the time falls back to 20:00 when no time is found;
 *  - events whose dates are all in the past are skipped;
 *  - events with a blank title are skipped;
 *  - an HTTP error or invalid JSON gives back an empty list;
 *  - isConfigured() is always true and the search query is sent.
 */
class AgendaLxProviderTest {

    private static WireMockServer wireMock;

    @BeforeAll
    static void startServer() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopServer() {
        wireMock.stop();
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
    }

    private AgendaLxProvider provider() {
        return new AgendaLxProvider(wireMock.baseUrl());
    }

    /** Valid JSON is parsed: date from occurences, time from the text, HTML cleaned, venue from the map. */
    @Test
    void search_parsesEvent() {
        String json = """
                [
                  {
                    "id": 99,
                    "title": { "rendered": "Portugal vs Congo" },
                    "description": ["<p>World Cup 2026 <b>group stage</b>.</p>"],
                    "occurences": ["2026-07-01"],
                    "string_times": "qua: 21h30",
                    "link": "https://agendalx.pt/portugal-congo",
                    "venue": { "v1": { "name": "MetLife Stadium" } }
                  }
                ]
                """;
        wireMock.stubFor(get(urlPathEqualTo("/events")).willReturn(okJson(json)));

        List<DiscoveredEvent> results = provider().search("portugal");

        assertThat(results).hasSize(1);
        DiscoveredEvent e = results.get(0);
        assertThat(e.source()).isEqualTo("Agenda Cultural de Lisboa");
        assertThat(e.externalId()).isEqualTo("99");
        assertThat(e.title()).isEqualTo("Portugal vs Congo");
        assertThat(e.start()).isEqualTo(Instant.parse("2026-07-01T20:30:00Z")); // 21h30 Lisbon (UTC+1) = 20:30 UTC
        assertThat(e.url()).isEqualTo("https://agendalx.pt/portugal-congo");
        assertThat(e.venue()).isEqualTo("MetLife Stadium");
        assertThat(e.description()).doesNotContain("<").contains("World Cup 2026");
    }

    /** When no time can be read from the text, the time falls back to 20:00 (Lisbon). */
    @Test
    void search_fallsBackTo2000WhenNoTimeIsFound() {
        String json = """
                [
                  {
                    "id": 2,
                    "title": { "rendered": "Spain vs Brazil" },
                    "occurences": ["2026-07-02"]
                  }
                ]
                """;
        wireMock.stubFor(get(urlPathEqualTo("/events")).willReturn(okJson(json)));

        List<DiscoveredEvent> results = provider().search("x");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).start()).isEqualTo(Instant.parse("2026-07-02T19:00:00Z")); // 20:00 Lisbon = 19:00 UTC
    }

    /** An event whose dates are all in the past is skipped. */
    @Test
    void search_skipsEventsWithOnlyPastDates() {
        String json = """
                [
                  {
                    "id": 3,
                    "title": { "rendered": "France vs Germany" },
                    "occurences": ["2025-12-31"]
                  }
                ]
                """;
        wireMock.stubFor(get(urlPathEqualTo("/events")).willReturn(okJson(json)));

        assertThat(provider().search("x")).isEmpty();
    }

    /** An event with a blank title is skipped. */
    @Test
    void search_skipsEventsWithBlankTitle() {
        String json = """
                [
                  { "id": 4, "title": { "rendered": "" }, "occurences": ["2026-07-01"] }
                ]
                """;
        wireMock.stubFor(get(urlPathEqualTo("/events")).willReturn(okJson(json)));

        assertThat(provider().search("x")).isEmpty();
    }

    /** An HTTP 500 gives back an empty list, not an error. */
    @Test
    void search_returnsEmptyOnHttpError() {
        wireMock.stubFor(get(urlPathEqualTo("/events")).willReturn(aResponse().withStatus(500)));

        assertThat(provider().search("x")).isEmpty();
    }

    /** Invalid JSON gives back an empty list. */
    @Test
    void search_returnsEmptyOnInvalidJson() {
        wireMock.stubFor(get(urlPathEqualTo("/events")).willReturn(okJson("not an array")));

        assertThat(provider().search("x")).isEmpty();
    }

    /** This provider needs no key, so isConfigured() is always true. */
    @Test
    void isConfigured_isAlwaysTrue() {
        assertThat(provider().isConfigured()).isTrue();
    }

    /** The search query is sent as a query parameter. */
    @Test
    void search_sendsSearchQuery() {
        wireMock.stubFor(get(urlPathEqualTo("/events")).willReturn(okJson("[]")));

        provider().search("portugal");

        wireMock.verify(getRequestedFor(urlPathEqualTo("/events"))
                .withQueryParam("search", equalTo("portugal")));
    }
}
