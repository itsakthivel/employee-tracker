package com.example.tracker.sensitive.crypto;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Data-encryption keys live outside the database: the active key id comes from
 * {@code tracker.data-key.active-id}, and each key from {@code TRACKER_DATA_KEY_<id>} (base64 of
 * 32 bytes). Old keys stay configured after rotation so older values can still be read.
 */
@Component
public class DataKeyProvider {

  private final Environment env;
  private final String activeKeyId;

  public DataKeyProvider(Environment env, @Value("${tracker.data-key.active-id:}") String activeKeyId) {
    this.env = env;
    this.activeKeyId = activeKeyId;
    if (activeKeyId.isBlank()) {
      throw new IllegalStateException("TRACKER_DATA_KEY_ID is not set");
    }
    if (activeKeyId.length() > 40 || !activeKeyId.matches("[A-Za-z0-9_]+")) {
      throw new IllegalStateException("TRACKER_DATA_KEY_ID must be letters, digits or _ (max 40)");
    }
    key(activeKeyId); // fail startup if the active key is missing or malformed
  }

  public String activeKeyId() {
    return activeKeyId;
  }

  public SecretKey key(String id) {
    String encoded = env.getProperty("TRACKER_DATA_KEY_" + id);
    if (encoded == null || encoded.isBlank()) {
      throw new MissingKeyException("Data key " + id + " is not configured");
    }
    byte[] raw;
    try {
      raw = Base64.getDecoder().decode(encoded.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Data key " + id + " is not valid base64");
    }
    if (raw.length != 32) {
      throw new IllegalStateException("Data key " + id + " must be 32 bytes");
    }
    return new SecretKeySpec(raw, "AES");
  }

  public static class MissingKeyException extends RuntimeException {
    public MissingKeyException(String message) {
      super(message);
    }
  }
}
