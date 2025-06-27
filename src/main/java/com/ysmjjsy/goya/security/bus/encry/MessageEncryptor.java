package com.ysmjjsy.goya.security.bus.encry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 消息加密器
 * 
 * 支持多种加密算法，用于保护消息传输安全
 *
 * @author goya
 * @since 2025/6/24
 */
@Slf4j
@Component
public class MessageEncryptor {

    /**
     * 加密算法类型
     */
    public enum EncryptionType {
        AES_128("AES", 128),
        AES_256("AES", 256),
        DES("DES", 56);

        private final String algorithm;
        private final int keySize;

        EncryptionType(String algorithm, int keySize) {
            this.algorithm = algorithm;
            this.keySize = keySize;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public int getKeySize() {
            return keySize;
        }
    }

    /**
     * 默认密钥（实际生产环境应该从配置中心获取）
     */
    private static final String DEFAULT_AES_KEY = "MySecretKey12345"; // 16字节的AES密钥

    /**
     * 加密消息
     *
     * @param data           原始数据
     * @param encryptionType 加密类型
     * @return 加密结果
     */
    public EncryptionResult encrypt(byte[] data, EncryptionType encryptionType) {
        return encrypt(data, encryptionType, null);
    }

    /**
     * 加密消息
     *
     * @param data           原始数据
     * @param encryptionType 加密类型
     * @param secretKey      密钥（如果为null则使用默认密钥）
     * @return 加密结果
     */
    public EncryptionResult encrypt(byte[] data, EncryptionType encryptionType, String secretKey) {
        if (data == null || data.length == 0) {
            return EncryptionResult.noEncryption(data);
        }

        try {
            switch (encryptionType) {
                case AES_128:
                case AES_256:
                    return encryptWithAES(data, secretKey != null ? secretKey : DEFAULT_AES_KEY);
                case DES:
                    // 可以后续添加DES实现
                    return encryptWithAES(data, secretKey != null ? secretKey : DEFAULT_AES_KEY);
                default:
                    throw new IllegalArgumentException("Unsupported encryption type: " + encryptionType);
            }
        } catch (Exception e) {
            log.error("Failed to encrypt message using {}", encryptionType, e);
            return EncryptionResult.noEncryption(data);
        }
    }

    /**
     * 解密消息
     *
     * @param encryptedData  加密数据
     * @param encryptionType 加密类型
     * @return 解密后的数据
     */
    public byte[] decrypt(byte[] encryptedData, EncryptionType encryptionType) {
        return decrypt(encryptedData, encryptionType, null);
    }

    /**
     * 解密消息
     *
     * @param encryptedData  加密数据
     * @param encryptionType 加密类型
     * @param secretKey      密钥（如果为null则使用默认密钥）
     * @return 解密后的数据
     */
    public byte[] decrypt(byte[] encryptedData, EncryptionType encryptionType, String secretKey) {
        if (encryptedData == null || encryptedData.length == 0) {
            return encryptedData;
        }

        try {
            switch (encryptionType) {
                case AES_128:
                case AES_256:
                    return decryptWithAES(encryptedData, secretKey != null ? secretKey : DEFAULT_AES_KEY);
                case DES:
                    return decryptWithAES(encryptedData, secretKey != null ? secretKey : DEFAULT_AES_KEY);
                default:
                    throw new IllegalArgumentException("Unsupported encryption type: " + encryptionType);
            }
        } catch (Exception e) {
            log.error("Failed to decrypt message using {}", encryptionType, e);
            return encryptedData; // 返回原始数据
        }
    }

    /**
     * 使用AES加密
     */
    private EncryptionResult encryptWithAES(byte[] data, String secretKey) throws Exception {
        // 确保密钥长度为16字节（AES-128）
        byte[] keyBytes = adjustKeyLength(secretKey.getBytes(StandardCharsets.UTF_8), 16);
        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");

        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        
        byte[] encryptedData = cipher.doFinal(data);
        
        log.debug("AES encryption: {} bytes -> {} bytes", data.length, encryptedData.length);
        
        return new EncryptionResult(
            encryptedData,
            data.length,
            encryptedData.length,
            EncryptionType.AES_128,
            true
        );
    }

    /**
     * 使用AES解密
     */
    private byte[] decryptWithAES(byte[] encryptedData, String secretKey) throws Exception {
        // 确保密钥长度为16字节（AES-128）
        byte[] keyBytes = adjustKeyLength(secretKey.getBytes(StandardCharsets.UTF_8), 16);
        SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");

        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
        
        byte[] decryptedData = cipher.doFinal(encryptedData);
        
        log.debug("AES decryption: {} bytes -> {} bytes", encryptedData.length, decryptedData.length);
        
        return decryptedData;
    }

    /**
     * 调整密钥长度
     */
    private byte[] adjustKeyLength(byte[] key, int targetLength) {
        byte[] adjustedKey = new byte[targetLength];
        if (key.length >= targetLength) {
            System.arraycopy(key, 0, adjustedKey, 0, targetLength);
        } else {
            System.arraycopy(key, 0, adjustedKey, 0, key.length);
            // 用0填充剩余部分
            for (int i = key.length; i < targetLength; i++) {
                adjustedKey[i] = 0;
            }
        }
        return adjustedKey;
    }

    /**
     * 生成随机密钥
     */
    public String generateRandomKey(EncryptionType encryptionType) {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(encryptionType.getAlgorithm());
            keyGenerator.init(encryptionType.getKeySize(), new SecureRandom());
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            log.error("Failed to generate random key for {}", encryptionType, e);
            return null;
        }
    }

    /**
     * 加密结果
     */
    public static class EncryptionResult {
        private final byte[] data;
        private final int originalSize;
        private final int encryptedSize;
        private final EncryptionType encryptionType;
        private final boolean encrypted;

        public EncryptionResult(byte[] data, int originalSize, int encryptedSize, 
                               EncryptionType encryptionType, boolean encrypted) {
            this.data = data;
            this.originalSize = originalSize;
            this.encryptedSize = encryptedSize;
            this.encryptionType = encryptionType;
            this.encrypted = encrypted;
        }

        public static EncryptionResult noEncryption(byte[] data) {
            return new EncryptionResult(data, data.length, data.length, null, false);
        }

        // Getters
        public byte[] getData() { return data; }
        public int getOriginalSize() { return originalSize; }
        public int getEncryptedSize() { return encryptedSize; }
        public EncryptionType getEncryptionType() { return encryptionType; }
        public boolean isEncrypted() { return encrypted; }
    }
} 