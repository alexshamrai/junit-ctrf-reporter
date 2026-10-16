package io.github.alexshamrai.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestContextAdapterTest {

    @Test
    @DisplayName("The suite is the container names")
    void suiteIsTheContainerNames() {
        assertThat(adapter("refundsTheFee()", List.of("Payments", "Refunds"), "JUnit Jupiter").getSuite())
            .containsExactly("Payments", "Refunds");
    }

    @Test
    @DisplayName("Without containers, the suite is the engine's name")
    void suiteIsTheEngineNameWithoutContainers() {
        assertThat(adapter("PaymentTest", List.of(), "JUnit Jupiter").getSuite()).containsExactly("JUnit Jupiter");
    }

    @Test
    @DisplayName("Knowing neither containers nor the engine, there is no suite")
    void suiteIsEmptyWhenNothingIsKnown() {
        assertThat(adapter("test()", List.of(), null).getSuite()).isEmpty();
    }

    @Test
    @DisplayName("What a container holds is grouped under it: its container names, then its own name, never the engine")
    void suiteOfChildrenEndsWithTheContainersOwnName() {
        assertThat(adapter("Refunds", List.of("Payments"), "JUnit Jupiter").getSuiteOfChildren())
            .containsExactly("Payments", "Refunds");
        assertThat(adapter("SetupTest", List.of(), "JUnit Jupiter").getSuiteOfChildren()).containsExactly("SetupTest");
    }

    @Test
    @DisplayName("An adapter that doesn't know its containers has none, no engine and no suite")
    void defaultsAreEmpty() {
        var adapter = new Bare("test()");

        assertThat(adapter.getContainerNames()).isEmpty();
        assertThat(adapter.getEngineName()).isEmpty();
        assertThat(adapter.getSuite()).isEmpty();
    }

    private static TestContextAdapter adapter(String displayName, List<String> containers, String engine) {
        return new Bare(displayName) {
            @Override
            public List<String> getContainerNames() {
                return containers;
            }

            @Override
            public Optional<String> getEngineName() {
                return Optional.ofNullable(engine);
            }
        };
    }

    /**
     * An adapter with only the methods it must implement, so that the defaults apply.
     */
    private static class Bare implements TestContextAdapter {

        private final String displayName;

        Bare(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String getUniqueId() {
            return "id";
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public Set<String> getTags() {
            return Set.of();
        }

        @Override
        public Optional<String> getSourceLocation() {
            return Optional.empty();
        }
    }
}
