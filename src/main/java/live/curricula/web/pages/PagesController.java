package live.curricula.web.pages;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.List;
import live.curricula.web.catalog.CatalogClient;
import live.curricula.web.catalog.Evidence;
import live.curricula.web.catalog.GraphArchive;
import live.curricula.web.catalog.Neighborhood;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
public class PagesController {
    private static final List<String> GRAPHS = List.of("all", "asserted", "inferred", "alignments");
    private static final List<String> KINDS = List.of("all", "scientific", "taxonomy", "association", "mapping", "other");
    private final CatalogClient client;

    private final GraphArchive archive;

    public PagesController(CatalogClient client, GraphArchive archive) {
        this.client = client;
        this.archive = archive;
    }

    @GetMapping("/")
    public String home(Model model) {
        try { model.addAttribute("publication", client.publication()); }
        catch (RestClientException exception) { model.addAttribute("publication", null); }
        return "home";
    }

    @GetMapping("/about")
    public String about() { return "about"; }

    @GetMapping("/search")
    public String search(@RequestParam(defaultValue = "") String q,
                         @RequestParam(defaultValue = "all") String category, Model model) {
        populateSearch(q.strip(), searchCategory(category), model);
        return "search";
    }

    @GetMapping("/search/results")
    public String searchResults(@RequestParam(defaultValue = "") String q,
                                @RequestParam(defaultValue = "all") String category,
                                Model model, HttpServletResponse response) {
        String query = q.strip();
        String resultType = searchCategory(category);
        populateSearch(query, resultType, model);
        String bookmark = UriComponentsBuilder.fromPath("/search")
                .queryParam("q", "{query}").queryParam("category", resultType)
                .encode().buildAndExpand(query).toUriString();
        response.setHeader("HX-Push-Url", bookmark);
        return "fragments/results :: results";
    }

    private String searchCategory(String category) {
        return switch (category) {
            case "concepts", "connections" -> category;
            default -> "all";
        };
    }

    private void populateSearch(String query, String category, Model model) {
        populateSearch(query, category, model, false);
    }

    private void populateSearch(String query, String category, Model model, boolean acrossProducers) {
        model.addAttribute("query", query);
        model.addAttribute("category", category);
        model.addAttribute("searched", !query.isBlank());
        model.addAttribute("results", List.of());
        model.addAttribute("connections", List.of());
        model.addAttribute("conceptsUnavailable", false);
        model.addAttribute("connectionsUnavailable", false);
        model.addAttribute("connectionsInvalid", false);
        model.addAttribute("archiveAvailable", archive.available());
        model.addAttribute("archiveResults", List.of());
        boolean invalidQuery = query.codePointCount(0, query.length()) > 160;
        model.addAttribute("invalidQuery", invalidQuery);
        if (query.isBlank() || invalidQuery) return;
        if (!category.equals("connections")) model.addAttribute("archiveResults", archive.search(query));

        if (!category.equals("connections")) {
            try {
                var result = acrossProducers ? client.searchAcross(query) : client.search(query);
                if (result != null && result.concepts() != null) model.addAttribute("results", result.concepts());
            } catch (RestClientException exception) {
                model.addAttribute("conceptsUnavailable", true);
            }
        }
        if (!category.equals("concepts")) {
            try {
                var result = client.searchConnections(query);
                if (result != null && result.connections() != null) model.addAttribute("connections", result.connections());
            } catch (HttpClientErrorException.BadRequest exception) {
                model.addAttribute("connectionsInvalid", true);
            } catch (RestClientException exception) {
                model.addAttribute("connectionsUnavailable", true);
            }
        }
    }

