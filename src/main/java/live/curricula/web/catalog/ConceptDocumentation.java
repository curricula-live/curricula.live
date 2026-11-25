package live.curricula.web.catalog;

import java.util.List;

public record ConceptDocumentation(String iri, List<Definition> definitions) {
    public record Definition(String text, String language) { }
}
