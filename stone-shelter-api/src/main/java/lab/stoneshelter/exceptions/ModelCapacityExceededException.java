package lab.stoneshelter.exceptions;

public class ModelCapacityExceededException extends RuntimeException {
    public ModelCapacityExceededException() { super("Model invocation capacity is occupied."); }
}
