package live.curricula.web.catalog;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphArchiveTests {
    @Test void searchesTheRealPinnedArchiveWithoutCanonicalizingItsIdentifiers() throws Exception {
        String configured = System.getenv("CURRICULA_TEST_ARCHIVE_PATH");
        Path archive = Path.of(configured == null ? "target/graph-archive.json" : configured);
        if (configured == null) Assumptions.assumeTrue(Files.isRegularFile(archive), "Fetch the public graph archive");
        var catalog = new GraphArchive(archive.toString());
        assertTrue(catalog.available());
        var match = catalog.search("VLOOKUP IN CALCUL TABELAR").getFirst();
        assertEquals("Spreadsheet VLOOKUP", match.label());
        assertTrue(match.id().startsWith("https://curricula.live/knowledge/computing/concept/"));
        assertTrue(match.url().startsWith("https://graph.curricula.live/?concept=https%3A%2F%2F"));
        assertTrue(catalog.search("unmatched arch test value").isEmpty());
    }

    @Test void rejectsTamperedInputAndExternalDeepLinks(@TempDir Path dir) throws IOException {
        assertFalse(new GraphArchive("").available());
        var bad = Files.writeString(dir.resolve("bad.json"), "{\"nodes\":[]}");
        assertThrows(IOException.class, () -> new GraphArchive(bad.toString()));
        assertThrows(IllegalArgumentException.class, () -> GraphArchive.legacyUrl("https://example.org/redirect"));
    }
}
