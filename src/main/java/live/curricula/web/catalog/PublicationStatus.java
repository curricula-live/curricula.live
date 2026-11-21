package live.curricula.web.catalog;
import java.util.Map;
public record PublicationStatus(boolean available, String version, String manifestSha256,
                                String sourceCommit, Map<String, Long> namedGraphs, int concepts) {}
