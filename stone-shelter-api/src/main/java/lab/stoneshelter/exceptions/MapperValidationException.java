package lab.stoneshelter.exceptions;

public class MapperValidationException extends IllegalStateException {
    public MapperValidationException(String message) {
        super(message);
    }
}
