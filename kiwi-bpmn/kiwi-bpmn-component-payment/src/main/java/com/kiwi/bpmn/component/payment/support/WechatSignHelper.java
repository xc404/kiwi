package com.kiwi.bpmn.component.payment.support;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

public final class WechatSignHelper {

    private WechatSignHelper() {}

    public static String nonce() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String authorization(
            String mchId,
            String certSerial,
            String privateKeyPem,
            String method,
            String pathWithQuery,
            String body) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = nonce();
        String message = method + "\n" + pathWithQuery + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
        String signature = sign(privateKeyPem, message);
        return "WECHATPAY2-SHA256-RSA2048 "
                + "mchid=\"" + mchId + "\","
                + "nonce_str=\"" + nonce + "\","
                + "timestamp=\"" + timestamp + "\","
                + "serial_no=\"" + certSerial + "\","
                + "signature=\"" + signature + "\"";
    }

    public static String sign(String privateKeyPem, String message) {
        try {
            PrivateKey privateKey = loadPrivateKey(privateKeyPem);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("微信 APIv3 签名失败: " + e.getMessage(), e);
        }
    }

    private static PrivateKey loadPrivateKey(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }
}
