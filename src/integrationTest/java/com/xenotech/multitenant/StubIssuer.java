package com.xenotech.multitenant;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * In-process OIDC provider: serves discovery documents and a JWKS for any realm under {@code /realms/{realm}}
 * and signs tokens with the matching private key. Stands in for Keycloak so the integration tests need no Docker.
 */
final class StubIssuer {

    private static final StubIssuer INSTANCE = new StubIssuer();

    private final RSAKey signingKey;
    private final HttpServer server;

    private StubIssuer() {
        try {
            signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (JOSEException | IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/realms/", exchange -> {
            var path = exchange.getRequestURI().getPath();
            var realmPath = path.substring(0, path.indexOf("/", "/realms/".length()));
            String body;
            if (path.endsWith("/.well-known/openid-configuration")) {
                var issuer = baseUrl() + realmPath;
                body = """
                        {"issuer":"%s","jwks_uri":"%s/protocol/openid-connect/certs",\
                        "subject_types_supported":["public"],"id_token_signing_alg_values_supported":["RS256"]}"""
                        .formatted(issuer, issuer);
            } else if (path.endsWith("/protocol/openid-connect/certs")) {
                body = new JWKSet(signingKey.toPublicJWK()).toString();
            } else {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    static StubIssuer get() {
        return INSTANCE;
    }

    String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    String issuerOf(String realm) {
        return baseUrl() + "/realms/" + realm;
    }

    /** A valid token for the realm, signed with the key the stub publishes. */
    String token(String realm, String subject) {
        return sign(signingKey, issuerOf(realm), subject, Instant.now().plusSeconds(300));
    }

    /** A token that expired a minute ago. */
    String expiredToken(String realm, String subject) {
        return sign(signingKey, issuerOf(realm), subject, Instant.now().minusSeconds(60));
    }

    /** A token claiming the realm's issuer but signed with a key the stub does not publish. */
    String forgedToken(String realm, String subject) {
        try {
            var otherKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
            return sign(otherKey, issuerOf(realm), subject, Instant.now().plusSeconds(300));
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sign(RSAKey key, String issuer, String subject, Instant expiry) {
        try {
            var claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(subject)
                    .issueTime(new Date())
                    .expirationTime(Date.from(expiry))
                    .build();
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
