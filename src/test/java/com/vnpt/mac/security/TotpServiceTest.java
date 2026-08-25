package com.vnpt.mac.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TotpServiceTest {
    @Test void generatedSecretHasExpectedBase32Shape() {
        var secret=new TotpService().generateSecret();
        assertThat(secret).matches("[A-Z2-7]{32}");
    }
    @Test void invalidCodeIsRejected() {
        assertThat(new TotpService().verify("JBSWY3DPEHPK3PXP","abc")).isFalse();
    }
}
