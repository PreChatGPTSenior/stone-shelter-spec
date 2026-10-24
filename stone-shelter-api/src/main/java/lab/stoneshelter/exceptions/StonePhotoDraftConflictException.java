package lab.stoneshelter.exceptions;

public class StonePhotoDraftConflictException extends RuntimeException {
    public StonePhotoDraftConflictException() { super("A draft photograph is already associated with a stone."); }
}
