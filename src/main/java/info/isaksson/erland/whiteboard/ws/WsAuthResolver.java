package info.isaksson.erland.whiteboard.ws;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.smallrye.jwt.auth.principal.JWTParser;

@ApplicationScoped
class WsAuthResolver {

    private final Instance<JWTParser> jwtParser;

    @Inject
    WsAuthResolver(Instance<JWTParser> jwtParser) {
        this.jwtParser = jwtParser;
    }

    String resolveInviteToken(Session session) {
        return firstQueryParam(session, "invite");
    }

    String resolveUserId(Session session) {
        Principal principal = session == null ? null : session.getUserPrincipal();
        if (principal instanceof JsonWebToken jwt) {
            String preferred = jwt.getClaim("preferred_username");
            if (preferred != null && !preferred.isBlank()) {
                return preferred;
            }
            return jwt.getSubject();
        }
        if (principal != null) {
            return principal.getName();
        }

        String bearer = bearerTokenFromHandshake(session);
        if (bearer == null || bearer.isBlank()) {
            bearer = firstQueryParam(session, "access_token");
        }
        if (bearer == null || bearer.isBlank()) {
            return null;
        }
        return userIdFromJwt(bearer);
    }

    private String bearerTokenFromHandshake(Session session) {
        try {
            Object raw = session.getUserProperties().get(WsHandshakeConfigurator.AUTHORIZATION_HEADER);
            if (raw instanceof String s && s.startsWith("Bearer ")) {
                return s.substring("Bearer ".length()).trim();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String userIdFromJwt(String token) {
        try {
            if (jwtParser == null || !jwtParser.isResolvable()) {
                return null;
            }
            JsonWebToken jwt = jwtParser.get().parse(token);
            String preferred = jwt.getClaim("preferred_username");
            if (preferred != null && !preferred.isBlank()) {
                return preferred;
            }
            return jwt.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    private String firstQueryParam(Session session, String key) {
        try {
            var map = session.getRequestParameterMap();
            if (map != null) {
                var values = map.get(key);
                if (values != null) {
                    for (String v : values) {
                        if (v != null && !v.isBlank()) {
                            return v;
                        }
                    }
                }
            }

            var uri = session.getRequestURI();
            if (uri == null) {
                return null;
            }
            return firstQueryParamFromQuery(uri.getRawQuery(), key);
        } catch (Exception e) {
            return null;
        }
    }

    private String firstQueryParamFromQuery(String rawQuery, String key) {
        if (rawQuery == null || rawQuery.isBlank() || key == null || key.isBlank()) {
            return null;
        }
        try {
            String[] parts = rawQuery.split("&");
            for (String part : parts) {
                if (part == null || part.isBlank()) {
                    continue;
                }
                int idx = part.indexOf('=');
                String k = idx >= 0 ? part.substring(0, idx) : part;
                String v = idx >= 0 ? part.substring(idx + 1) : "";
                k = URLDecoder.decode(k, StandardCharsets.UTF_8);
                if (!key.equals(k)) {
                    continue;
                }
                if (v == null || v.isBlank()) {
                    continue;
                }
                v = URLDecoder.decode(v, StandardCharsets.UTF_8);
                if (!v.isBlank()) {
                    return v;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
