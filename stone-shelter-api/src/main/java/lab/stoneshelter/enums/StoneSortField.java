package lab.stoneshelter.enums;

public enum StoneSortField {
    NAME("name"),
    STONE_SIZE("stoneSize"),
    ADMISSION_DATE("admissionDate");

    private final String value;

    StoneSortField(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static StoneSortField fromValue(String value) {
        for (StoneSortField field : values()) {
            if (field.value.equals(value)) {
                return field;
            }
        }
        throw new IllegalArgumentException("Unsupported Stone sort field: " + value);
    }
}
