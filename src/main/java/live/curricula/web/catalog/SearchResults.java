package live.curricula.web.catalog;
import java.util.List;
public record SearchResults(String query, List<ConceptSummary> concepts) {}
