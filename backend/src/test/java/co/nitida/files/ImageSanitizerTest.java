package co.nitida.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.files.domain.ImageSanitizer;
import co.nitida.shared.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageSanitizerTest {

    static byte[] image(String format, int w, int h) throws Exception {
        var img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /** Inserts an APP1/EXIF segment carrying fake GPS text right after the JPEG start marker. */
    static byte[] withExifGps(byte[] jpeg) {
        byte[] payload = "Exif\0\0GPSLatitude=1.2136 GPSLongitude=-77.2811".getBytes(StandardCharsets.US_ASCII);
        var out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write((payload.length + 2) >> 8);
        out.write((payload.length + 2) & 0xFF);
        out.write(payload, 0, payload.length);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    @Test
    void jpegAndPngAreAcceptedAndComeOutAsJpeg() throws Exception {
        for (String format : new String[] {"jpg", "png"}) {
            byte[] clean = ImageSanitizer.sanitize(image(format, 40, 30));
            var decoded = ImageIO.read(new ByteArrayInputStream(clean));
            assertThat(decoded.getWidth()).isEqualTo(40);
            assertThat(clean[0] & 0xFF).isEqualTo(0xFF);
            assertThat(clean[1] & 0xFF).isEqualTo(0xD8);
        }
    }

    @Test
    void exifGpsMetadataDoesNotSurvive() throws Exception {
        byte[] dirty = withExifGps(image("jpg", 40, 30));
        assertThat(new String(dirty, StandardCharsets.ISO_8859_1)).contains("GPSLatitude");
        byte[] clean = ImageSanitizer.sanitize(dirty);
        String text = new String(clean, StandardCharsets.ISO_8859_1);
        assertThat(text).doesNotContain("GPSLatitude").doesNotContain("Exif");
    }

    @Test
    void bytesAppendedAfterTheImageAreDropped() throws Exception {
        byte[] polyglot = concat(image("jpg", 20, 20), "<script>alert(1)</script>PK\u0003\u0004".getBytes(StandardCharsets.ISO_8859_1));
        byte[] clean = ImageSanitizer.sanitize(polyglot);
        assertThat(new String(clean, StandardCharsets.ISO_8859_1)).doesNotContain("<script>").doesNotContain("PK\u0003\u0004");
    }

    @Test
    void contentIsJudgedByMagicBytesNotByWhatTheClientClaims() {
        byte[] text = "esto no es una foto, aunque se llame foto.jpg".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> ImageSanitizer.sanitize(text)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo("INVALID_IMAGE");
        byte[] gif = "GIF89a\u0001\u0000\u0001\u0000\u0000\u0000\u0000;".getBytes(StandardCharsets.ISO_8859_1);
        assertThatThrownBy(() -> ImageSanitizer.sanitize(gif)).isInstanceOf(ApiException.class);
    }

    @Test
    void truncatedOrEmptyInputIsRejected() throws Exception {
        assertThatThrownBy(() -> ImageSanitizer.sanitize(new byte[0])).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> ImageSanitizer.sanitize(null)).isInstanceOf(ApiException.class);
        byte[] png = image("png", 30, 30);
        byte[] half = java.util.Arrays.copyOf(png, png.length / 2);
        assertThatThrownBy(() -> ImageSanitizer.sanitize(half)).isInstanceOf(ApiException.class);
    }

    @Test
    void sizeLimitIsEightMegabytes() {
        byte[] big = new byte[ImageSanitizer.MAX_BYTES + 1];
        assertThatThrownBy(() -> ImageSanitizer.sanitize(big)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo("FILE_TOO_LARGE");
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    /** A 48 MP phone photo is a normal upload: it is accepted and shrunk while decoding, not rejected. */
    @Test
    void aHugeButLegitimatePhotoIsShrunkInsteadOfRejected() throws Exception {
        var gray = new BufferedImage(7000, 6000, BufferedImage.TYPE_BYTE_GRAY); // 42 MP, a few KB as PNG
        var out = new ByteArrayOutputStream();
        ImageIO.write(gray, "png", out);
        byte[] clean = ImageSanitizer.sanitize(out.toByteArray());
        var result = ImageIO.read(new java.io.ByteArrayInputStream(clean));
        assertThat(Math.max(result.getWidth(), result.getHeight())).isLessThanOrEqualTo(4096);
    }

    @Test
    void aCanvasBeyondAnyRealCameraIsStillRejected() throws Exception {
        var gray = new BufferedImage(12_000, 10_000, BufferedImage.TYPE_BYTE_GRAY); // 120 MP
        var out = new ByteArrayOutputStream();
        ImageIO.write(gray, "png", out);
        assertThatThrownBy(() -> ImageSanitizer.sanitize(out.toByteArray())).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo("INVALID_IMAGE");
    }
}
