package co.nitida.files.domain;

import co.nitida.shared.ApiException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageInputStream;

/**
 * Accepts JPEG, PNG or WebP by magic bytes (never by extension or Content-Type), decodes the pixels and
 * writes a brand-new JPEG: polyglot payloads and every metadata block (EXIF/GPS included) are dropped.
 */
public final class ImageSanitizer {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    /** Decompression-bomb guard: a small file can declare a gigantic canvas. */
    static final long MAX_PIXELS = 40_000_000L;

    private ImageSanitizer() {}

    public static byte[] sanitize(byte[] input) {
        if (input == null || input.length == 0) throw invalid();
        if (input.length > MAX_BYTES) {
            throw ApiException.unprocessable("FILE_TOO_LARGE", "La foto supera el máximo de 8 MB");
        }
        if (!hasAllowedMagic(input)) throw invalid();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            var readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw invalid();
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) throw invalid();
                return encodeJpeg(flatten(reader.read(0)));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) throw api;
            throw invalid();
        }
    }

    static boolean hasAllowedMagic(byte[] b) {
        if (b.length < 12) return false;
        boolean jpeg = (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
        boolean png = (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        boolean webp = b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        return jpeg || png || webp;
    }

    private static BufferedImage flatten(BufferedImage src) {
        var rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = rgb.createGraphics();
        g.drawImage(src, 0, 0, Color.WHITE, null); // transparent areas become white
        g.dispose();
        return rgb;
    }

    private static byte[] encodeJpeg(BufferedImage image) throws IOException {
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        var params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(0.85f);
        var out = new ByteArrayOutputStream();
        try (var ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), params); // null metadata: nothing carried over
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static ApiException invalid() {
        return ApiException.unprocessable("INVALID_IMAGE", "El archivo no es una foto válida (JPEG, PNG o WebP)");
    }
}
