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
 * Integration tests for SeatGeekProvider - Assignment point 2 (integration with 3rd party sources).
 *
 * A WireMock server replaces the real SeatGeek API: it returns fixed JSON. The provider is pointed
 * at it through the configurable base URL (see report section 3). Covered behaviour:
 *  - valid JSON is parsed into a DiscoveredEvent;
 *  - datetime_utc has no time zone, so it is read as UTC;
 *  - the title falls back to short_title when title is missing;
 *  - events without a date are skipped;
 *  - an HTTP error or invalid JSON gives back an empty list;
 *  - the query and client_id are sent; isConfigured() and the no-key case.
 */
class SeatGeekProviderTest {

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

    private SeatGeekProvider provider(String clientId) {
        return new SeatGeekProvider(clientId, wireMock.baseUrl());
    }

    /** Valid JSON is mapped to a DiscoveredEvent; datetime_utc (no zone) is read as UTC. */
    @Test
    void search_parsesEventsAndReadsDatetimeAsUtc() {
        String json = """
                {
                  "events": [
                    {
                      "id": 4242,
                      "title": "Portugal vs Congo",
                      "datetime_utc": "2026-06-25T18:00:00",
                      "url": "https://sg.example/portugal-congo",
                      "description": "World Cup 2026 group stage",
                      "venue": { "name": "MetLife Stadium" }
                    }
                  ]
                }
                """;
        wireMock.stubFor(get(urlPathEqualTo("/events")).willReturn(okJson(json)));

        List<DiscoveredEvent> results = provider("client-123").search("portugal");

        assertThat(results).hasSize(1);
        DiscoveredEvent e = results.get(0);
        assertThat(e.source()).isEqualTo("SeatGeek");
        assertThat(e.externalId()).isEqualTo("4242");
        assertThat(e.title()).isEqualTo("Portugal vs Congo");
        assertThat(e.description()).isEqualTo("World Cup 2026 group stage");
        assertThat(e.start()).isEqualTo(Instant.parse("2026-06-25T18:00:00Z")); // no zone -> UTC
        assertThat(e.url()).isEqualTo("https://sg.example/portugal-congo");
        assertThat(e.venue()).isEqualTo("MetLife Stadium");
    }

    /** When the event has no title, short_title is used instead. */
    @Test
    void search_fallsBackToShortTitleWhenTitleIsMissing() {
        String json = """
                {
                  "events": [
                    { "id": 7, "short_title": "Portugal vs Congo", "datetime_utc": "2026-06-26T20:00:00" }
                  ]
                }
                """;
        wireMock.stubFor(get(urlPathEqualTo("/events")).willReturn(okJson(json)));

        List<DiscoveredEvent> results = provider("client-123").search("x");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Portugal vs Congo");
    }

    /** Events without a datetime_utc are dropped; dated events are kept. */
    @Test
    void search_skipsEventsWithoutADate() {
        String json = """
                {
                  "events": [
                    { "id": 1, "title": "Portugal vs Congo" },
                    { "id": 2, "title": "Spain vs Brazil", "datetime_utc": "2026-07-01T17:00:00" }
                  ]
                }
                """;
        wireMock.stubFor(get(urlPathEqualTo("/events")).willReturn(okJson(json)));

        List<DiscoveredEvent> results = provider("client-123").search("x");

        assertThat(results).extracting(DiscoveredEvent::externalId).containsExactly("2");
    }

    /** An HTTP 503 gives back an empty list, not an error. */
    @Test
    void search_returnsEmptyOnHttpError() {
        wireMock.stubFor(get(urlPathEqualTo("/events")).willReturn(aResponse().withStatus(503)));

        assertThat(provider("client-123").search("x")).isEmpty();
    }

    /** Invalid JSON gives back an empty list. */
    @Test
    void search_returnsEmptyOnInvalidJson() {
        wireMock.stubFor(get(urlPathEqualTo("/events")).willReturn(okJson("nonsense {")));

        assertThat(provider("client-123").search("x")).isEmpty();
    }

    /** The query and client id are sent as query parameters. */
    @Test
    void search_sendsQueryAndClientId() {
        wireMock.stubFor(get(urlPathEqualTo("/events")).willReturn(okJson("{}")));

        provider("client-123").search("portugal");

        wireMock.verify(getRequestedFor(urlPathEqualTo("/events"))
                .withQueryParam("q", equalTo("portugal"))
                .withQueryParam("client_id", equalTo("client-123")));
    }

    /** isConfigured() is true only when a client id is present. */
    @Test
    void isConfigured_reflectsClientIdPresence() {
        assertThat(provider("").isConfigured()).isFalse();
        assertThat(provider("client-123").isConfigured()).isTrue();
    }

    /** With no client id the provider returns empty and never calls the server. */
    @Test
    void search_returnsEmptyAndSkipsServerWhenNotConfigured() {
        assertThat(provider("").search("x")).isEmpty();

        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/events")));
    }
}
