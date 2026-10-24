package lab.stoneshelter.exceptions;

public class StonePhotoDraftNotFoundException extends RuntimeException {
    public StonePhotoDraftNotFoundException() { super("Draft photograph is unknown, expired, or already associated."); }
}
