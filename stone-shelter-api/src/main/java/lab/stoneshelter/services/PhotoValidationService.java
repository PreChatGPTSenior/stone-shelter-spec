package lab.stoneshelter.services;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import javax.imageio.ImageIO;
import lab.stoneshelter.exceptions.InvalidPhotoException;
import lab.stoneshelter.exceptions.PhotoTooLargeException;
import lab.stoneshelter.exceptions.UnsupportedPhotoTypeException;
import lab.stoneshelter.shared.StonePhotoUploadRequest;
import lab.stoneshelter.shared.StonePhotoDraftUploadRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PhotoValidationService {
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Set<String> SUPPORTED = Set.of("image/jpeg", "image/png", "image/webp");

    @Transactional(readOnly = true)
    public byte[] validate(StonePhotoUploadRequest request) {
        validateSizeAndType(request.contentLength(), request.declaredMediaType());
        try {
            return validateBytes(request.readContent(), request.declaredMediaType());
        } catch (IOException exception) {
            throw new InvalidPhotoException(exception);
        }
    }

    @Transactional(readOnly = true)
    public byte[] validate(StonePhotoDraftUploadRequest request) {
        validateSizeAndType(request.contentLength(), request.declaredMediaType());
        try {
            return validateBytes(request.readContent(), request.declaredMediaType());
        } catch (IOException exception) {
            throw new InvalidPhotoException(exception);
        }
    }

    private void validateSizeAndType(long contentLength, String declaredMediaType) {
        if (contentLength > MAX_BYTES) throw new PhotoTooLargeException();
        if (contentLength == 0) throw new InvalidPhotoException();
        if (!SUPPORTED.contains(declaredMediaType == null ? "" : declaredMediaType)) {
            throw new UnsupportedPhotoTypeException();
        }
    }

    private byte[] validateBytes(byte[] bytes, String declaredMediaType) throws IOException {
        String actualType = signatureType(bytes);
        if (actualType == null) throw new InvalidPhotoException();
        if (!actualType.equals(declaredMediaType)) throw new UnsupportedPhotoTypeException();
        if (actualType.equals("image/webp") && !validWebp(bytes)) throw new InvalidPhotoException();
        try {
            if (ImageIO.read(new ByteArrayInputStream(bytes)) == null) throw new InvalidPhotoException();
        } catch (RuntimeException exception) {
            throw new InvalidPhotoException(exception);
        }
        return bytes;
    }

    private String signatureType(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10})) return "image/png";
        if (bytes.length >= 12 && text(bytes, 0).equals("RIFF") && text(bytes, 8).equals("WEBP")) return "image/webp";
        return null;
    }

    // Check RIFF boundaries; the ImageIO WebP decoder validates still and animated frame payloads.
    private boolean validWebp(byte[] bytes) {
        if (bytes.length < 20 || littleEndian(bytes, 4) + 8 != bytes.length) return false;
        int offset = 12;
        while (offset + 8 <= bytes.length) {
            long length = littleEndian(bytes, offset + 4);
            long end = offset + 8L + length + (length & 1);
            if (end > bytes.length) return false;
            offset = (int) end;
        }
        return offset == bytes.length;
    }

    private long littleEndian(byte[] bytes, int offset) {
        return (bytes[offset] & 255L) | ((bytes[offset + 1] & 255L) << 8)
                | ((bytes[offset + 2] & 255L) << 16) | ((bytes[offset + 3] & 255L) << 24);
    }

    private String text(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }
}
