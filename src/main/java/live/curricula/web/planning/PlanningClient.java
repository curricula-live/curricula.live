package live.curricula.web.planning;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(name = "CURRICULA_PLANNING_ENABLED", havingValue = "true")
public final class PlanningClient {
    private final RestClient client;

    public PlanningClient(@Value("${curricula.api.base-url}") String origin) {
        var request = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build());
        request.setReadTimeout(Duration.ofSeconds(8));
        client = RestClient.builder().baseUrl(origin).requestFactory(request).build();
    }

    public record Plan(UUID id, String title, String conceptIri, long revision) { }
    record Change(String title, String conceptIri, long revision) { }

    public List<Plan> plans(String token, int offset) {
        return client.get().uri("/api/v1/planning/plans?offset={offset}", offset)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve().body(new ParameterizedTypeReference<List<Plan>>() {});
    }

    public Plan plan(String token, UUID id) {
        return client.get().uri("/api/v1/planning/plans/{id}", id)
                .headers(headers -> headers.setBearerAuth(token)).retrieve().body(Plan.class);
    }

    public void create(String token, String title, String iri) {
        client.post().uri("/api/v1/planning/plans").headers(headers -> headers.setBearerAuth(token))
                .body(new Change(title, iri, 0)).retrieve().toBodilessEntity();
    }

    public void update(String token, UUID id, String title, String iri, long revision) {
        client.put().uri("/api/v1/planning/plans/{id}", id).headers(headers -> headers.setBearerAuth(token))
                .body(new Change(title, iri, revision)).retrieve().toBodilessEntity();
    }
}