    @GetMapping("/concepts/{slug}")
    public String concept(@PathVariable String slug, @RequestParam(defaultValue = "overview") String view,
                          Model model, HttpServletResponse response) {
        model.addAttribute("slug", slug);
        String activeView = switch (view) {
            case "connections", "curriculum", "pedagogical" -> view;
            default -> "overview";
        };
        model.addAttribute("activeView", activeView);
        model.addAttribute("connectionsView", "connections".equals(activeView));
        model.addAttribute("definitions", List.of());
        model.addAttribute("documentationUnavailable", false);
        model.addAttribute("concept", null);
        model.addAttribute("relations", List.of());
        model.addAttribute("evidence", List.of());
        model.addAttribute("unavailable", false);
        try {
            var resolved = client.concept(slug);
            model.addAttribute("concept", resolved);
            if (resolved != null && ("curriculum".equals(activeView) || "pedagogical".equals(activeView))) {
                addEducation(resolved.iri(), model);
            }
            if ("overview".equals(activeView) || "connections".equals(activeView)) {
                model.addAttribute("relations", client.relationships(slug, "all"));
                model.addAttribute("evidence", client.provenance(slug));
            }
            if ("overview".equals(activeView) || "pedagogical".equals(activeView)) {
                try {
                    var documentation = client.documentation(slug);
                    if (documentation != null && documentation.definitions() != null) {
                        model.addAttribute("definitions", documentation.definitions());
                    }
                } catch (RestClientException exception) {
                    model.addAttribute("documentationUnavailable", true);
                }
            }
        } catch (HttpClientErrorException.NotFound exception) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
        } catch (RestClientException exception) {
            model.addAttribute("unavailable", true);
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        return "concept";
    }

    @GetMapping(value = "/graph", params = "!concept")
    public String graphChooser(@RequestParam(defaultValue = "") String q, Model model) {
        populateSearch(q.strip(), "concepts", model, true);
        return "graph-chooser";
    }

