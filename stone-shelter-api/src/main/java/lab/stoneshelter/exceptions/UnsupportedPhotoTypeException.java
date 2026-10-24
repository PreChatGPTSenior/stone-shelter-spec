package lab.stoneshelter.exceptions;

public class UnsupportedPhotoTypeException extends RuntimeException {
    public UnsupportedPhotoTypeException() { super("Only matching JPEG, PNG or WebP images are supported."); }
    public UnsupportedPhotoTypeException(Throwable cause) { super("Only matching JPEG, PNG or WebP images are supported.", cause); }
}
