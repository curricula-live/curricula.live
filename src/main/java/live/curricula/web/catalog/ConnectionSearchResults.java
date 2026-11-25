package live.curricula.web.catalog;

import java.util.List;

public record ConnectionSearchResults(String query, List<Connection> connections) {
    public record Connection(String subject, String predicate, String object, String graph,
                             String subjectSlug, String subjectLabel, String objectSlug, String objectLabel) {
        public String predicateName() {
            int separator = Math.max(predicate.lastIndexOf('/'), predicate.lastIndexOf('#'));
            return predicate.substring(separator + 1);
        }
    }
}
