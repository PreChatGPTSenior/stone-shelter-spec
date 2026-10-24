package lab.stoneshelter.criteria;

public record StoneSemanticWish(String key, String text) {
    public StoneSemanticWish {
        if (key == null || !key.matches("[a-z][a-z0-9_-]{0,31}") || text == null || text.isBlank()) {
            throw new IllegalArgumentException("A stable wish key and explicit text are required");
        }
    }
}
