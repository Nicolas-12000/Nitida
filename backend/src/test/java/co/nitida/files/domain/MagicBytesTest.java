package co.nitida.files.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The file type is judged by its first bytes only (spec §11): every byte of each signature matters. */
class MagicBytesTest {
    private static byte[] padded(int... head) {
        byte[] b = new byte[16];
        for (int i = 0; i < head.length; i++) b[i] = (byte) head[i];
        return b;
    }

    private static byte[] webp(String riff, String webp) {
        byte[] b = new byte[16];
        System.arraycopy(riff.getBytes(StandardCharsets.US_ASCII), 0, b, 0, 4);
        System.arraycopy(webp.getBytes(StandardCharsets.US_ASCII), 0, b, 8, 4);
        return b;
    }

    @Test
    void jpegNeedsAllThreeSignatureBytes() {
        assertThat(ImageSanitizer.hasAllowedMagic(padded(0xFF, 0xD8, 0xFF))).isTrue();
        assertThat(ImageSanitizer.hasAllowedMagic(padded(0xFE, 0xD8, 0xFF))).isFalse();
        assertThat(ImageSanitizer.hasAllowedMagic(padded(0xFF, 0xD9, 0xFF))).isFalse();
        assertThat(ImageSanitizer.hasAllowedMagic(padded(0xFF, 0xD8, 0xFE))).isFalse();
    }

    @Test
    void pngNeedsAllFourSignatureBytes() {
        assertThat(ImageSanitizer.hasAllowedMagic(padded(0x89, 'P', 'N', 'G'))).isTrue();
        assertThat(ImageSanitizer.hasAllowedMagic(padded(0x88, 'P', 'N', 'G'))).isFalse();
        assertThat(ImageSanitizer.hasAllowedMagic(padded(0x89, 'Q', 'N', 'G'))).isFalse();
        assertThat(ImageSanitizer.hasAllowedMagic(padded(0x89, 'P', 'M', 'G'))).isFalse();
        assertThat(ImageSanitizer.hasAllowedMagic(padded(0x89, 'P', 'N', 'H'))).isFalse();
    }

    @Test
    void webpNeedsRiffAndWebpMarkers() {
        assertThat(ImageSanitizer.hasAllowedMagic(webp("RIFF", "WEBP"))).isTrue();
        assertThat(ImageSanitizer.hasAllowedMagic(webp("RIFX", "WEBP"))).isFalse();
        assertThat(ImageSanitizer.hasAllowedMagic(webp("RIFF", "WEBQ"))).isFalse();
        assertThat(ImageSanitizer.hasAllowedMagic(webp("RIFF", "WAVE"))).isFalse();
        assertThat(ImageSanitizer.hasAllowedMagic(webp("RJFF", "WEBP"))).isFalse();
        assertThat(ImageSanitizer.hasAllowedMagic(webp("RIFF", "XEBP"))).isFalse();
        assertThat(ImageSanitizer.hasAllowedMagic(webp("RIFF", "WXBP"))).isFalse();
        assertThat(ImageSanitizer.hasAllowedMagic(webp("RIFF", "WEXP"))).isFalse();
    }

    @Test
    void tooShortToCarryASignatureIsRejected() {
        byte[] eleven = new byte[11];
        eleven[0] = (byte) 0xFF; eleven[1] = (byte) 0xD8; eleven[2] = (byte) 0xFF;
        assertThat(ImageSanitizer.hasAllowedMagic(eleven)).isFalse();
        byte[] twelve = new byte[12];
        twelve[0] = (byte) 0xFF; twelve[1] = (byte) 0xD8; twelve[2] = (byte) 0xFF;
        assertThat(ImageSanitizer.hasAllowedMagic(twelve)).isTrue();
    }
}