    @GetMapping(value = "/graph", params = "concept")
    public ResponseEntity<Void> graphEntry(@RequestParam(defaultValue = "") String concept) {
        if (GraphArchive.isLegacyIri(concept)) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(GraphArchive.legacyUrl(concept))).build();
        }
        if (isCanonicalCandidate(concept)) {
            URI path = UriComponentsBuilder.fromPath("/graph/by-iri").queryParam("iri", "{iri}")
                    .encode().buildAndExpand(concept).toUri();
            return ResponseEntity.status(HttpStatus.FOUND).location(path).build();
        }
        if (!concept.isBlank() && !concept.matches("[a-z0-9][-a-z0-9]{0,127}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a canonical concept slug");
        }
        String path = concept.isBlank() ? "/graph" : "/concepts/" + concept + "/graph";
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(path)).build();
    }

    @GetMapping("/concepts/{slug}/graph")
    public String graph(@PathVariable String slug, Model model, HttpServletResponse response) {
        model.addAttribute("slug", slug);
        model.addAttribute("concept", null);
        model.addAttribute("unavailable", false);
        try { model.addAttribute("concept", client.concept(slug)); }
        catch (HttpClientErrorException.NotFound exception) { response.setStatus(404); }
        catch (RestClientException exception) {
            model.addAttribute("unavailable", true);
            response.setStatus(503);
        }
        return "graph";
    }

    @GetMapping("/graph/by-iri")
    public String graphByIri(@RequestParam String iri, Model model, HttpServletResponse response) {
        addCanonical(iri, model, response);
        return "graph";
    }

    @GetMapping("/concepts/by-iri")
    public String canonicalConcept(@RequestParam String iri, Model model, HttpServletResponse response) {
        addCanonical(iri, model, response);
        if (model.getAttribute("concept") != null) addEducation(iri, model);
        return "canonical-concept";
    }

    private void addCanonical(String iri, Model model, HttpServletResponse response) {
        if (!isCanonicalCandidate(iri)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected a canonical HTTP(S) IRI");
        model.addAttribute("iri", iri);
        model.addAttribute("unavailable", false);
        try { model.addAttribute("concept", client.conceptByIri(iri)); }
        catch (HttpClientErrorException.NotFound failure) { response.setStatus(404); }
        catch (RestClientException failure) {
            model.addAttribute("unavailable", true);
            response.setStatus(503);
        }
    }

    private void addEducation(String iri, Model model) {
        try { model.addAttribute("education", client.education(iri)); }
        catch (RestClientException failure) { model.addAttribute("educationUnavailable", true); }
    }

    @ResponseBody
    @GetMapping("/graph/by-iri/data")
    public Neighborhood graphDataByIri(@RequestParam String iri, @RequestParam(defaultValue = "2") int depth,
                                       @RequestParam(defaultValue = "all") String graph,
                                       @RequestParam(defaultValue = "all") String kind,
                                       @RequestParam(defaultValue = "") String domain,
                                       @RequestParam(defaultValue = "") String predicate) {
        if (!isCanonicalCandidate(iri)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid concept IRI");
        validateGraphRequest(depth, graph, kind, domain, predicate);
        return catalogResult(() -> client.neighborhoodByIri(iri, depth, graph, kind, domain, predicate));
    }

    @ResponseBody
    @GetMapping("/graph/by-iri/evidence")
    public List<Evidence> evidenceByIri(@RequestParam String iri, @RequestParam String predicate,
                                        @RequestParam String object) {
        if (!isCanonicalCandidate(iri) || !isAbsoluteIri(predicate) || !isAbsoluteIri(object)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid evidence IRIs");
        }
        return catalogResult(() -> client.edgeProvenanceByIri(iri, predicate, object));
    }

    private static <T> T catalogResult(java.util.function.Supplier<T> request) {
        try { return request.get(); }
        catch (HttpClientErrorException.NotFound failure) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Concept not found");
        } catch (HttpClientErrorException.BadRequest failure) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid graph request");
        } catch (RestClientException failure) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Knowledge API unavailable");
        }
    }

    private static boolean isCanonicalCandidate(String value) {
        return isAbsoluteIri(value) && (value.startsWith("https://") || value.startsWith("http://"))
                && value.contains("/concept/") && !GraphArchive.isLegacyIri(value);
    }

    @ResponseBody
    @GetMapping("/concepts/{slug}/graph/data")
    public Neighborhood graphData(@PathVariable String slug,
                                  @RequestParam(defaultValue = "2") int depth,
                                  @RequestParam(defaultValue = "all") String graph,
                                  @RequestParam(defaultValue = "all") String kind,
                                  @RequestParam(defaultValue = "") String domain,
                                  @RequestParam(defaultValue = "") String predicate) {
        validateGraphRequest(depth, graph, kind, domain, predicate);
        return catalogResult(() -> client.neighborhood(slug, depth, graph, kind, domain, predicate));
    }

    private static void validateGraphRequest(int depth, String graph, String kind, String domain, String predicate) {
        if (depth < 1 || depth > 5) throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Depth must be 1..5");
        if (!GRAPHS.contains(graph) || !KINDS.contains(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown graph or relation category");
        }
        if ((!domain.isEmpty() && !isAbsoluteIri(domain)) ||
                (!predicate.isEmpty() && !isAbsoluteIri(predicate))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Domain and predicate must be absolute IRIs");
        }
    }

    @ResponseBody
    @GetMapping("/concepts/{slug}/graph/evidence")
    public List<Evidence> edgeEvidence(@PathVariable String slug, @RequestParam String predicate,
                                      @RequestParam String object) {
        if (!isAbsoluteIri(predicate) || !isAbsoluteIri(object)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Predicate and object must be absolute IRIs");
        }
        return catalogResult(() -> client.edgeProvenance(slug, predicate, object));
    }

    private static boolean isAbsoluteIri(String value) {
        if (value.length() > 2048) return false;
        try { return URI.create(value).isAbsolute(); }
        catch (IllegalArgumentException exception) { return false; }
    }
}
