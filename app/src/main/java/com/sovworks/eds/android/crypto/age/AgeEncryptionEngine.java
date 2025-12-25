package com.sovworks.eds.android.crypto.age;

import com.sovworks.eds.android.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

/*
import com.str4d.age.ArmoredOutputStream;
import com.str4d.age.Recipient;
import com.str4d.age.X25519Recipient;
import com.str4d.age.ScryptRecipient;
*/

/**
 * Engine for age encryption/decryption.
 */
public class AgeEncryptionEngine {

    public static void encryptWithPassphrase(InputStream in, OutputStream out, char[] passphrase) throws IOException, GeneralSecurityException {
        /*
        ScryptRecipient recipient = new ScryptRecipient(passphrase);
        List<Recipient> recipients = new ArrayList<>();
        recipients.add(recipient);
        
        com.str4d.age.Encryptor encryptor = new com.str4d.age.Encryptor(recipients);
        try (OutputStream ageOut = encryptor.encrypt(out)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                ageOut.write(buffer, 0, read);
            }
        }
        */
        throw new UnsupportedOperationException("age-java library not found during migration");
    }

    public static void encryptWithPublicKeys(InputStream in, OutputStream out, List<String> b64PublicKeys) throws IOException, GeneralSecurityException {
        /*
        List<Recipient> recipients = new ArrayList<>();
        for (String b64Key : b64PublicKeys) {
            recipients.add(X25519Recipient.parse(b64Key));
        }
        
        com.str4d.age.Encryptor encryptor = new com.str4d.age.Encryptor(recipients);
        try (OutputStream ageOut = encryptor.encrypt(out)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                ageOut.write(buffer, 0, read);
            }
        }
        */
        throw new UnsupportedOperationException("age-java library not found during migration");
    }
}
