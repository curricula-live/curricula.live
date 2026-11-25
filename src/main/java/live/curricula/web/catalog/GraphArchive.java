package live.curricula.web.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

/** The published v1 archive has its own identifiers and never enters the canonical catalog. */
@Service
public final class GraphArchive {
    private static final String DIGEST = "c36057ff0fefe52150eb09f44213baec3443e36f3a38f185e13db93300086d70";
    private static final Pattern LEGACY_IRI = Pattern.compile(
            "https://curricula\\.live/knowledge/computing/(?:concept|overlay)/[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private final List<Entry> entries;

    public GraphArchive(@Value("${CURRICULA_ARCHIVE_PATH:}") String path) throws IOException {
        entries = path.isBlank() ? List.of() : load(Path.of(path), DIGEST);
    }

    record Entry(Hit hit, List<String> terms) { }
    private record Match(Hit hit, int rank) { }
    public record Hit(String id, String label, String labelRo, String module) {
        public String url() { return legacyUrl(id); }
    }

    public boolean available() { return !entries.isEmpty(); }

    public List<Hit> search(String query) {
        if (query.isBlank() || query.codePointCount(0, query.length()) > 160) return List.of();
        String term = fold(query);
        if (term.isEmpty()) return List.of();
        return entries.stream().map(entry -> new Match(entry.hit(), rank(entry.terms(), term)))
                .filter(match -> match.rank() > 0)
                .sorted(Comparator.comparingInt(Match::rank).reversed()
                        .thenComparing(match -> match.hit().label(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(match -> match.hit().id()))
                .limit(20).map(Match::hit).toList();
    }

    public static boolean isLegacyIri(String iri) { return LEGACY_IRI.matcher(iri).matches(); }

    public static String legacyUrl(String iri) {
        if (!isLegacyIri(iri)) throw new IllegalArgumentException("Invalid archived graph identifier");
        return UriComponentsBuilder.fromUriString("https://graph.curricula.live/")
                .queryParam("concept", "{id}").encode().buildAndExpand(iri).toUriString();
    }

    private static List<Entry> load(Path archive, String digest) throws IOException {
        if (!Files.isRegularFile(archive) || Files.size(archive) > 24 * 1024 * 1024) {
            throw new IOException("Expected a public graph archive smaller than 24 MiB");
        }
        byte[] bytes = Files.readAllBytes(archive);
        try {
            if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(digest)) {
                throw new IOException("Graph archive SHA-256 mismatch");
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        var payload = JsonMapper.builder().build().readTree(bytes);
        var nodes = payload.path("nodes");
        if (!nodes.isArray() || nodes.isEmpty()) throw new IOException("Graph archive has no nodes");
        var entries = new ArrayList<Entry>();
        for (var node : nodes) {
            String id = node.path("id").asText();
            String label = node.path("label").asText();
            if (!isLegacyIri(id) || label.isBlank()) throw new IOException("Invalid archived graph node");
            String romanian = node.path("labelRo").isString() ? node.path("labelRo").asText() : "";
            var terms = new ArrayList<>(List.of(fold(label), fold(romanian)));
            for (var alias : node.path("aliases")) if (alias.isString()) terms.add(fold(alias.asText()));
            entries.add(new Entry(new Hit(id, label, romanian, node.path("module").asText()), List.copyOf(terms)));
        }
        return List.copyOf(entries);
    }

    private static int rank(List<String> terms, String query) {
        int best = 0;
        for (String term : terms) {
            if (term.equals(query)) return 3;
            if (term.startsWith(query)) best = 2;
            else if (best == 0 && term.contains(query)) best = 1;
        }
        return best;
    }

    private static String fold(String value) {
        return MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("")
                .toLowerCase(Locale.ROOT).strip();
    }
}
