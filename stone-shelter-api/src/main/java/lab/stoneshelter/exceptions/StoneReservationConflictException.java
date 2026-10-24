package lab.stoneshelter.exceptions;

public class StoneReservationConflictException extends RuntimeException {
    public StoneReservationConflictException(long id) {
        super("Stone " + id + " is not available for reservation or already has a reservation.");
    }
}
