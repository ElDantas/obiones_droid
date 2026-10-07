package io.agentic.integrations.github;

import java.io.ByteArrayOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public final class Pem {
    private static final byte[] PKCS8_RSA_HEADER = {
            0x02, 0x01, 0x00, 0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
    };

    private Pem() {
    }

    public static PrivateKey readPrivateKey(String pem) {
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        String base64 = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        byte[] pkcs8 = pkcs1 ? wrapPkcs1(der) : der;
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid RSA private key", e);
        }
    }

    private static byte[] wrapPkcs1(byte[] pkcs1) {
        ByteArrayOutputStream octet = new ByteArrayOutputStream();
        octet.write(0x04);
        writeLength(octet, pkcs1.length);
        octet.writeBytes(pkcs1);
        byte[] octetBytes = octet.toByteArray();

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(PKCS8_RSA_HEADER);
        body.writeBytes(octetBytes);
        byte[] bodyBytes = body.toByteArray();

        ByteArrayOutputStream seq = new ByteArrayOutputStream();
        seq.write(0x30);
        writeLength(seq, bodyBytes.length);
        seq.writeBytes(bodyBytes);
        return seq.toByteArray();
    }

    private static void writeLength(ByteArrayOutputStream out, int length) {
        if (length < 0x80) {
            out.write(length);
        } else if (length < 0x100) {
            out.write(0x81);
            out.write(length);
        } else if (length < 0x10000) {
            out.write(0x82);
            out.write(length >> 8);
            out.write(length & 0xff);
        } else {
            out.write(0x83);
            out.write(length >> 16);
            out.write((length >> 8) & 0xff);
            out.write(length & 0xff);
        }
    }
}
