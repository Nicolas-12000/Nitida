package co.nitida.shared.crypto;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/** Ed25519 over the JDK provider (RFC 8032). Keys travel as X.509 (public) / PKCS#8 (private) DER. */
public final class Ed25519 {
    private Ed25519() {}

    public static KeyPair generate() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ed25519 unavailable", e);
        }
    }

    public static byte[] sign(byte[] privateKeyPkcs8, byte[] data) {
        try {
            PrivateKey key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(privateKeyPkcs8));
            var s = Signature.getInstance("Ed25519");
            s.initSign(key);
            s.update(data);
            return s.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("signing failed", e);
        }
    }

    /** Never throws: any malformed key or signature is simply "not valid" (fail closed). */
    public static boolean verify(byte[] publicKeyX509, byte[] data, byte[] signature) {
        try {
            PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(publicKeyX509));
            var s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update(data);
            return s.verify(signature);
        } catch (GeneralSecurityException | RuntimeException e) {
            return false;
        }
    }
}
