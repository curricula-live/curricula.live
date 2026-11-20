package live.curricula.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import live.curricula.web.catalog.CatalogClient;
import live.curricula.web.catalog.ConceptSummary;
import live.curricula.web.catalog.Relationship;
import live.curricula.web.catalog.Evidence;
import live.curricula.web.catalog.Neighborhood;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Renders real RDF-shaped response fixtures through the complete MVC/Thymeleaf path. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConceptRenderingTests {
    @LocalServerPort int port;
    @MockitoBean CatalogClient api;
    private final HttpClient http = HttpClient.newHttpClient();
    private static final String BASE = "https://curricula.live/id/formal-sciences/";
    private static final String DPLL = BASE + "concept/dpll-algorithm";

    @BeforeEach void data() {
        when(api.concept("dpll-algorithm")).thenReturn(
                new ConceptSummary(DPLL, "dpll-algorithm", "DPLL algorithm"));
        var relations = List.of(
                new Relationship(DPLL, BASE + "vocab/uses", BASE + "concept/unit-propagation",
                        "asserted", "outgoing", "scientific", "Unit propagation"),
                new Relationship(DPLL, "http://www.w3.org/2004/02/skos/core#broader",
                        BASE + "concept/boolean-satisfiability", "asserted", "outgoing",
                        "taxonomy", "Boolean satisfiability"));
        when(api.relationships("dpll-algorithm", "all")).thenReturn(relations);
        when(api.provenance("dpll-algorithm")).thenReturn(List.of(
                new Evidence(BASE + "assertion/test-witness", BASE + "vocab/uses",
                        BASE + "concept/unit-propagation", "https://example.org/source",
                        BASE + "witness/test", "example-digest")));
        when(api.neighborhood("dpll-algorithm", 2)).thenReturn(
                new Neighborhood(DPLL, List.of(DPLL, BASE + "concept/unit-propagation"),
                        relations, 2, false));
    }

    private HttpResponse<String> request(String route) throws Exception {
        return http.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + route)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test void rendersRealScientificAndProvenanceFields() throws Exception {
        var response = request("/concepts/dpll-algorithm");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("DPLL algorithm"));
        assertTrue(response.body().contains("Unit propagation"));
        assertTrue(response.body().contains("Boolean satisfiability"));
        assertTrue(response.body().contains("Provenance and supporting evidence"));
        assertTrue(response.body().contains("test-witness"));
        assertTrue(response.body().contains("Explore relationship graph"));
    }

    @Test void rendersGraphPageAndData() throws Exception {
        var html = request("/concepts/dpll-algorithm/graph");
        assertEquals(200, html.statusCode());
        assertTrue(html.body().contains("concept-graph.js"));
        var json = request("/concepts/dpll-algorithm/graph/data?depth=2");
        assertEquals(200, json.statusCode());
        assertTrue(json.body().contains("unit-propagation"));
        assertTrue(json.body().contains("asserted"));
    }

    @Test void rejectsExcessiveTraversalDepth() throws Exception {
        assertEquals(400, request("/concepts/dpll-algorithm/graph/data?depth=6").statusCode());
    }
}
