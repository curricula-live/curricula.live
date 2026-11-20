package live.curricula.web.catalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;

@Service
public class CatalogClient {
    private final RestClient client;

    public CatalogClient(@Value("${curricula.api.base-url:http://localhost:8081}") String origin) {
        client = RestClient.builder().baseUrl(origin).build();
    }

    public SearchResults search(String query) {
        return client.get().uri(builder -> builder.path("/api/v1/search")
                .queryParam("q", "{query}").build(query)).retrieve().body(SearchResults.class);
    }

    public ConnectionSearchResults searchConnections(String query) {
        return client.get().uri(builder -> builder.path("/api/v1/relationships/search")
                .queryParam("q", "{query}").build(query)).retrieve().body(ConnectionSearchResults.class);
    }

    public ConceptSummary concept(String slug) {
        return client.get().uri("/api/v1/concepts/{slug}", slug).retrieve().body(ConceptSummary.class);
    }

    public List<Relationship> relationships(String slug, String layer) {
        var result = client.get().uri(builder -> builder.path("/api/v1/concepts/{slug}/relationships")
                .queryParam("graph", layer).build(slug))
                .retrieve().body(new ParameterizedTypeReference<List<Relationship>>() {});
        return result == null ? List.of() : result;
    }

    public List<Evidence> provenance(String slug) {
        var result = client.get().uri("/api/v1/concepts/{slug}/provenance", slug)
                .retrieve().body(new ParameterizedTypeReference<List<Evidence>>() {});
        return result == null ? List.of() : result;
    }

    public List<Evidence> edgeProvenance(String slug, String predicate, String object) {
        var result = client.get().uri(builder -> builder.path("/api/v1/concepts/{slug}/provenance")
                .queryParam("direction", "outgoing")
                .queryParam("predicate", "{predicate}")
                .queryParam("adjacent", "{object}")
                .build(Map.of("slug", slug, "predicate", predicate, "object", object)))
                .retrieve().body(new ParameterizedTypeReference<List<Evidence>>() {});
        return result == null ? List.of() : result;
    }

    public Neighborhood neighborhood(String slug, int depth, String graph, String kind,
                                     String domain, String predicate) {
        return client.get().uri(builder -> builder.path("/api/v1/concepts/{slug}/neighborhood")
                .queryParam("depth", depth)
                .queryParam("limit", 200)
                .queryParam("graph", graph)
                .queryParam("kind", kind)
                .queryParamIfPresent("domain", domain.isEmpty() ? Optional.empty() : Optional.of("{domain}"))
                .queryParamIfPresent("predicate", predicate.isEmpty() ? Optional.empty() : Optional.of("{predicate}"))
                .build(Map.of("slug", slug, "domain", domain, "predicate", predicate)))
                .retrieve().body(Neighborhood.class);
    }

    public PublicationStatus publication() {
        return client.get().uri("/api/v1/knowledge/publication")
                .retrieve().body(PublicationStatus.class);
    }
}
