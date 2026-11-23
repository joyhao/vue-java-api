package com.joykit.modular.auth.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokens {
    private final byte[] key;
    private static final String ISSUER = "joykit-api";
    public record Claims(AuthService.Identity user, Instant expiresAt, String sessionId) {}

    public JwtTokens(@Value("${JWT_SECRET:${app.auth.jwt-secret}}") String secret) {
        key = Base64.getDecoder().decode(secret);
        if (key.length < 32) throw new IllegalArgumentException("JWT密钥须为至少32字节随机值的Base64编码");
    }

    public String issue(AuthService.Identity user, String type, Instant expires) {
        return issue(user, type, expires, UUID.randomUUID().toString());
    }

    public String issue(AuthService.Identity user, String type, Instant expires, String sessionId) {
        var claims = new JWTClaimsSet.Builder().issuer(ISSUER).audience("joykit")
                .subject(Long.toString(user.id())).issueTime(new Date()).expirationTime(Date.from(expires))
                .jwtID(UUID.randomUUID().toString()).claim("token_type", type).claim("sid", sessionId)
                .claim("account", user.account()).claim("name", user.name()).claim("client_type", user.clientType()).build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(key));
            return jwt.serialize();
        } catch (JOSEException exception) { throw new IllegalStateException("JWT签名失败", exception); }
    }

    public Claims verify(String token, String type) {
        if (token == null || token.length() > 4096) return null;
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(key))) return null;
            var claims = jwt.getJWTClaimsSet();
            Instant now = Instant.now();
            if (!ISSUER.equals(claims.getIssuer()) || !claims.getAudience().contains("joykit")
                    || !type.equals(claims.getStringClaim("token_type")) || claims.getExpirationTime() == null
                    || !claims.getExpirationTime().toInstant().isAfter(now) || claims.getIssueTime() == null
                    || claims.getIssueTime().toInstant().isAfter(now.plusSeconds(30))) return null;
            String client = claims.getStringClaim("client_type");
            AuthService.ClientType.valueOf(client);
            var user = new AuthService.Identity(Long.parseLong(claims.getSubject()), claims.getStringClaim("account"), claims.getStringClaim("name"), client);
            String sid = claims.getStringClaim("sid");
            if (sid == null || sid.isBlank()) return null;
            return new Claims(user, claims.getExpirationTime().toInstant(), sid);
        } catch (java.text.ParseException | JOSEException | IllegalArgumentException | NullPointerException exception) { return null; }
    }
}
