package io.github.alexshamrai.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.PreconditionViolationException;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestIdentifierAdapterTest {

    @Test
    @DisplayName("Should extract unique ID from TestIdentifier")
    void shouldExtractUniqueId() {
        var identifier = mock(TestIdentifier.class);
        when(identifier.getUniqueId()).thenReturn("[test:unique-id]");

        var adapter = new TestIdentifierAdapter(identifier);

        assertThat(adapter.getUniqueId()).isEqualTo("[test:unique-id]");
    }

    @Test
    @DisplayName("Should extract display name from TestIdentifier")
    void shouldExtractDisplayName() {
        var identifier = mock(TestIdentifier.class);
        when(identifier.getDisplayName()).thenReturn("Test Method Name");

        var adapter = new TestIdentifierAdapter(identifier);

        assertThat(adapter.getDisplayName()).isEqualTo("Test Method Name");
    }

    @Test
    @DisplayName("Should convert TestTag objects to strings")
    void shouldConvertTagsToStrings() {
        var identifier = mock(TestIdentifier.class);
        var tag1 = TestTag.create("unit");
        var tag2 = TestTag.create("integration");
        when(identifier.getTags()).thenReturn(Set.of(tag1, tag2));

        var adapter = new TestIdentifierAdapter(identifier);

        assertThat(adapter.getTags()).containsExactlyInAnyOrder("unit", "integration");
    }

    @Test
    @DisplayName("Should extract class name from MethodSource")
    void shouldExtractClassNameFromMethodSource() {
        var identifier = mock(TestIdentifier.class);
        var methodSource = MethodSource.from("com.example.TestClass", "testMethod");
        when(identifier.getSource()).thenReturn(Optional.of(methodSource));

        var adapter = new TestIdentifierAdapter(identifier);

        assertThat(adapter.getSourceLocation()).contains("com.example.TestClass");
    }

    @Test
    @DisplayName("Should extract class name from ClassSource")
    void shouldExtractClassNameFromClassSource() {
        var identifier = mock(TestIdentifier.class);
        var classSource = ClassSource.from("com.example.TestClass");
        when(identifier.getSource()).thenReturn(Optional.of(classSource));

        var adapter = new TestIdentifierAdapter(identifier);

        assertThat(adapter.getSourceLocation()).contains("com.example.TestClass");
    }

    @Test
    @DisplayName("Should use toString for unknown TestSource types")
    void shouldUseToStringForUnknownSourceTypes() {
        var identifier = mock(TestIdentifier.class);
        var unknownSource = new TestSource() {
            @Override
            public String toString() {
                return "CustomSource[path=/custom/path]";
            }
        };
        when(identifier.getSource()).thenReturn(Optional.of(unknownSource));

        var adapter = new TestIdentifierAdapter(identifier);

        assertThat(adapter.getSourceLocation()).contains("CustomSource[path=/custom/path]");
    }

    @Test
    @DisplayName("Should return empty optional when source is absent")
    void shouldReturnEmptyWhenSourceAbsent() {
        var identifier = mock(TestIdentifier.class);
        when(identifier.getSource()).thenReturn(Optional.empty());

        var adapter = new TestIdentifierAdapter(identifier);

        assertThat(adapter.getSourceLocation()).isEmpty();
    }

    @Test
    @DisplayName("Should handle empty tags")
    void shouldHandleEmptyTags() {
        var identifier = mock(TestIdentifier.class);
        when(identifier.getTags()).thenReturn(Set.of());

        var adapter = new TestIdentifierAdapter(identifier);

        assertThat(adapter.getTags()).isEmpty();
    }

    @Test
    @DisplayName("With its plan, the suite is the display names of the containers above, without the engine")
    void suiteFromThePlanIsTheContainersAboveWithoutTheEngine() {
        TestPlan plan = discover(PaymentsFixture.class);

        var adapter = new TestIdentifierAdapter(onlyTest(plan), plan);

        assertThat(adapter.getContainerNames()).containsExactly("Payments", "Refunds");
        assertThat(adapter.getEngineName()).contains("JUnit Jupiter");
        assertThat(adapter.getSuite()).containsExactly("Payments", "Refunds");
    }

    @Test
    @DisplayName("A nested class groups its children under its containers and its own name")
    void nestedClassGroupsItsChildrenUnderItsContainersAndItsOwnName() {
        TestPlan plan = discover(PaymentsFixture.class);
        TestIdentifier nestedClass = plan.getParent(onlyTest(plan)).orElseThrow();

        assertThat(new TestIdentifierAdapter(nestedClass, plan).getSuiteOfChildren()).containsExactly("Payments", "Refunds");
    }

    @Test
    @DisplayName("The engine's suite is its own name; a top-level class groups its children under its own name")
    void engineAndTopLevelClass() {
        TestPlan plan = discover(PaymentsFixture.class);
        TestIdentifier engine = plan.getRoots().stream()
            .filter(root -> root.getUniqueId().equals("[engine:junit-jupiter]"))
            .findFirst()
            .orElseThrow();
        TestIdentifier topLevelClass = plan.getChildren(engine).iterator().next();

        assertThat(new TestIdentifierAdapter(engine, plan).getSuite()).containsExactly("JUnit Jupiter");
        assertThat(new TestIdentifierAdapter(topLevelClass, plan).getSuite()).containsExactly("JUnit Jupiter");
        assertThat(new TestIdentifierAdapter(topLevelClass, plan).getSuiteOfChildren()).containsExactly("Payments");
    }

    @Test
    @DisplayName("Review Focus 2: names from a @DisplayNameGeneration are used as JUnit generates them")
    void suiteUsesGeneratedDisplayNames() {
        TestPlan plan = discover(GeneratedNamesFixture.class);

        assertThat(new TestIdentifierAdapter(onlyTest(plan), plan).getSuite()).containsExactly("Generated GeneratedNamesFixture");
    }

    @Test
    @DisplayName("Without a plan, there is no suite")
    void withoutAPlanThereIsNoSuite() {
        TestPlan plan = discover(PaymentsFixture.class);

        assertThat(new TestIdentifierAdapter(onlyTest(plan)).getSuite()).isEmpty();
    }

    @Test
    @DisplayName("Under a suite engine, every engine is left out, and the @Suite class stays")
    void suiteEngineAndTheEngineInsideItAreLeftOut() {
        TestPlan plan = discover(PaymentsSuite.class);

        var adapter = new TestIdentifierAdapter(onlyTest(plan), plan);

        assertThat(adapter.getSuite()).containsExactly("PaymentsSuite", "Payments", "Refunds");
        assertThat(adapter.getEngineName()).contains("JUnit Jupiter");
    }

    @Test
    @DisplayName("An identifier whose ancestors the plan can't give has no suite, and doesn't throw")
    void identifierThePlanCantPlaceHasNoSuite() {
        var identifier = mock(TestIdentifier.class);
        var plan = mock(TestPlan.class);
        when(plan.getParent(identifier)).thenThrow(new PreconditionViolationException("not in the plan"));

        var adapter = new TestIdentifierAdapter(identifier, plan);

        assertThat(adapter.getContainerNames()).isEmpty();
        assertThat(adapter.getEngineName()).isEmpty();
        assertThat(adapter.getSuite()).isEmpty();
    }

    private static TestPlan discover(Class<?> fixture) {
        return LauncherFactory.create().discover(LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(fixture))
            .build());
    }

    private static TestIdentifier onlyTest(TestPlan plan) {
        List<TestIdentifier> tests = plan.getRoots().stream()
            .flatMap(root -> plan.getDescendants(root).stream())
            .filter(TestIdentifier::isTest)
            .toList();
        assertThat(tests).hasSize(1);
        return tests.get(0);
    }

    @Tag("scenario-fixture")
    @DisplayName("Payments")
    static class PaymentsFixture {

        @Nested
        class Refunds {

            @Test
            void refundsTheFee() {
            }
        }
    }

    @Tag("scenario-fixture")
    @Suite
    @SelectClasses(PaymentsFixture.class)
    static class PaymentsSuite {
    }

    @Tag("scenario-fixture")
    @DisplayNameGeneration(PrefixingGenerator.class)
    static class GeneratedNamesFixture {

        @Test
        void refunds() {
        }
    }

    /**
     * Names every class "Generated " followed by its simple name.
     */
    static class PrefixingGenerator extends DisplayNameGenerator.Standard {

        @Override
        public String generateDisplayNameForClass(Class<?> testClass) {
            return "Generated " + testClass.getSimpleName();
        }
    }
}
