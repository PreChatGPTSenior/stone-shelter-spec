package lab.stoneshelter.services;

import lab.stoneshelter.exceptions.InvalidStoneCardRequestException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StoneCardSearchSettings {
    private final Environment environment;

    public StoneCardSearchSettings(Environment environment) { this.environment = environment; }

    public double minimumSimilarity() {
        String configured = environment.getProperty("STONE_CARD_SEARCH_MIN_SIMILARITY");
        if (configured == null) { return 0.5; }
        try {
            double minimumSimilarity = Double.parseDouble(configured);
            if (!Double.isFinite(minimumSimilarity) || minimumSimilarity < -1 || minimumSimilarity > 1) {
                throw new NumberFormatException();
            }
            return minimumSimilarity;
        } catch (NumberFormatException exception) {
            throw new InvalidStoneCardRequestException("STONE_CARD_SEARCH_MIN_SIMILARITY must be finite and between -1 and 1.");
        }
    }
}
