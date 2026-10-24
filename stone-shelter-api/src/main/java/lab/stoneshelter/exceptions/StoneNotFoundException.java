package lab.stoneshelter.exceptions;

public class StoneNotFoundException extends RuntimeException {
    public StoneNotFoundException(long id) { super("Stone " + id + " was not found."); }
}
