package co.nitida.history;

import static org.assertj.core.api.Assertions.assertThat;

import co.nitida.history.domain.ChainSealer;
import co.nitida.history.domain.JsonCanonicalizer;
import co.nitida.shared.Json;
import co.nitida.shared.UuidV7;
import co.nitida.shared.crypto.Ed25519;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** D1: Java exports chains; the Python verifier (rfc8785 + cryptography) must agree. */
class IndependentVerifierTest {
    @Test
    void pythonAgreesOnAValidChainAndOnTheSeqOfTheBrokenLink() throws Exception {
        var pair = Ed25519.generate();
        String publicKey = b64(pair.getPublic().getEncoded());
        byte[] privateKey = pair.getPrivate().getEncoded();
        var valid = chain(privateKey, publicKey, "valid", false);
        var broken = chain(privateKey, publicKey, "broken", true);

        Path out = Path.of("build/d1/chains.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, Json.write(Map.of("chains", List.of(valid, broken))));

        var process = new ProcessBuilder(python(), "scripts/verify_chains.py", out.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(output).isZero();
    }

    private static Map<String, Object> chain(byte[] privateKey, String publicKey, String name, boolean tamperSecond) {
        var entries = new ArrayList<Map<String, Object>>();
        byte[] previous = ChainSealer.GENESIS_HASH;
        var motorcycle = UuidV7.next();
        for (int seq = 1; seq <= 2; seq++) {
            var document = new LinkedHashMap<String, Object>();
            document.put("motorcycleId", motorcycle.toString());
            document.put("seq", seq);
            document.put("type", seq == 1 ? "INITIAL_REGISTRATION" : "SERVICE");
            document.put("note", "revisión " + seq);
            String canonical = JsonCanonicalizer.canonicalize(document);
            byte[] hash = ChainSealer.hash(previous, canonical);
            byte[] signature = Ed25519.sign(privateKey, hash);
            String published = canonical;
            if (tamperSecond && seq == 2) published = canonical.replace("revisión", "revixión");
            var entry = new LinkedHashMap<String, Object>();
            entry.put("seq", seq);
            entry.put("canonical", published);
            entry.put("document", document);
            entry.put("previousHash", b64(previous));
            entry.put("hash", b64(hash));
            entry.put("signature", b64(signature));
            entries.add(entry);
            previous = hash;
        }
        var chain = new LinkedHashMap<String, Object>();
        chain.put("name", name);
        chain.put("publicKey", publicKey);
        chain.put("expectedStatus", tamperSecond ? "INVALID" : "VALID");
        chain.put("expectedSeq", tamperSecond ? 2 : null);
        chain.put("entries", entries);
        return chain;
    }

    /** CI and a local venv set this; otherwise the python3 on PATH (GitHub's image, after pip). */
    private static String python() {
        String configured = System.getenv("NITIDA_D1_PYTHON");
        if (configured != null && !configured.isBlank()) return configured;
        if (java.nio.file.Files.isExecutable(Path.of(".venv-d1/bin/python"))) return ".venv-d1/bin/python";
        return "python3";
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
