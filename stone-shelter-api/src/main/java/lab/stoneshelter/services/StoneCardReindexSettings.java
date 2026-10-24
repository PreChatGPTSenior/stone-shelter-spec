package lab.stoneshelter.services;

import lab.stoneshelter.exceptions.StoneDocumentDependencyUnavailableException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class StoneCardReindexSettings {
    private final Environment environment;
    public StoneCardReindexSettings(Environment environment) { this.environment = environment; }

    @Transactional(readOnly = true)
    public int batchSize() {
        try {
            int value = Integer.parseInt(environment.getProperty("STONE_REINDEX_BATCH_SIZE", "20"));
            if (value > 0) return value;
        } catch (NumberFormatException exception) {
            throw new StoneDocumentDependencyUnavailableException("STONE_REINDEX_BATCH_SIZE must be a positive integer.", exception);
        }
        throw new StoneDocumentDependencyUnavailableException("STONE_REINDEX_BATCH_SIZE must be a positive integer.");
    }
}
