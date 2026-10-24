package lab.stoneshelter.exceptions;

public class InvalidStoneExactSearchOutputException extends RuntimeException {
    public InvalidStoneExactSearchOutputException() { super("Invalid structured exact-search output after repair."); }
    public InvalidStoneExactSearchOutputException(Throwable cause) { super("Invalid structured exact-search output after repair.", cause); }
}
