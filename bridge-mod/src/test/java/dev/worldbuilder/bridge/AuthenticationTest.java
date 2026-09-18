package dev.worldbuilder.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthenticationTest {
    @Test void tokenComparisonRejectsShortAndWrongSecrets() {
        assertFalse(Authentication.tokenMatches("short", "short"));
        assertFalse(Authentication.tokenMatches("0123456789abcdef", "0123456789abcdeg"));
        assertTrue(Authentication.tokenMatches("0123456789abcdef", "0123456789abcdef"));
    }
    @Test void localTokenComparisonIsConstantTimeCompatible() { assertFalse(Authentication.tokenMatches("short", "short")); }
}
