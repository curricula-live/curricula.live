package live.curricula.web.catalog;

import java.util.List;

public record EducationContext(String conceptIri, boolean available, List<Item> prerequisites,
                               List<Item> curriculumMappings, List<Item> teachingNotes, boolean truncated) {
    public record Item(String value, String language, String graphIri) { }
}
