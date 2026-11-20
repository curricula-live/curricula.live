package live.curricula.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
    private static final String UNIT = BASE + "concept/unit-propagation";
    private static final String USES = BASE + "vocab/uses";
    private static final String LOGIC = BASE + "scheme/logic";

    @BeforeEach void data() {
        when(api.concept("dpll-algorithm")).thenReturn(
                new ConceptSummary(DPLL, "dpll-algorithm", "DPLL algorithm"));
        var relations = List.of(
                new Relationship(UNIT, USES, DPLL,
                        "asserted", "incoming", "scientific", "Unit propagation"),
                new Relationship(DPLL, USES, UNIT,
                        "asserted", "outgoing", "scientific", "Unit propagation"),
                new Relationship(DPLL, "http://www.w3.org/2004/02/skos/core#broader",
                        BASE + "concept/boolean-satisfiability", "asserted", "outgoing",
                        "taxonomy", "Boolean satisfiability"));
        when(api.relationships("dpll-algorithm", "all")).thenReturn(relations);
        var evidence = List.of(new Evidence(BASE + "assertion/test-witness", USES,
                UNIT, "https://example.org/source", BASE + "witness/test", "example-digest", DPLL));
        when(api.provenance("dpll-algorithm")).thenReturn(evidence);
        when(api.edgeProvenance("dpll-algorithm", USES, UNIT)).thenReturn(evidence);
        var neighborhood = new Neighborhood(DPLL, List.of(DPLL, UNIT), relations, 2, false, List.of(
                new Neighborhood.NodeDetails(DPLL, "dpll-algorithm", "DPLL algorithm", List.of(LOGIC)),
                new Neighborhood.NodeDetails(UNIT, "unit-propagation", "Unit propagation", List.of(LOGIC))));
        when(api.neighborhood("dpll-algorithm", 2, "all", "all", "", "")).thenReturn(neighborhood);
        when(api.neighborhood("dpll-algorithm", 2, "asserted", "scientific", LOGIC, USES)).thenReturn(neighborhood);
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
        assertTrue(html.body().contains("graph-domain"));
        assertTrue(html.body().contains("Zoom in"));
        var json = request("/concepts/dpll-algorithm/graph/data?depth=2");
        assertEquals(200, json.statusCode());
        assertTrue(json.body().contains("unit-propagation"));
        assertTrue(json.body().contains("asserted"));
        assertTrue(json.body().contains("nodeDetails"));
    }

    @Test void forwardsNeighborhoodFiltersAndEvidenceLookup() throws Exception {
        var graph = request("/concepts/dpll-algorithm/graph/data?depth=2&graph=asserted&kind=scientific"
                + "&domain=" + encode(LOGIC) + "&predicate=" + encode(USES));
        assertEquals(200, graph.statusCode());
        verify(api).neighborhood("dpll-algorithm", 2, "asserted", "scientific", LOGIC, USES);

        var provenance = request("/concepts/dpll-algorithm/graph/evidence?predicate=" + encode(USES)
                + "&object=" + encode(UNIT));
        assertEquals(200, provenance.statusCode());
        assertTrue(provenance.body().contains("test-witness"));
        verify(api).edgeProvenance("dpll-algorithm", USES, UNIT);
    }

    @Test void rejectsInvalidGraphAndEvidenceInputs() throws Exception {
        assertEquals(400, request("/concepts/dpll-algorithm/graph/data?depth=6").statusCode());
        assertEquals(400, request("/concepts/dpll-algorithm/graph/data?kind=unknown").statusCode());
        assertEquals(400, request("/concepts/dpll-algorithm/graph/data?predicate=relative").statusCode());
        assertEquals(400, request("/concepts/dpll-algorithm/graph/evidence?predicate=uses&object=unit").statusCode());
    }

    @Test void graphEntryAndConnectionsViewUseCanonicalSlug() throws Exception {
        var entry = request("/graph?concept=dpll-algorithm");
        assertEquals(302, entry.statusCode());
        assertEquals("/concepts/dpll-algorithm/graph", entry.headers().firstValue("location").orElseThrow());
        assertEquals(400, request("/graph?concept=%2fother").statusCode());
        var connections = request("/concepts/dpll-algorithm?view=connections");
        assertEquals(200, connections.statusCode());
        assertTrue(connections.body().contains("section section-current"));
        assertTrue(connections.body().contains("aria-current=\"page\">Connections"));
        assertTrue(connections.body().contains("incoming"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
