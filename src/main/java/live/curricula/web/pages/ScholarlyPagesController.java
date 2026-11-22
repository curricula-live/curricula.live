package live.curricula.web.pages;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import live.curricula.web.catalog.CatalogClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

@Controller
public final class ScholarlyPagesController {
    private final CatalogClient client;

    public ScholarlyPagesController(CatalogClient client) {
        this.client = client;
    }

    @GetMapping("/evidence")
    public String evidence(@RequestParam(required = false) String source,
                           @RequestParam(defaultValue = "0") int offset,
                           @RequestParam(required = false) String publication,
                           @RequestParam(defaultValue = "formal-sciences") String producer,
                           Model model, HttpServletResponse response) {
        return render(source, null, offset, publication, producer, model, response);
    }

    @GetMapping("/concepts/{slug}/evidence")
    public String conceptEvidence(@PathVariable String slug, @RequestParam(defaultValue = "0") int offset,
                                  @RequestParam(required = false) String publication,
                                  Model model, HttpServletResponse response) {
        return render(null, slug, offset, publication, "formal-sciences", model, response);
    }

    private String render(String source, String slug, int offset, String pin, String producer,
                          Model model, HttpServletResponse response) {
        boolean listing = source == null && slug == null;
        model.addAttribute("source", source);
        model.addAttribute("slug", slug);
        model.addAttribute("listing", listing);
        model.addAttribute("offset", offset);
        boolean formal = "formal-sciences".equals(producer);
        model.addAttribute("producer", formal ? null : producer);
        if (listing) {
            try {
                var available = client.publications();
                if (available != null) model.addAttribute("availableProducers", available.entrySet().stream()
                        .filter(entry -> entry.getValue().available()).map(java.util.Map.Entry::getKey).sorted().toList());
            } catch (RestClientException ignored) {
                // A failure to load the selector does not invalidate a pinned citation page.
            }
        }
        try {
            var page = listing
                    ? (formal ? client.sources(offset, pin) : client.sources(producer, offset, pin))
                    : (formal ? client.citations(source, slug, offset, pin)
                              : client.citationsByProducer(producer, source, offset, pin));
            model.addAttribute("page", page);
            if (source != null && page != null && !page.results().isEmpty()) {
                URI recorded = URI.create(source);
                if (recorded.getHost() != null && recorded.getUserInfo() == null
                        && ("https".equalsIgnoreCase(recorded.getScheme()) || "http".equalsIgnoreCase(recorded.getScheme()))) {
                    model.addAttribute("sourceLink", recorded.toASCIIString());
                }
            }
        } catch (HttpClientErrorException.Conflict exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            model.addAttribute("message", "The RDF publication changed. Start again at the first page.");
        } catch (HttpClientErrorException.NotFound exception) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            model.addAttribute("message", "Concept not found.");
        } catch (HttpClientErrorException.BadRequest exception) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("message", "The evidence request is invalid.");
        } catch (RestClientException exception) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            model.addAttribute("message", "Published evidence is temporarily unavailable.");
        }
        return "evidence";
    }
}
