package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.model.RecipeImage;
import io.everyonecodes.deliciousnessness.repository.RecipeImageRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

// Pictures uploaded from the recipe form. The upload answers with the picture's link, which the form
// puts in its Image URL box.
@RestController
@RequestMapping("/images")
public class ImageController {

    private final RecipeImageRepository images;

    public ImageController(RecipeImageRepository images) {
        this.images = images;
    }

    @PostMapping
    public Map<String, String> upload(@RequestParam MultipartFile file) throws IOException {
        byte[] data = file.getBytes();
        String type = imageType(data);
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG and WebP pictures");
        }
        return Map.of("url", "/images/" + images.save(new RecipeImage(type, data)).getId());
    }

    // A picture never changes under its link, so browsers may keep it.
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> show(@PathVariable Long id) {
        return images.findById(id)
                .map(image -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(image.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                        .body(image.getData()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // The type is read from the file's first bytes, not from what the browser says it is, so nothing
    // but a picture (no HTML, no SVG with scripts) is ever served from here.
    static String imageType(byte[] data) {
        if (startsWith(data, 0, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (startsWith(data, 0, 0x89, 'P', 'N', 'G')) return "image/png";
        if (startsWith(data, 0, 'R', 'I', 'F', 'F') && startsWith(data, 8, 'W', 'E', 'B', 'P')) return "image/webp";
        return null;
    }

    private static boolean startsWith(byte[] data, int offset, int... expected) {
        if (data.length < offset + expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }
}
