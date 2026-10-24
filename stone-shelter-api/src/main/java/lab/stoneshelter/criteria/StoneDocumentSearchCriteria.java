package lab.stoneshelter.criteria;

public record StoneDocumentSearchCriteria(double[] vector, String profile, int limit) {
    public StoneDocumentSearchCriteria { vector = vector.clone(); }
    @Override public double[] vector() { return vector.clone(); }
}
