package live.curricula.web.catalog;

import java.util.List;

public final class Scholarly {
    private Scholarly() { }
    public record Page<T>(String version, String manifestSha256, int offset, Integer nextOffset, List<T> results) { }
    public record Source(String iri, long assertions) { }
    public record Term(String value, String type, String datatype, String language) { }
    public record Citation(String assertionIri, String subjectIri, String subjectSlug, String predicateIri,
                           Term object, String objectSlug, String sourceIri, String graphIri) { }
}
