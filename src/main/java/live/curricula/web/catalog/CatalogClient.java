package live.curricula.web.catalog;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
@Service
public class CatalogClient {
 private final RestClient client;
 public CatalogClient() {
   String origin = System.getenv().getOrDefault("CURRICULA_API_URL", "http://localhost:8081");
   client = RestClient.builder().baseUrl(origin).build();
 }
 public SearchResults search(String query) {
   return client.get().uri(builder -> builder.path("/api/v1/search").queryParam("q", query).build())
       .retrieve().body(SearchResults.class);
 }
 public ConceptSummary concept(String slug) {
   return client.get().uri("/api/v1/concepts/{slug}",slug).retrieve().body(ConceptSummary.class);
 }
}
