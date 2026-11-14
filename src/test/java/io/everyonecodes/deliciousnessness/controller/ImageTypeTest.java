package io.everyonecodes.deliciousnessness.controller;

import io.everyonecodes.deliciousnessness.security.WebLinks;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

// Only real pictures are stored, whatever the browser says the file is.
class ImageTypeTest {

    @Test
    void picturesAreKnownByTheirFirstBytes() {
        assertEquals("image/jpeg", ImageController.imageType(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
        assertEquals("image/png", ImageController.imageType(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10}));
        assertEquals("image/webp", ImageController.imageType("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    void anythingElseIsRefused() {
        assertNull(ImageController.imageType("<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)));
        assertNull(ImageController.imageType("<html>".getBytes(StandardCharsets.UTF_8)));
        assertNull(ImageController.imageType(new byte[]{(byte) 0xFF}));
        assertNull(ImageController.imageType(new byte[0]));
    }

    @Test
    void anUploadedPictureIsASafeLinkButOtherRelativeLinksAreNot() {
        assertTrue(WebLinks.isSafe("/images/12"));
        assertFalse(WebLinks.isSafe("/images/12/../../logout"));
        assertFalse(WebLinks.isSafe("//evil.example/x.png"));
        assertFalse(WebLinks.isSafe("javascript:alert(1)"));
    }
}
