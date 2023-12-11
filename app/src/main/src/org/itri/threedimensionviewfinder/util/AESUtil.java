package org.itri.threedimensionviewfinder.util;

import android.util.Base64;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

//import mma.security.component.diagnostics.Debuk;


/**
 * AES Encrypt and Decrypt.
 *
 * @version 1.0, Mar 15, 2009
 */
public class AESUtil {
    private static final String TAG = "AESUtil";

    private static void log(String what, byte[] bytes) {
        Log.d(TAG, what + "[" + bytes.length + "] [" + bytesToHex(bytes) + "]");
    }

    private static void log(String what, String value) {
        Log.d(TAG, what + "[" + value.length() + "] [" + value + "]");
    }

    private static String bytesToHex(byte[] bytes) {
        final char[] hexArray = {'0', '1', '2', '3', '4', '5', '6', '7', '8',
                '9', 'A', 'B', 'C', 'D', 'E', 'F'};
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static SecretKeySpec generateKey(final String password) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = password.getBytes("UTF-8");
        digest.update(bytes, 0, bytes.length);
        byte[] key = digest.digest();

        log("SHA-256 key ", key);

        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        return secretKeySpec;
    }

    public static String encrypt(final String keyText, String message)
            throws GeneralSecurityException {

        try {
//            final SecretKeySpec key = generateKey(keyText);
            byte[] keyTextBytes = keyText.getBytes("UTF-8");
            byte[] keyBytes;
            if (keyTextBytes.length < 32) {
                keyBytes = new byte[32];
                System.arraycopy(keyTextBytes, 0, keyBytes, 0, keyTextBytes.length);
            } else {
                keyBytes = keyTextBytes;
            }
            final SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

            log("message", message);

//            final Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            final Cipher cipher = Cipher.getInstance("AES_256/CBC/PKCS7Padding"); // force to use AES 256
//            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(new byte[cipher.getBlockSize()])); // iv with all zero
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] cipherText = cipher.doFinal(message.getBytes("UTF-8"));
            byte[] iv = cipher.getIV();

            log("cipherText", cipherText);
            log("iv", iv);

            byte[] sep = "::".getBytes("UTF-8");
            byte[] combined = new byte[cipherText.length + sep.length + iv.length];
            System.arraycopy(cipherText, 0, combined, 0, cipherText.length);
            System.arraycopy(sep, 0, combined, cipherText.length, sep.length);
            System.arraycopy(iv, 0, combined, cipherText.length + sep.length, iv.length);

            //NO_WRAP is important as was getting \n at the end
            String encoded = Base64.encodeToString(combined, Base64.NO_WRAP);
            log("Base64.NO_WRAP", encoded);
            return encoded;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "UnsupportedEncodingException ", e);
            throw new GeneralSecurityException(e);
        }
    }
//    private static final String TAG = AESUtil.class.getSimpleName();
//    /**
//     * @return cipher
//     * @throws Exception
//     */
//    private static Cipher getCipher() throws Exception {
//        // 算法，模式，補碼方式
//        Cipher cipher = Cipher.getInstance("AES/ECB/ZeroBytePadding");
//        return cipher;
//    }
//
//    /**
//     * @param aesKey
//     * @return secret key spec
//     * @throws Exception
//     */
//    private static SecretKeySpec getSecretKeySpec(String aesKey) throws Exception {
//        SecretKeySpec spec = new SecretKeySpec(aesKey.getBytes("UTF8"), "AES");
//        return spec;
//    }
//
//
//    public static String selfKey(String key) {   // key.length() must be 16, 24 or 32
//        int length = key.length();
//        if( length < 16 ) {
//            for( int i=length ;i<16; ++i )
//                key += i%10;
//            return key;
//        } else if ( length < 24 ) {
//            for( int i=length ;i<24; ++i )
//                key += i%10;
//            return key;
//        } else if ( length < 32 ) {
//            for( int i=length ;i<32; ++i )
//                key += i%10;
//            return key;
//        }
//        return key.substring(0, 32);
//    }
//
//
//    public static String selfEncode(String key, String value) {
//        try {
//            //SecretKeySpec spec = new SecretKeySpec(selfKey(key).getBytes(), "AES");
//            SecretKeySpec spec = getSecretKeySpec(key);
//            Cipher cipher = getCipher();
//            cipher.init(Cipher.ENCRYPT_MODE, spec);
//            System.out.println("ynhuang, selfencode dofinal:" + cipher.doFinal(value.getBytes()));
//            System.out.println("ynhuang, tostring: " + Base64.encodeToString(cipher.doFinal(value.getBytes()), Base64.NO_WRAP));
//            return Base64.encodeToString(cipher.doFinal(value.getBytes()), Base64.NO_WRAP);
//        } catch (Exception e) {
////            Debuk.WriteLine(e.toString());
//        }
//        return null;
//    }
//
//
//    public static String selfDecode(String key, String value) {
//        try {
//            //SecretKeySpec spec = new SecretKeySpec(selfKey(key).getBytes(), "AES");
//            SecretKeySpec spec = getSecretKeySpec(key);
//            Cipher cipher = getCipher();
//            cipher.init(Cipher.DECRYPT_MODE, spec);
//            return new String( cipher.doFinal(Base64.decode(value, Base64.NO_WRAP)));
//        } catch (Exception e) {
////            Debuk.WriteLine(e.toString());
//        }
//        return null;
//    }
}
