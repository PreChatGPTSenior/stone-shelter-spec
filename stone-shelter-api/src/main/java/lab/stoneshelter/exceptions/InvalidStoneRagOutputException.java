package lab.stoneshelter.exceptions;

public class InvalidStoneRagOutputException extends RuntimeException {
    public InvalidStoneRagOutputException() { super("Invalid structured answer output after the permitted correction"); }
    public InvalidStoneRagOutputException(Throwable cause) { super("Invalid structured answer output after the permitted correction", cause); }
}
