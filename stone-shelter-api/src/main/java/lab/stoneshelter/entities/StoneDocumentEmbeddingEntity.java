package lab.stoneshelter.entities;

public record StoneDocumentEmbeddingEntity(String model, String profile, double[] vector, int inputTokens) {
    public StoneDocumentEmbeddingEntity {
        vector = vector.clone();
    }

    @Override
    public double[] vector() {
        return vector.clone();
    }
}
