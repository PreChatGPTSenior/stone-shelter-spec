package lab.stoneshelter.entities;

import java.util.List;

public record StoneDocumentSearchResultEntity(List<Passage> passages) {
    public StoneDocumentSearchResultEntity { passages = List.copyOf(passages); }
    public record Passage(StoneDocumentFragmentEntity fragment, double score) { }
}
