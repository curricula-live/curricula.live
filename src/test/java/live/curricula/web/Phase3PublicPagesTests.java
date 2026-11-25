package live.curricula.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import live.curricula.web.catalog.CatalogClient;
import live.curricula.web.catalog.ConceptDocumentation;
import live.curricula.web.catalog.ConceptSummary;
import live.curricula.web.catalog.EducationContext;
import live.curricula.web.catalog.PublicationStatus;
import live.curricula.web.catalog.Scholarly;
import live.curricula.web.catalog.SearchResults;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Phase3PublicPagesTests {
    @LocalServerPort int port;
    @MockitoBean CatalogClient api;
    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String route) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + route)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test void utilitiesAndGraphSupportBookmarks() throws Exception {
        assertTrue(get("/privacy").body().contains("Your information"));
        assertTrue(get("/contact").body().contains("Privacy &amp; security"));
        assertTrue(get("/graph").body().contains("Find a starting concept"));
        var concept = new ConceptSummary("https://curricula.live/id/formal-sciences/concept/dpll-algorithm",
                "dpll-algorithm", "DPLL");
        when(api.searchAcross("DPLL")).thenReturn(new SearchResults("DPLL", List.of(concept)));
        assertTrue(get("/graph?q=DPLL").body().contains("/graph/by-iri?iri="));

        String archived = "https://curricula.live/knowledge/computing/overlay/002bd701-c4a9-5e04-a861-40dfab515cf7";
        var result = get("/graph?concept=" + URLEncoder.encode(archived, StandardCharsets.UTF_8));
        assertEquals(302, result.statusCode());
        assertTrue(result.headers().firstValue("Location").orElseThrow().startsWith("https://graph.curricula.live/"));
        assertEquals(400, get("/graph?concept=https%3A%2F%2Fexample.org").statusCode());
    }

    @Test void conceptTabsUseRecordedDefinitionsAndRecognizeMissingEducationData() throws Exception {
        String iri = "https://curricula.live/id/formal-sciences/concept/dpll-algorithm";
        when(api.concept("dpll-algorithm")).thenReturn(new ConceptSummary(iri, "dpll-algorithm", "DPLL"));
        when(api.documentation("dpll-algorithm")).thenReturn(new ConceptDocumentation(iri,
                List.of(new ConceptDocumentation.Definition("Recorded definition", "en"))));
        assertTrue(get("/concepts/dpll-algorithm").body().contains("Recorded definition"));
        assertTrue(get("/concepts/dpll-algorithm?view=curriculum").body().contains("require published, reviewed data"));
        assertTrue(get("/concepts/dpll-algorithm?view=pedagogical").body().contains("yet to be published"));
    }

    @Test void planningIsDisabledAndCanonicalPagesRenderOnlyRecordedEducation() throws Exception {
        assertEquals(403, get("/teach").statusCode());
        assertFalse(get("/").body().contains("Teacher planning"));
        String iri = "https://example.test/learning/concept/algebra";
        String graph = "https://example.test/learning/graph/asserted";
        var concept = new ConceptSummary(iri, "algebra", "Algebra test concept");
        when(api.conceptByIri(iri)).thenReturn(concept);
        when(api.education(iri)).thenReturn(new EducationContext(iri, true, List.of(),
                List.of(new EducationContext.Item("https://example.test/curriculum/test", "", graph)),
                List.of(new EducationContext.Item("Published test note", "en", graph)), false));
        var response = get("/concepts/by-iri?iri=" + URLEncoder.encode(iri, StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Published test note"));
        assertTrue(response.body().contains("https://example.test/curriculum/test"));
        assertTrue(response.body().contains("record no prerequisites"));
    }

    @Test void sourcesCanBeSelectedByProducerWithoutLosingThePublicationPin() throws Exception {
        String pin = "a".repeat(64);
        var current = new PublicationStatus(true, "fixture-1", pin, null, java.util.Map.of(), 1);
        when(api.publications()).thenReturn(java.util.Map.of("formal-sciences", current, "fixture", current));
        when(api.sources("fixture", 0, null)).thenReturn(new Scholarly.Page<>("fixture-1", pin, 0, 25, List.of(
                new Scholarly.Source("https://example.test/fixture-source", 1))));
        var response = get("/evidence?producer=fixture");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Ontology publication"));
        assertTrue(response.body().contains("fixture vfixture-1"));
        assertTrue(response.body().contains("publication=" + pin + "&amp;producer=fixture"));
    }

    @Test void evidenceLinksPinThePublicationAndHandleRollovers() throws Exception {
        String pin = "b".repeat(64);
        var source = new Scholarly.Source("https://doi.org/10.3233/FAIA336", 25);
        when(api.sources(0, null)).thenReturn(new Scholarly.Page<>("0.8.0", pin, 0, 25, List.of(source)));
        var result = get("/evidence");
        assertEquals(200, result.statusCode());
        assertTrue(result.body().contains("25 recorded assertions"));
        assertTrue(result.body().contains("/evidence?offset=25&amp;publication=" + pin));
        when(api.sources(25, pin)).thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT,
                "Changed", null, null, null));
        assertEquals(409, get("/evidence?offset=25&publication=" + pin).statusCode());

        var term = new Scholarly.Term("A recorded term", "literal", "http://www.w3.org/2001/XMLSchema#string", "");
        var citation = new Scholarly.Citation("https://example.org/assertion", "https://example.org/subject",
                null, "https://example.org/predicate", term, null, source.iri(), "https://example.org/graph");
        when(api.citations(source.iri(), null, 0, pin))
                .thenReturn(new Scholarly.Page<>("0.8.0", pin, 0, null, List.of(citation)));
        String selected = "/evidence?source=" + URLEncoder.encode(source.iri(), StandardCharsets.UTF_8) + "&publication=" + pin;
        assertTrue(get(selected).body().contains("href=\"" + source.iri() + "\" rel=\"noreferrer\">Open cited source"));
    }
}
