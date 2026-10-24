package lab.stoneshelter.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "One draft JPEG, PNG or WebP image, up to 10 MiB.")
public class StonePhotoDraftUploadRequest {
    @NotNull
    @Schema(description = "Required image file.", type = "string", format = "binary")
    private MultipartFile file;
    public StonePhotoDraftUploadRequest() {}
    public MultipartFile getFile() { return file; }
    public void setFile(MultipartFile file) { this.file = file; }
    public long contentLength() { return file.getSize(); }
    public String declaredMediaType() { return file.getContentType(); }
    public byte[] readContent() throws IOException { return file.getBytes(); }
}
