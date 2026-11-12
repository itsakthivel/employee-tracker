package com.example.tracker.sensitive.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM field encryption. Stored format: [format version][key-id length][key id][12-byte
 * IV][ciphertext + 16-byte tag]. The key id travels with the value, so keys can be rotated.
 *
 * <p>Encrypted columns are plain {@code byte[]} on the entities and are only decrypted here, on
 * purpose: loading a record never decrypts anything, so masked views keep working without the key.
 */
@Component
public class FieldCipher {

  private static final byte FORMAT = 1;
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final DataKeyProvider keys;
  private final SecureRandom random = new SecureRandom();

  public FieldCipher(DataKeyProvider keys) {
    this.keys = keys;
  }

  public byte[] encrypt(String plain) {
    if (plain == null) {
      return null;
    }
    try {
      String keyId = keys.activeKeyId();
      byte[] id = keyId.getBytes(StandardCharsets.US_ASCII);
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, keys.key(keyId), new GCMParameterSpec(TAG_BITS, iv));
      byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      return ByteBuffer.allocate(2 + id.length + IV_BYTES + sealed.length)
          .put(FORMAT)
          .put((byte) id.length)
          .put(id)
          .put(iv)
          .put(sealed)
          .array();
    } catch (GeneralSecurityException e) {
      throw new DecryptionException("Encryption failed", e);
    }
  }

  public String decrypt(byte[] stored) {
    if (stored == null) {
      return null;
    }
    try {
      ByteBuffer buf = ByteBuffer.wrap(stored);
      if (buf.get() != FORMAT) {
        throw new DecryptionException("Unknown format", null);
      }
      byte[] id = new byte[buf.get()];
      buf.get(id);
      byte[] iv = new byte[IV_BYTES];
      buf.get(iv);
      byte[] sealed = new byte[buf.remaining()];
      buf.get(sealed);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          keys.key(new String(id, StandardCharsets.US_ASCII)),
          new GCMParameterSpec(TAG_BITS, iv));
      return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | RuntimeException e) {
      throw new DecryptionException("Stored value could not be decrypted", e);
    }
  }

  /** Never carries the value in its message. */
  public static class DecryptionException extends RuntimeException {
    public DecryptionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
