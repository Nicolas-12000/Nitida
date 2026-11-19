package co.nitida.shared;

import static org.assertj.core.api.Assertions.assertThat;

import co.nitida.shared.crypto.Ed25519;
import java.security.KeyPair;
import java.util.Arrays;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

/**
 * D2 (spec §15.5): the signatures we store must be verifiable by anyone, not only by our own code. The JDK provider and
 * BouncyCastle are independent implementations of RFC 8032; they must agree in both directions on random inputs.
 */
class Ed25519DifferentialTest {
    private static final int RAW = 32; // both DER encodings end with the raw 32-byte key

    private static byte[] tail(byte[] der) { return Arrays.copyOfRange(der, der.length - RAW, der.length); }

    @Property(tries = 200)
    void aSignatureFromTheJdkIsAcceptedByBouncyCastle(@ForAll @Size(max = 600) byte[] message) {
        KeyPair pair = Ed25519.generate();
        byte[] signature = Ed25519.sign(pair.getPrivate().getEncoded(), message);

        var verifier = new Ed25519Signer();
        verifier.init(false, new Ed25519PublicKeyParameters(tail(pair.getPublic().getEncoded()), 0));
        verifier.update(message, 0, message.length);
        assertThat(verifier.verifySignature(signature)).isTrue();
    }

    @Property(tries = 200)
    void aSignatureFromBouncyCastleIsAcceptedByTheJdk(@ForAll @Size(max = 600) byte[] message) {
        KeyPair pair = Ed25519.generate();
        var signer = new Ed25519Signer();
        signer.init(true, new Ed25519PrivateKeyParameters(tail(pair.getPrivate().getEncoded()), 0));
        signer.update(message, 0, message.length);
        byte[] signature = signer.generateSignature();

        assertThat(Ed25519.verify(pair.getPublic().getEncoded(), message, signature)).isTrue();
    }

    @Property(tries = 100)
    void bothRejectATamperedMessage(@ForAll @Size(min = 1, max = 300) byte[] message) {
        KeyPair pair = Ed25519.generate();
        byte[] signature = Ed25519.sign(pair.getPrivate().getEncoded(), message);
        byte[] tampered = message.clone();
        tampered[tampered.length / 2] ^= 0x01;

        var verifier = new Ed25519Signer();
        verifier.init(false, new Ed25519PublicKeyParameters(tail(pair.getPublic().getEncoded()), 0));
        verifier.update(tampered, 0, tampered.length);
        assertThat(verifier.verifySignature(signature)).as("BouncyCastle").isFalse();
        assertThat(Ed25519.verify(pair.getPublic().getEncoded(), tampered, signature)).as("JDK").isFalse();
    }
}
