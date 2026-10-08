package dev.axonia.media;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

import dev.axonia.ValidationException;
import lombok.experimental.UtilityClass;

/**
 * Reads a multipart part into an {@link ImageUpload}.
 *
 * <p>
 * Every image entering the product does so through a controller holding a {@link MultipartFile}, and every one of them
 * needs the same two lines: the bytes, and a refusal the member can read when the part cannot be read at all. Three
 * controllers each carried their own byte-identical copy, small enough to sit under the duplication gate's token
 * threshold and therefore never reported. It lives here, beside {@link ImageValidator}, because reading the part is the
 * step before validating it, not a concern of whichever feature happens to own the endpoint.
 */
@UtilityClass
public class ImageUploads {

    public ImageUpload from(MultipartFile file) {

        try {
            return new ImageUpload(file.getBytes(), file.getContentType(), file.getOriginalFilename());
        } catch (IOException e) {
            throw new ValidationException("INVALID_IMAGE", "Could not read the uploaded file");
        }
    }
}
