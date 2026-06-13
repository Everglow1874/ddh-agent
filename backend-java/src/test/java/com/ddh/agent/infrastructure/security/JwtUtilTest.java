package com.ddh.agent.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class JwtUtilTest {

    @Autowired JwtUtil jwtUtil;

    @Test
    void generateAndValidate() {
        String token = jwtUtil.generateToken(42L);
        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(42L);
    }

    @Test
    void invalidToken_returnsFalse() {
        assertThat(jwtUtil.validateToken("not.a.token")).isFalse();
    }
}
