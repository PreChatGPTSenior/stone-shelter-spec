package lab.stoneshelter.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lab.stoneshelter.criteria.StoneMarkdownDocumentCriteria;
import lab.stoneshelter.criteria.StoneMarkdownSectionCriteria;
import lab.stoneshelter.entities.StoneDocumentFragmentEntity;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.exceptions.StoneDocumentSourceUnindexableException;
import lab.stoneshelter.mappers.entities.StoneMarkdownSectionCriteriaToStoneDocumentFragmentEntityMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StoneMarkdownDocumentService {
    private static final Set<String> EXCLUDED = Set.of("About This Guide", "Contents", "Sources and Editorial Notes");
    private final StoneDocumentEmbeddingService stoneDocumentEmbeddingService;
    private final StoneMarkdownSectionCriteriaToStoneDocumentFragmentEntityMapper mapper;

    public StoneMarkdownDocumentService(StoneDocumentEmbeddingService stoneDocumentEmbeddingService,
            StoneMarkdownSectionCriteriaToStoneDocumentFragmentEntityMapper mapper) {
        this.stoneDocumentEmbeddingService = stoneDocumentEmbeddingService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<StoneDocumentFragmentEntity> process(StoneMarkdownDocumentCriteria stoneMarkdownDocumentCriteria) {
        if (stoneMarkdownDocumentCriteria == null || stoneMarkdownDocumentCriteria.text() == null
                || stoneMarkdownDocumentCriteria.text().isBlank() || stoneMarkdownDocumentCriteria.maxFragments() < 1) {
            throw new StoneDocumentSourceUnindexableException("The Markdown source has no useful passages.");
        }
        List<StoneDocumentFragmentEntity> fragments = new ArrayList<>();
        String title = "";
        String parent = "";
        String topic = "";
        StringBuilder body = new StringBuilder();
        for (String line : stoneMarkdownDocumentCriteria.text().replace("\r\n", "\n").split("\n")) {
            if (line.startsWith("# ") || line.startsWith("## ") || line.startsWith("### ")) {
                append(fragments, title, parent, topic, body.toString(), stoneMarkdownDocumentCriteria);
                body.setLength(0);
                if (line.startsWith("# ")) { title = line.substring(2).strip(); parent = ""; topic = ""; }
                else if (line.startsWith("## ")) { parent = line.substring(3).strip(); topic = ""; }
                else { topic = line.substring(4).strip(); }
            } else { body.append(line).append('\n'); }
        }
        append(fragments, title, parent, topic, body.toString(), stoneMarkdownDocumentCriteria);
        if (fragments.isEmpty()) throw new StoneDocumentSourceUnindexableException("The Markdown source has no useful passages.");
        return List.copyOf(fragments);
    }

    private void append(List<StoneDocumentFragmentEntity> fragments, String title, String parent, String topic,
            String body, StoneMarkdownDocumentCriteria stoneMarkdownDocumentCriteria) {
        if (title.isBlank() || parent.isBlank() || EXCLUDED.contains(parent) || body.isBlank()) return;
        StoneType stoneType = stoneType(parent);
        String path = title + " > " + parent + (topic.isBlank() ? (stoneType == null ? "" : " > Attributes") : " > " + topic);
        if (parent.equals("Quick Comparison")) {
            comparison(fragments, title, body, stoneMarkdownDocumentCriteria);
            return;
        }
        String context = path.replace("Fictional Care Ritual", "Care Ritual");
        List<String> blocks = blocks(body.strip(), context);
        List<String> current = new ArrayList<>();
        for (String block : blocks) {
            if (!current.isEmpty() && tokens(context + "\n" + String.join("\n\n", current) + "\n\n" + block) > 500) {
                add(fragments, path, stoneType, context + "\n" + String.join("\n\n", current), stoneMarkdownDocumentCriteria);
                List<String> overlap = new ArrayList<>();
                for (int i = current.size() - 1; i >= 0; i--) {
                    if (tokens(String.join("\n\n", overlap) + "\n\n" + current.get(i)) > 50) break;
                    overlap.addFirst(current.get(i));
                }
                current = overlap;
                if (tokens(context + "\n" + String.join("\n\n", current) + "\n\n" + block) > 500) current.clear();
            }
            current.add(block);
        }
        if (!current.isEmpty()) add(fragments, path, stoneType, context + "\n" + String.join("\n\n", current), stoneMarkdownDocumentCriteria);
    }

    private List<String> blocks(String body, String context) {
        List<String> blocks = new ArrayList<>();
        for (String paragraph : body.split("\\n\\s*\\n")) {
            List<String> units = paragraph.startsWith("- ") || paragraph.matches("^\\d+\\..*")
                    ? List.of(paragraph.split("\\n(?=(?:- |\\d+\\. ))")) : List.of(paragraph);
            for (String unit : units) {
                if (tokens(context + "\n" + unit) > 500 && !unit.startsWith("|")
                        && !unit.startsWith("- ") && !unit.matches("^\\d+\\..*")) {
                    for (String sentence : unit.split("(?<=[.!?])\\s+(?=[A-Z\"“])")) {
                        checkCapacity(context + "\n" + sentence);
                        blocks.add(sentence);
                    }
                } else { checkCapacity(context + "\n" + unit); blocks.add(unit); }
            }
        }
        return blocks;
    }

    private void comparison(List<StoneDocumentFragmentEntity> fragments, String title, String body,
            StoneMarkdownDocumentCriteria stoneMarkdownDocumentCriteria) {
        String[] lines = body.strip().split("\n");
        StringBuilder qualification = new StringBuilder();
        String[] columns = null;
        for (String line : lines) {
            if (!line.startsWith("|")) { if (columns == null) qualification.append(line).append('\n'); continue; }
            String[] cells = line.substring(1, line.lastIndexOf('|')).split("\\|", -1);
            if (columns == null) { columns = cells; continue; }
            if (cells[0].strip().matches(":?-+:?")) continue;
            if (cells.length != columns.length) throw new StoneDocumentSourceUnindexableException("Invalid comparison table.");
            String name = cells[0].strip();
            StringBuilder text = new StringBuilder(title + " > Quick Comparison > " + name + "\n" + qualification);
            for (int i = 0; i < columns.length; i++) text.append(columns[i].strip()).append(": ").append(cells[i].strip()).append('\n');
            checkCapacity(text.toString());
            add(fragments, title + " > Quick Comparison > " + name, stoneType(name), text.toString().strip(), stoneMarkdownDocumentCriteria);
        }
    }

    private void add(List<StoneDocumentFragmentEntity> fragments, String path, StoneType stoneType, String text,
            StoneMarkdownDocumentCriteria stoneMarkdownDocumentCriteria) {
        if (fragments.size() >= stoneMarkdownDocumentCriteria.maxFragments()) throw new StoneDocumentSourceUnindexableException("The fragment limit is exceeded.");
        StoneMarkdownSectionCriteria stoneMarkdownSectionCriteria = new StoneMarkdownSectionCriteria();
        stoneMarkdownSectionCriteria.setSectionPath(path);
        stoneMarkdownSectionCriteria.setStoneType(stoneType);
        stoneMarkdownSectionCriteria.setText(text);
        stoneMarkdownSectionCriteria.setSourceVersion(stoneMarkdownDocumentCriteria.sourceVersion());
        stoneMarkdownSectionCriteria.setFragmentOrder(fragments.size());
        fragments.add(mapper.toEntity(stoneMarkdownSectionCriteria));
    }

    private int tokens(String text) { return stoneDocumentEmbeddingService.estimateTokens(text); }
    private void checkCapacity(String text) {
        if (tokens(text) > 32768) throw new StoneDocumentSourceUnindexableException("An indivisible passage exceeds model input capacity.");
    }
    private StoneType stoneType(String heading) {
        try { return StoneType.valueOf(heading.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { return null; }
    }
}
