package com.example.back.livetrading.startup;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.back.livetrading.config.LiveTradingProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

class LiveTradingStartupSafetyCheckTest {

    @Test
    void allowsLocalDefaultsWhenRealSubmissionIsDisabled() {
        LiveTradingStartupSafetyCheck check = check(false, "change-me-key", "change-me-jwt", "change-me-python");

        assertThatCode(() -> check.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsBlankLocalDefaultsWhenRealSubmissionIsDisabled() {
        LiveTradingStartupSafetyCheck check = check(false, "", "", "");

        assertThatCode(() -> check.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsChangeMeSecretsWhenRealSubmissionIsEnabled() {
        LiveTradingStartupSafetyCheck check = check(true, "change-me-key", "release-jwt", "release-python");

        assertThatThrownBy(() -> check.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIVE_TRADING_CREDENTIAL_ENCRYPTION_KEY");
    }

    @Test
    void rejectsBlankSharedSecretsWhenRealSubmissionIsEnabled() {
        LiveTradingStartupSafetyCheck check = check(true, "release-live-key", "", "");

        assertThatThrownBy(() -> check.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SECURITY_JWT_SECRET")
                .hasMessageContaining("PYTHON_PARSER_INTERNAL_SECRET");
    }

    @Test
    void allowsRealSubmissionOnlyWithNonDefaultSecrets() {
        LiveTradingStartupSafetyCheck check = check(
                true,
                "release-live-key-32-characters-minimum",
                "release-jwt-secret",
                "release-python-secret"
        );

        assertThatCode(() -> check.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsNonPlaceholderSecretThatContainsChangeMeSubstring() {
        LiveTradingStartupSafetyCheck check = check(
                true,
                "release-change-method-secret",
                "release-jwt-secret",
                "release-python-secret"
        );

        assertThatCode(() -> check.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    private LiveTradingStartupSafetyCheck check(
            boolean realSubmissionEnabled,
            String credentialKey,
            String jwtSecret,
            String pythonSecret
    ) {
        LiveTradingProperties properties = new LiveTradingProperties(
                realSubmissionEnabled,
                credentialKey,
                new BigDecimal("100"),
                new BigDecimal("500"),
                new BigDecimal("1000"),
                new BigDecimal("250"),
                new BigDecimal("2"),
                60,
                3,
                10,
                new LiveTradingProperties.Binance(
                        "https://api.binance.com",
                        "https://testnet.binance.vision"
                )
        );
        LiveTradingStartupSafetyCheck check = new LiveTradingStartupSafetyCheck(properties);
        ReflectionTestUtils.setField(check, "jwtSecret", jwtSecret);
        ReflectionTestUtils.setField(check, "pythonInternalSecret", pythonSecret);
        return check;
    }
}
