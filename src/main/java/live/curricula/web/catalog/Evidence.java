package live.curricula.web.catalog;
public record Evidence(String assertionIri, String predicate, String object,
                       String source, String witness, String identifier) {}
