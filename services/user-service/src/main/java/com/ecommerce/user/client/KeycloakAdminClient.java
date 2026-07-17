package com.ecommerce.user.client;

import com.ecommerce.common.error.ConflictException;
import com.ecommerce.common.error.ServiceUnavailableException;
import com.ecommerce.common.error.UnauthorizedException;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class KeycloakAdminClient {

    @Value("${keycloak.url}")
    private String keycloakUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin-username}")
    private String adminUsername;

    @Value("${keycloak.admin-password}")
    private String adminPassword;

    @Value("${keycloak.client-id}")
    private String clientId;

    private final RestClient restClient = RestClient.create();

    public String createUser(String email, String password, String firstName, String lastName) {
        String adminToken = getAdminToken();
        String userId;
        try {
            var body = new java.util.HashMap<String, Object>();
            body.put("email", email);
            body.put("username", email);
            body.put("enabled", true);
            body.put("emailVerified", true);
            body.put("firstName", firstName);
            body.put("lastName", lastName != null ? lastName : "");
            var response = restClient.post()
                .uri(keycloakUrl + "/admin/realms/" + realm + "/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
            String location = response.getHeaders().getLocation().toString();
            userId = location.substring(location.lastIndexOf('/') + 1);
        } catch (HttpClientErrorException.Conflict e) {
            throw new ConflictException("Email already registered");
        } catch (HttpClientErrorException e) {
            throw new ServiceUnavailableException("Keycloak user creation failed: " + e.getStatusCode(), e);
        }
        // Set password via reset-password (inline credentials in create body stay unverified in KC 26)
        try {
            restClient.put()
                .uri(keycloakUrl + "/admin/realms/" + realm + "/users/" + userId + "/reset-password")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "password", "value", password, "temporary", false))
                .retrieve()
                .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            throw new ServiceUnavailableException("Keycloak set-password failed: " + e.getStatusCode(), e);
        }
        return userId;
    }

    public TokenPair getTokens(String email, String password) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "password");
        form.add("client_id", clientId);
        form.add("username", email);
        form.add("password", password);
        try {
            return restClient.post()
                .uri(keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenPair.class);
        } catch (HttpClientErrorException e) {
            throw new UnauthorizedException("Invalid credentials");
        }
    }

    public TokenPair refreshTokens(String refreshToken) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", clientId);
        form.add("refresh_token", refreshToken);
        try {
            return restClient.post()
                .uri(keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenPair.class);
        } catch (HttpClientErrorException e) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
    }

    private String getAdminToken() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "password");
        form.add("client_id", "admin-cli");
        form.add("username", adminUsername);
        form.add("password", adminPassword);
        try {
            TokenPair pair = restClient.post()
                .uri(keycloakUrl + "/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenPair.class);
            return pair.getAccessToken();
        } catch (Exception e) {
            throw new ServiceUnavailableException("Keycloak admin authentication failed", e);
        }
    }

    @Data
    public static class TokenPair {
        @JsonProperty("access_token")
        private String accessToken;
        @JsonProperty("refresh_token")
        private String refreshToken;
        @JsonProperty("expires_in")
        private long expiresIn;
    }
}
