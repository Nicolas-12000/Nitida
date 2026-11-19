package co.nitida.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.nitida.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.ResultActions;

class FilesIntegrationTest extends IntegrationTest {


    /** Functional endpoints read servlet Parts, so tests send Parts (not MockMultipartFile). */
    static MockPart part(String field, String filename, byte[] content) {
        return new MockPart(field, filename, content);
    }

    ResultActions upload(Session s, UUID ws, String name, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/v1/files").part(part("file", name, content))
                .with(csrf()).cookie(s.cookie()).header("X-Workshop-Id", ws.toString()));
    }


    @Test
    void uploadedPhotoIsReencodedStoredAndServedOnlyToItsWorkshop() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        byte[] dirty = ImageSanitizerTest.withExifGps(ImageSanitizerTest.image("jpg", 50, 40));
        var res = upload(owner, ws, "tablero.jpg", dirty).andExpect(status().isCreated()).andExpect(jsonPath("$.sha256").exists());
        UUID id = UUID.fromString(json(res).get("fileId").asString());

        var served = get("/api/v1/files/" + id, owner, ws).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(served.getContentType()).isEqualTo("image/jpeg");
        assertThat(served.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(new String(served.getContentAsByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("GPSLatitude");

        var other = registerAndLogin();
        UUID otherWs = createWorkshop(other);
        get("/api/v1/files/" + id, other, otherWs).andExpect(status().isNotFound());
    }

    @Test
    void fakePhotoIsRejectedAndMissingPartIsABadRequest() throws Exception {
        var owner = registerAndLogin();
        UUID ws = createWorkshop(owner);
        upload(owner, ws, "foto.jpg", "<?php echo 1; ?>".getBytes()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        mvc.perform(multipart("/api/v1/files").part(part("other", "x.jpg", new byte[] {1}))
                .with(csrf()).cookie(owner.cookie()).header("X-Workshop-Id", ws.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("FILE_REQUIRED"));
    }
}
