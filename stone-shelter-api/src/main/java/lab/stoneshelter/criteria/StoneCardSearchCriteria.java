package lab.stoneshelter.criteria;

public record StoneCardSearchCriteria(double[] vector, String profile, int limit, double minimumSimilarity) {
    public StoneCardSearchCriteria { vector = vector.clone(); }
    @Override public double[] vector() { return vector.clone(); }
}
