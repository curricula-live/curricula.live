package live.curricula.web.catalog;
import java.util.List;
public record Neighborhood(String root, List<String> nodes, List<Relationship> edges,
                           int depth, boolean truncated, List<NodeDetails> nodeDetails) {
    public record NodeDetails(String iri, String slug, String label, List<String> domains) {}
}
