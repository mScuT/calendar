package com.example.meetings.discover;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TicketmasterProvider - Assignment point 2 (integration with 3rd party sources).
 *
 * A WireMock server replaces the real Ticketmaster API: it returns fixed JSON.
 * The provider is pointed at it through the configurable base URL (see the
 * SUT modification in report section 3). Covered behaviour:
 *  - valid JSON is parsed into DiscoveredEvents (title, date, venue, url, id);
 *  - events without a start date ("TBA") are skipped;
 *  - an HTTP error or invalid JSON gives back an empty list (the page never breaks);
 *  - the country-code filter is sent, and left out when blank;
 *  - isConfigured() and the no-key case (returns empty without calling the server).
 */
class TicketmasterProviderTest {

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

    private TicketmasterProvider provider(String apiKey, String countryCode) {
        return new TicketmasterProvider(apiKey, countryCode, wireMock.baseUrl());
    }

    /** Valid JSON is turned into a DiscoveredEvent with all fields mapped. */
    @Test
    void search_parsesEventsFromJson() {
        String json = """
                {
                  "_embedded": {
                    "events": [
                      {
                        "id": "ev1",
                        "name": "Portugal vs Congo",
                        "url": "https://tm.example/portugal-congo",
                        "info": "World Cup 2026 group stage",
                        "dates": { "start": { "dateTime": "2026-07-01T19:00:00Z" } },
                        "_embedded": { "venues": [ { "name": "MetLife Stadium" } ] }
                      }
                    ]
                  }
                }
                """;
        wireMock.stubFor(get(urlPathEqualTo("/events.json")).willReturn(okJson(json)));

        List<DiscoveredEvent> results = provider("test-key", "PT").search("portugal");

        assertThat(results).hasSize(1);
        DiscoveredEvent e = results.get(0);
        assertThat(e.source()).isEqualTo("Ticketmaster");
        assertThat(e.externalId()).isEqualTo("ev1");
        assertThat(e.title()).isEqualTo("Portugal vs Congo");
        assertThat(e.description()).isEqualTo("World Cup 2026 group stage");
        assertThat(e.start()).isEqualTo(Instant.parse("2026-07-01T19:00:00Z"));
        assertThat(e.url()).isEqualTo("https://tm.example/portugal-congo");
        assertThat(e.venue()).isEqualTo("MetLife Stadium");
    }

    /** Events with no start dateTime (TBA) are dropped; dated events are kept. */
    @Test
    void search_skipsEventsWithoutAStartDate() {
        String json = """
                {
                  "_embedded": {
                    "events": [
                      { "id": "tba", "name": "Spain vs Brazil", "dates": { "start": { } } },
                      { "id": "ok", "name": "France vs Germany",
                        "dates": { "start": { "dateTime": "2026-08-10T20:00:00Z" } } }
                    ]
                  }
                }
                """;
        wireMock.stubFor(get(urlPathEqualTo("/events.json")).willReturn(okJson(json)));

        List<DiscoveredEvent> results = provider("test-key", "PT").search("x");

        assertThat(results).extracting(DiscoveredEvent::externalId).containsExactly("ok");
    }

    /** An HTTP 500 gives back an empty list, not an error. */
    @Test
    void search_returnsEmptyOnHttpError() {
        wireMock.stubFor(get(urlPathEqualTo("/events.json")).willReturn(aResponse().withStatus(500)));

        assertThat(provider("test-key", "PT").search("x")).isEmpty();
    }

    /** Invalid JSON gives back an empty list. */
    @Test
    void search_returnsEmptyOnInvalidJson() {
        wireMock.stubFor(get(urlPathEqualTo("/events.json")).willReturn(okJson("{ not valid json ")));

        assertThat(provider("test-key", "PT").search("x")).isEmpty();
    }

    /** The keyword, api key and country code are sent as query parameters. */
    @Test
    void search_sendsCountryCodeAndQueryParams() {
        wireMock.stubFor(get(urlPathEqualTo("/events.json")).willReturn(okJson("{}")));

        provider("test-key", "US").search("portugal");

        wireMock.verify(getRequestedFor(urlPathEqualTo("/events.json"))
                .withQueryParam("keyword", equalTo("portugal"))
                .withQueryParam("apikey", equalTo("test-key"))
                .withQueryParam("countryCode", equalTo("US")));
    }

    /** A blank country code leaves the countryCode parameter out of the request. */
    @Test
    void search_omitsCountryCodeWhenBlank() {
        wireMock.stubFor(get(urlPathEqualTo("/events.json")).willReturn(okJson("{}")));

        provider("test-key", "").search("x");

        wireMock.verify(getRequestedFor(urlPathEqualTo("/events.json"))
                .withQueryParam("countryCode", absent()));
    }

    /** isConfigured() is true only when an api key is present. */
    @Test
    void isConfigured_reflectsApiKeyPresence() {
        assertThat(provider("", "PT").isConfigured()).isFalse();
        assertThat(provider("a-key", "PT").isConfigured()).isTrue();
    }

    /** With no api key the provider returns empty and never calls the server. */
    @Test
    void search_returnsEmptyAndSkipsServerWhenNotConfigured() {
        assertThat(provider("", "PT").search("x")).isEmpty();

        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/events.json")));
    }
}
