package com.ecommerce.user.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Loads the RS256 private key (PKCS#8 PEM) used to SIGN access tokens.
 * Only user-service holds the private key; everyone else verifies with the public key.
 * In production this comes from Vault / a mounted secret, not a baked-in resource.
 */
@Configuration
public class JwtKeyConfig {

    @Bean
    public RSAPrivateKey jwtPrivateKey() throws Exception {
        String pem = new String(
            new ClassPathResource("keys/private_key.pem").getInputStream().readAllBytes(),
            StandardCharsets.UTF_8);
        String base64 = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
            .generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
