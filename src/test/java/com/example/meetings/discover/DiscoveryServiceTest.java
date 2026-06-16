package com.example.meetings.discover;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DiscoveryService - Assignment point 1 (business-logic unit tests).
 *
 * DiscoveryService sends the query to several EventProviders and combines their results. The
 * providers are Mockito mocks, so there are no real network calls here. Covered behaviour:
 *  - a null or blank query returns an empty list without calling any provider;
 *  - providers that are not configured are skipped;
 *  - duplicate events are removed by URL; when the URL is null, source + externalId is used instead;
 *  - the results are sorted by start time.
 */
@ExtendWith(MockitoExtension.class)
class DiscoveryServiceTest {

    @Mock private EventProvider provider1;
    @Mock private EventProvider provider2;

    private static final Instant START = Instant.parse("2026-06-20T18:30:00Z");
    private static final Instant END   = Instant.parse("2026-06-20T20:00:00Z");

    private static DiscoveredEvent event(String source, String externalId, String url, Instant start) {
        return new DiscoveredEvent(source, externalId, "Title " + externalId, null, start, null, url, null);
    }

    /** A null query returns an empty list right away, without calling any provider. */
    @Test
    void search_returnsEmptyForNullQuery() {
        DiscoveryService service = new DiscoveryService(List.of(provider1));

        assertThat(service.search(null)).isEmpty();
        verifyNoInteractions(provider1);
    }

    /** A blank query returns an empty list right away, without calling any provider. */
    @Test
    void search_returnsEmptyForBlankQuery() {
        DiscoveryService service = new DiscoveryService(List.of(provider1));

        assertThat(service.search("   ")).isEmpty();
        verifyNoInteractions(provider1);
    }

    /** Providers that report isConfigured() == false are never queried. */
    @Test
    void search_ignoresUnconfiguredProviders() {
        when(provider1.isConfigured()).thenReturn(false);
        when(provider2.isConfigured()).thenReturn(true);
        when(provider2.search("concert")).thenReturn(List.of(event("P2", "1", "http://a", START)));
        DiscoveryService service = new DiscoveryService(List.of(provider1, provider2));

        List<DiscoveredEvent> results = service.search("concert");

        assertThat(results).hasSize(1);
        verify(provider1, never()).search(any());
    }

    /** The same URL coming from two providers is kept only once (first one wins). */
    @Test
    void search_dedupesByUrlAcrossProviders() {
        when(provider1.isConfigured()).thenReturn(true);
        when(provider2.isConfigured()).thenReturn(true);
        when(provider1.search("x")).thenReturn(List.of(event("P1", "1", "http://same", START)));
        when(provider2.search("x")).thenReturn(List.of(event("P2", "2", "http://same", END)));
        DiscoveryService service = new DiscoveryService(List.of(provider1, provider2));

        List<DiscoveredEvent> results = service.search("x");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).source()).isEqualTo("P1");
    }

    /** When the URL is null, dedup falls back to source + externalId. */
    @Test
    void search_dedupesBySourceAndIdWhenUrlIsNull() {
        when(provider1.isConfigured()).thenReturn(true);
        when(provider1.search("x")).thenReturn(List.of(
                event("P", "1", null, START),
                event("P", "1", null, END),    // same source+id, null url -> duplicate
                event("P", "2", null, END)));  // different id -> kept
        DiscoveryService service = new DiscoveryService(List.of(provider1));

        List<DiscoveredEvent> results = service.search("x");

        assertThat(results).hasSize(2);
    }

    /** The merged list is sorted by start time, regardless of provider order. */
    @Test
    void search_sortsResultsByStartTime() {
        Instant jan = Instant.parse("2026-01-01T10:00:00Z");
        Instant jun = Instant.parse("2026-06-01T10:00:00Z");
        Instant dec = Instant.parse("2026-12-01T10:00:00Z");
        when(provider1.isConfigured()).thenReturn(true);
        when(provider1.search("x")).thenReturn(List.of(
                event("P", "a", "http://a", dec),
                event("P", "b", "http://b", jan),
                event("P", "c", "http://c", jun)));
        DiscoveryService service = new DiscoveryService(List.of(provider1));

        List<DiscoveredEvent> results = service.search("x");

        assertThat(results).extracting(DiscoveredEvent::start).containsExactly(jan, jun, dec);
    }
}
