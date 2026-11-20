package live.curricula.web.pages;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.stream.Collectors;
import live.curricula.web.catalog.CatalogClient;
import live.curricula.web.catalog.Relationship;
import live.curricula.web.catalog.Neighborhood;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class PagesController {
    private final CatalogClient client;

    public PagesController(CatalogClient client) { this.client = client; }

    @GetMapping("/")
    public String home(Model model) {
        try { model.addAttribute("publication", client.publication()); }
        catch (RestClientException exception) { model.addAttribute("publication", null); }
        return "home";
    }

    @GetMapping("/about")
    public String about() { return "about"; }

    @GetMapping("/search")
    public String search(@RequestParam(defaultValue = "") String q, Model model) {
        populateSearch(q, model);
        return "search";
    }

    @GetMapping("/search/results")
    public String searchResults(@RequestParam(defaultValue = "") String q, Model model) {
        populateSearch(q, model);
        return "fragments/results :: results";
    }

    private void populateSearch(String q, Model model) {
        model.addAttribute("query", q);
        model.addAttribute("searched", !q.isBlank());
        model.addAttribute("unavailable", false);
        model.addAttribute("results", List.of());
        if (q.isBlank()) return;
        try {
            var response = client.search(q.trim());
            model.addAttribute("results", response == null || response.concepts() == null
                    ? List.of() : response.concepts());
        } catch (RestClientException exception) {
            model.addAttribute("unavailable", true);
        }
    }

    @GetMapping("/concepts/{slug}")
    public String concept(@PathVariable String slug, Model model, HttpServletResponse response) {
        model.addAttribute("slug", slug);
        model.addAttribute("concept", null);
        model.addAttribute("relations", List.of());
        model.addAttribute("evidence", List.of());
        model.addAttribute("unavailable", false);
        try {
            model.addAttribute("concept", client.concept(slug));
            model.addAttribute("relations", client.relationships(slug, "all"));
            model.addAttribute("evidence", client.provenance(slug));
        } catch (HttpClientErrorException.NotFound exception) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
        } catch (RestClientException exception) {
            model.addAttribute("unavailable", true);
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        return "concept";
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

    @ResponseBody
    @GetMapping("/concepts/{slug}/graph/data")
    public Neighborhood graphData(@PathVariable String slug,
                                  @RequestParam(defaultValue = "2") int depth) {
        if (depth < 1 || depth > 5) throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Depth must be 1..5");
        try { return client.neighborhood(slug, depth); }
        catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Concept not found");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Knowledge API unavailable");
        }
    }
}
