package lab.stoneshelter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lab.stoneshelter.entities.StoneDocumentIndexEntity;
import lab.stoneshelter.services.StoneEncyclopediaIndexService;
import lab.stoneshelter.services.StoneDocumentRetrievalService;
import lab.stoneshelter.shared.StoneDocumentSearchRequest;
import lab.stoneshelter.shared.StoneDocumentPassageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfSystemProperty(named = "stone.document.qualification", matches = "true")
class StoneDocumentRetrievalQualificationTest extends IntegrationTest {
    @Autowired private StoneEncyclopediaIndexService indexService;
    @Autowired private StoneDocumentRetrievalService retrievalService;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM stone_document_fragment WHERE document_key = ?", StoneDocumentIndexEntity.DOCUMENT_KEY);
        jdbc.update("UPDATE stone_document_index SET active_operation_key = NULL, owner_token = NULL WHERE document_key = ?", StoneDocumentIndexEntity.DOCUMENT_KEY);
        jdbc.update("DELETE FROM stone_document_index_operation WHERE document_key = ?", StoneDocumentIndexEntity.DOCUMENT_KEY);
        jdbc.update("DELETE FROM stone_document_index WHERE document_key = ?", StoneDocumentIndexEntity.DOCUMENT_KEY);
    }

    @Test
    void qualifiesPredeclaredTwentyQueriesAgainstRealMinioAndInstalledModel() throws Exception {
        var imported = indexService.importIndex(UUID.randomUUID());
        assertThat(imported.indexedFragments()).isPositive();
        System.out.println("QUALIFICATION indexedFragments=" + imported.indexedFragments() + " sourceVersion=" + imported.sourceVersion());
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT embedding_profile) FROM stone_document_fragment WHERE document_key = ?",
                Integer.class, StoneDocumentIndexEntity.DOCUMENT_KEY)).isEqualTo(1);
        List<String> failures = new ArrayList<>();
        int total = 0;
        try (var stream = getClass().getResourceAsStream("/stone-document-index/retrieval-queries.json")) {
            var fixture = JsonMapper.builder().build().readTree(stream);
            assertThat(fixture.size()).isEqualTo(20);
            for (var query : fixture) {
                List<StoneDocumentPassageResponse> passages = retrievalService.search(new StoneDocumentSearchRequest(query.path("query").asText(), 5)).passages();
                boolean success = true;
                for (var group : query.path("relevantGroups")) {
                    boolean matched = false;
                    for (var relevant : group) {
                        matched |= passages.stream().anyMatch(p -> p.sectionPath().equals("Stone Encyclopedia > " + relevant.asText()));
                    }
                    success &= matched;
                }
                String id = query.path("id").asText();
                if (!success) failures.add(id);
                total++;
                System.out.println("QUALIFICATION " + id + " " + (success ? "PASS" : "FAIL") + " "
                        + passages.stream().map(StoneDocumentPassageResponse::sectionPath).toList());
            }
        }
        assertThat(failures).as("mandatory character, ritual and two-type comparison controls").doesNotContain("Q03", "Q04", "Q09");
        assertThat(total - failures.size()).as("successful predefined relevant groups within top five: failures=" + failures).isGreaterThanOrEqualTo(18);
    }
}
