package com.sovworks.eds.crypto;

import com.sovworks.eds.crypto.kdf.HMAC;
import java.security.MessageDigest;
import java.nio.ByteBuffer;

public class TwoFactorAuth {
    private static final int TIME_STEP = 30;
    private static final int DIGITS = 6;

    public static boolean verifyCode(String secretBase32, String codeStr) {
        if (secretBase32 == null || secretBase32.isEmpty() || codeStr == null) return false;
        try {
            int code = Integer.parseInt(codeStr.trim());
            byte[] secret = decodeBase32(secretBase32);
            long timeIndex = System.currentTimeMillis() / 1000 / TIME_STEP;
            
            for (int i = -1; i <= 1; i++) {
                if (calculateCode(secret, timeIndex + i) == code) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private static int calculateCode(byte[] key, long timeIndex) throws Exception {
        byte[] data = new byte[8];
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (timeIndex & 0xff);
            timeIndex >>= 8;
        }
        
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        HMAC hmac = new HMAC(key, md, 64);
        byte[] hash = new byte[hmac.getDigestLength()];
        hmac.calcHMAC(data, 0, data.length, hash);
        
        int offset = hash[hash.length - 1] & 0xf;
        int binary = ((hash[offset] & 0x7f) << 24) |
                     ((hash[offset + 1] & 0xff) << 16) |
                     ((hash[offset + 2] & 0xff) << 8) |
                     (hash[offset + 3] & 0xff);
        
        return binary % (int)Math.pow(10, DIGITS);
    }

    private static byte[] decodeBase32(String base32) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        base32 = base32.toUpperCase().replaceAll("[^A-Z2-7]", "");
        int len = (base32.length() * 5) / 8;
        byte[] bytes = new byte[len];
        int buffer = 0;
        int bitsLeft = 0;
        int count = 0;
        for (char c : base32.toCharArray()) {
            int val = alphabet.indexOf(c);
            if (val == -1) continue;
            buffer <<= 5;
            buffer |= val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bytes[count++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        return bytes;
    }

    public static String generateSecret() {
        byte[] buffer = new byte[20];
        new java.security.SecureRandom().nextBytes(buffer);
        return encodeBase32(buffer);
    }

    private static String encodeBase32(byte[] data) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer <<= 8;
            buffer |= (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(alphabet.charAt((buffer >> (bitsLeft - 5)) & 0x1f));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(alphabet.charAt((buffer << (5 - bitsLeft)) & 0x1f));
        }
        return sb.toString();
    }
}
