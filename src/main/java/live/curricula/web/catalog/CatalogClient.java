package live.curricula.web.catalog;

import java.util.List;
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
                .queryParam("q", query).build()).retrieve().body(SearchResults.class);
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

    public Neighborhood neighborhood(String slug, int depth) {
        return client.get().uri(builder -> builder.path("/api/v1/concepts/{slug}/neighborhood")
                .queryParam("depth", depth).build(slug)).retrieve().body(Neighborhood.class);
    }

    public PublicationStatus publication() {
        return client.get().uri("/api/v1/knowledge/publication")
                .retrieve().body(PublicationStatus.class);
    }
}
