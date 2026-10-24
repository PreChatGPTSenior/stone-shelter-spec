package lab.stoneshelter.services;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StoneSemanticSearchSettings {
    private record Limits(int candidates, int documents, int evidenceCalls, double documentSimilarity) {}
    private final Limits limits;
    private final StoneCardSearchSettings stoneCardSearchSettings;

    public StoneSemanticSearchSettings(Environment environment, StoneCardSearchSettings stoneCardSearchSettings) {
        limits = new Limits(integer(environment, "STONE_SEMANTIC_SEARCH_CANDIDATE_LIMIT", 10, 6),
                integer(environment, "STONE_SEMANTIC_SEARCH_DOCUMENT_LIMIT", 10, 1),
                integer(environment, "STONE_SEMANTIC_SEARCH_MAX_EVIDENCE_CALLS", 20, 1),
                similarity(environment, "STONE_SEMANTIC_SEARCH_DOCUMENT_MIN_SIMILARITY", 0.5));
        this.stoneCardSearchSettings = stoneCardSearchSettings;
        stoneCardSearchSettings.minimumSimilarity();
    }

    public int candidateLimit() { return limits.candidates(); }
    public int documentLimit() { return limits.documents(); }
    public int maximumEvidenceCalls() { return limits.evidenceCalls(); }
    public double documentMinimumSimilarity() { return limits.documentSimilarity(); }
    public double cardMinimumSimilarity() { return stoneCardSearchSettings.minimumSimilarity(); }

    private int integer(Environment environment, String name, int fallback, int minimum) {
        try {
            int value = Integer.parseInt(environment.getProperty(name, Integer.toString(fallback)));
            if (value < minimum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) { throw new IllegalArgumentException(name + " must be at least " + minimum, exception); }
    }

    private double similarity(Environment environment, String name, double fallback) {
        try {
            double value = Double.parseDouble(environment.getProperty(name, Double.toString(fallback)));
            if (!Double.isFinite(value) || value < -1 || value > 1) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) { throw new IllegalArgumentException(name + " must be finite and within [-1,1]", exception); }
    }
}
