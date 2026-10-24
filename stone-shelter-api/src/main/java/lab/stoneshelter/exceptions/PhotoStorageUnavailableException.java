package lab.stoneshelter.exceptions;

public class PhotoStorageUnavailableException extends RuntimeException {
    public PhotoStorageUnavailableException() { super("Photo storage is unavailable. Try again later."); }
    public PhotoStorageUnavailableException(Throwable cause) { super("Photo storage is unavailable. Try again later.", cause); }
}
