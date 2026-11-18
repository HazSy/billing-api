package dev.hazsy.billing.auth;

import dev.hazsy.billing.common.ApiException;
import dev.hazsy.billing.config.JwtProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record TokenRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public AuthController(AuthenticationManager authenticationManager, JwtEncoder encoder, JwtProperties properties) {
        this.authenticationManager = authenticationManager;
        this.encoder = encoder;
        this.properties = properties;
    }

    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials",
                    "Usuario o contraseña incorrectos.");
        }

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.replace("ROLE_", ""))
                .toList();

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(properties.ttlMinutes() * 60))
                .subject(authentication.getName())
                .claim("roles", roles)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return new TokenResponse(token, "Bearer", properties.ttlMinutes() * 60);
    }
}
