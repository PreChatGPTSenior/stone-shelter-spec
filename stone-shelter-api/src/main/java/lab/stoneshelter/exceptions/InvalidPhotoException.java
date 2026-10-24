package lab.stoneshelter.exceptions;

public class InvalidPhotoException extends RuntimeException {
    public InvalidPhotoException() { super("Invalid or corrupt image."); }
    public InvalidPhotoException(Throwable cause) { super("Invalid or corrupt image.", cause); }
}
