package app.gym.qr;

import app.gym.config.AppProperties;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption of the QR token copy (decision A: lets "My Gym" redisplay the pass). The key comes from
 * APP_QR_ENC_KEY (base64, 32 bytes). Production refuses to start without it; dev/test fall back to an ephemeral key.
 */
@Component
public class QrCrypto {

    private static final Logger log = LoggerFactory.getLogger(QrCrypto.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    public QrCrypto(AppProperties props, Environment env) {
        String configured = props.qr().encryptionKey();
        if (configured == null || configured.isBlank()) {
            if (env.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("APP_QR_ENC_KEY is required in the prod profile "
                        + "(generate one with: openssl rand -base64 32).");
            }
            byte[] ephemeral = new byte[32];
            RANDOM.nextBytes(ephemeral);
            this.key = new SecretKeySpec(ephemeral, "AES");
            log.warn("APP_QR_ENC_KEY not set: using an ephemeral key. QR passes issued before a restart cannot be redisplayed.");
        } else {
            byte[] raw = Base64.getDecoder().decode(configured.trim());
            if (raw.length != 32) {
                throw new IllegalStateException("APP_QR_ENC_KEY must be base64 of exactly 32 bytes.");
            }
            this.key = new SecretKeySpec(raw, "AES");
        }
    }

    /** Returns base64(iv || ciphertext+tag). */
    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("QR encryption failed", e);
        }
    }

    /** Returns null if the value cannot be decrypted (e.g. key rotated); callers then reissue the pass. */
    public String decrypt(String encoded) {
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(all, 0, 12);
            byte[] ct = Arrays.copyOfRange(all, 12, all.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ct), java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return null;
        }
    }
}
