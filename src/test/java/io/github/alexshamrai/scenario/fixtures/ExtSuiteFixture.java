package io.github.alexshamrai.scenario.fixtures;

import io.github.alexshamrai.jupiter.CtrfExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link SuiteFixture} with the extension, without the dynamic tests, which the extension doesn't report.
 */
@Tag("scenario-fixture")
@ExtendWith(CtrfExtension.class)
@DisplayName("Payments")
public class ExtSuiteFixture {

    @Test
    void charges() {
    }

    @Nested
    class Refunds {

        @Test
        void refundsTheFee() {
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2})
        void refundsEachAmount(int amount) {
        }

        @RepeatedTest(2)
        void refundsTwice() {
        }
    }

    @Nested
    class Broken {

        @BeforeAll
        static void connect() {
            throw new IllegalStateException("setup of a nested class fails");
        }

        @Test
        void neverRuns() {
        }
    }
}
