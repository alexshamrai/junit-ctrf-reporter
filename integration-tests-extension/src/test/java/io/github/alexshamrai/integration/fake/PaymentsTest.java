package io.github.alexshamrai.integration.fake;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Passing tests in a {@code @Nested} class of a class with a {@code @DisplayName}, for the report's {@code suite}.
 */
@DisplayName("Payments")
public class PaymentsTest extends BaseFakeTest {

    @Nested
    class Refunds {

        @Test
        void refundsTheFee() {
            System.out.println("PaymentsTest refundsTheFee()");
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        void refundsEachAmount(int amount) {
            System.out.println("PaymentsTest refundsEachAmount(" + amount + ")");
        }
    }
}
