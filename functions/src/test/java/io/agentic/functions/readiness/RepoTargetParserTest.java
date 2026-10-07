package io.agentic.functions.readiness;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RepoTargetParserTest {
    private final RepoTargetParser parser = new RepoTargetParser();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Acme/Payments|acme/payments",
            " https://github.com/Acme/Payments.git/ |acme/payments",
            "git@github.com:acme/payments.git|acme/payments",
            "acme/payments-api.v2|acme/payments-api.v2"
    })
    void normalises(String raw, String expected) {
        assertThat(parser.parse(raw).repo()).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"payments", "acme/payments/tree/main", "acme / payments"})
    void rejectsNonOwnerRepoForms(String raw) {
        RepoTargetParser.Result r = parser.parse(raw);
        assertThat(r.repo()).isEmpty();
        assertThat(r.error()).hasValueSatisfying(e -> assertThat(e).contains("is not in owner/repo form"));
    }

    @Test
    void emptyFieldHasSpecificMessage() {
        assertThat(parser.parse("").error()).hasValueSatisfying(e -> assertThat(e).contains("field is empty"));
        assertThat(parser.parse(null).error()).isPresent();
    }
}
