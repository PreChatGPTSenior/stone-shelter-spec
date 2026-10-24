package lab.stoneshelter.exceptions;

public class InvalidStonePhotoDraftException extends RuntimeException {
    public InvalidStonePhotoDraftException() { super("Photo references must be distinct, known and unexpired."); }
}
