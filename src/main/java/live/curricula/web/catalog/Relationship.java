package live.curricula.web.catalog;
public record Relationship(String subject, String predicate, String object, String graph, String direction,
                           String kind, String objectLabel, String graphIri) {}
