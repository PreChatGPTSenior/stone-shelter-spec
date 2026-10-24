package lab.stoneshelter.exceptions;

public class InvalidAdmissionDateRangeException extends RuntimeException {
    public InvalidAdmissionDateRangeException() {
        super("Admission date From must not be after To.");
    }
}
