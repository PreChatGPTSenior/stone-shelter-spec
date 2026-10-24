package lab.stoneshelter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lab.stoneshelter.criteria.StoneMarkdownDocumentCriteria;
import lab.stoneshelter.entities.StoneDocumentFragmentEntity;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.exceptions.StoneDocumentSourceUnindexableException;
import lab.stoneshelter.mappers.entities.StoneMarkdownSectionCriteriaToStoneDocumentFragmentEntityMapper;
import lab.stoneshelter.services.StoneDocumentEmbeddingService;
import lab.stoneshelter.services.StoneMarkdownDocumentService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoneMarkdownDocumentTest {
    private final StoneMarkdownDocumentService service = new StoneMarkdownDocumentService(
            new StoneDocumentEmbeddingService(null, null), new StoneMarkdownSectionCriteriaToStoneDocumentFragmentEntityMapper());

    private List<StoneDocumentFragmentEntity> process(String text) {
        return service.process(new StoneMarkdownDocumentCriteria(text, "a".repeat(64), 2000));
    }

    @Test
    void encyclopediaPreservesAllTypesAndExcludesEditorialContext() throws Exception {
        List<StoneDocumentFragmentEntity> fragments = process(Files.readString(
                Path.of("../stone-rules/stone-encyclopedias/stone-encyclopedia_en.md")));
        assertThat(fragments).extracting(StoneDocumentFragmentEntity::getStoneType).contains(StoneType.values());
        assertThat(fragments).allSatisfy(fragment -> {
            assertThat(fragment.getDocumentKey()).isEqualTo("stone-encyclopedia-en");
            assertThat(fragment.getLanguage()).isEqualTo("en");
            assertThat(fragment.getText()).doesNotContain("About This Guide", "Sources and Editorial Notes", "Contents", "Fictional Care Ritual");
        });
        for (int i = 0; i < fragments.size(); i++) assertThat(fragments.get(i).getFragmentOrder()).isEqualTo(i);
        assertThat(fragments.stream().filter(f -> f.getSectionPath().contains("Fictional Care Ritual")))
                .hasSize(10).allSatisfy(f -> assertThat(f.getText()).contains("Care Ritual"));
        assertThat(fragments.stream().filter(f -> f.getSectionPath().contains("Quick Comparison")))
                .hasSize(10).allSatisfy(f -> assertThat(f.getText()).contains("Fragility:", "Care Demand:", "without varnish"));
        assertThat(fragments.stream().filter(f -> f.getSectionPath().contains("Rating Scales")))
                .allSatisfy(f -> assertThat(f.getStoneType()).isNull());
        assertThat(fragments.stream().filter(f -> f.getSectionPath().endsWith("Fragility Scale")))
                .anySatisfy(f -> assertThat(f.getText()).contains("| Score | Level | Meaning |", "differs from hardness"));
    }

    @Test
    void shortTopicsAndAttributesRemainSeparate() {
        List<StoneDocumentFragmentEntity> fragments = process("# Guide\nOpening excluded.\n## Basalt\nArchetype: quiet.\n### Fictional Care Ritual\nEnjoy silence.\n### Monthly Care\nRemove dust.");
        assertThat(fragments).hasSize(3);
        assertThat(fragments.get(0).getText()).contains("Guide > Basalt > Attributes", "quiet");
        assertThat(fragments.get(1).getText()).contains("Guide > Basalt > Care Ritual").doesNotContain("dust");
        assertThat(fragments.get(2).getText()).contains("Remove dust.").doesNotContain("silence");
    }

    @Test
    void longParagraphSplitsAtWholeSentencesWithBoundedOverlap() {
        String sentence = "A " + "gentle ".repeat(45) + "stone.";
        List<StoneDocumentFragmentEntity> fragments = process("# Guide\n## Basalt\n### Care\n" + (sentence + " ").repeat(12));
        assertThat(fragments).hasSizeGreaterThan(1);
        assertThat(fragments).allSatisfy(f -> {
            assertThat(f.getText()).endsWith("stone.");
            assertThat(new StoneDocumentEmbeddingService(null, null).estimateTokens(f.getText())).isLessThanOrEqualTo(500);
        });
    }

    @Test
    void smallBlocksOverlapOnlyWithinOneTopicAndListsStayWhole() {
        String paragraph = "Keep " + "calm ".repeat(12) + "today.";
        List<StoneDocumentFragmentEntity> fragments = process("# Guide\n## Basalt\n### Care\n"
                + (paragraph + "\n\n").repeat(35) + "### Other\nUnique end.");
        assertThat(fragments).hasSizeGreaterThan(2);
        assertThat(fragments.get(1).getText()).startsWith("Guide > Basalt > Care\n" + paragraph);
        assertThat(fragments.getLast().getText()).isEqualTo("Guide > Basalt > Other\nUnique end.");
        assertThat(process("# Guide\n## Basalt\n### Care\n- " + "word ".repeat(600) + "\n  continued."))
                .singleElement().satisfies(f -> assertThat(f.getText()).contains("continued."));
    }

    @Test
    void rejectsEmptyExcludedExcessiveAndIndivisibleInputs() {
        assertThatThrownBy(() -> process("# Guide\n## Contents\nNo passages."))
                .isInstanceOf(StoneDocumentSourceUnindexableException.class);
        assertThatThrownBy(() -> process("# Guide\n## Basalt\n### Care\n- " + "word ".repeat(33000)))
                .isInstanceOf(StoneDocumentSourceUnindexableException.class);
        assertThatThrownBy(() -> service.process(new StoneMarkdownDocumentCriteria(
                "# Guide\n## Basalt\nAttributes\n### Care\nDust", "a".repeat(64), 1)))
                .isInstanceOf(StoneDocumentSourceUnindexableException.class);
    }
}
