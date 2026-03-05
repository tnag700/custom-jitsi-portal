package com.acme.jitsi.domains.configsets.infrastructure;

import com.acme.jitsi.domains.configsets.service.ConfigSetInvalidDataException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ConfigSetEncryptionService {

  private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int GCM_TAG_BITS = 128;
  private static final int IV_SIZE_BYTES = 12;

  private final String rawEncryptionKey;
  private final SecureRandom secureRandom;

  ConfigSetEncryptionService(
      @Value("${APP_CONFIG_SETS_ENCRYPTION_KEY:}") String encryptionKey) {
    this.rawEncryptionKey = encryptionKey;
    this.secureRandom = new SecureRandom();
  }

  String encrypt(String plainText) {
    if (plainText == null) {
      return null;
    }
    try {
      byte[] iv = new byte[IV_SIZE_BYTES];
      secureRandom.nextBytes(iv);
      byte[] encryptionKey = parseAndValidateKey(rawEncryptionKey);

      Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

      ByteBuffer payload = ByteBuffer.allocate(iv.length + cipherText.length);
      payload.put(iv);
      payload.put(cipherText);
      return Base64.getEncoder().encodeToString(payload.array());
    } catch (GeneralSecurityException ex) {
      throw new ConfigSetInvalidDataException("Failed to encrypt signingSecret", ex);
    }
  }

  String decrypt(String encryptedText) {
    if (encryptedText == null) {
      return null;
    }
    try {
      byte[] payload = Base64.getDecoder().decode(encryptedText);
      if (payload.length <= IV_SIZE_BYTES) {
        throw new ConfigSetInvalidDataException("Encrypted signingSecret payload is invalid");
      }
      byte[] encryptionKey = parseAndValidateKey(rawEncryptionKey);

      byte[] iv = new byte[IV_SIZE_BYTES];
      byte[] cipherText = new byte[payload.length - IV_SIZE_BYTES];
      System.arraycopy(payload, 0, iv, 0, IV_SIZE_BYTES);
      System.arraycopy(payload, IV_SIZE_BYTES, cipherText, 0, cipherText.length);

      Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] plain = cipher.doFinal(cipherText);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException | GeneralSecurityException ex) {
      throw new ConfigSetInvalidDataException("Failed to decrypt signingSecret", ex);
    }
  }

  private byte[] parseAndValidateKey(String rawKey) {
    if (rawKey == null || rawKey.isBlank()) {
      throw new ConfigSetInvalidDataException("APP_CONFIG_SETS_ENCRYPTION_KEY is required");
    }

    byte[] keyBytes;
    try {
      keyBytes = Base64.getDecoder().decode(rawKey.trim());
    } catch (IllegalArgumentException ignored) {
      keyBytes = rawKey.trim().getBytes(StandardCharsets.UTF_8);
    }

    if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
      throw new ConfigSetInvalidDataException(
          "APP_CONFIG_SETS_ENCRYPTION_KEY must be 16, 24, or 32 bytes (or base64 of these lengths)");
    }
    return keyBytes;
  }
}