package com.xenotech.multitenant.utils;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;

public class TokenUtils {
    public static JWTClaimsSet getJWTClaimSet(String token) {
        try {
            return JWTParser.parse(token).getJWTClaimsSet();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
