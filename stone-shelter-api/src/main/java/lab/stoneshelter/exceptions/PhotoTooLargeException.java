package lab.stoneshelter.exceptions;

public class PhotoTooLargeException extends RuntimeException {
    public PhotoTooLargeException() { super("Image exceeds the 10 MiB limit."); }
    public PhotoTooLargeException(Throwable cause) { super("Image exceeds the 10 MiB limit.", cause); }
}
