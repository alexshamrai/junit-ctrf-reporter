package io.github.alexshamrai.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.PreconditionViolationException;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
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

    @Test
    @DisplayName("MP-13: a test that a test method creates is named after the method")
    void testsThatATestMethodCreatesAreNamedAfterIt() {
        Map<String, String> names = namesOfExecuted(GeneratedTestsFixture.class);

        assertThat(nameOf(names, "[method:ordinary()]")).isEqualTo("ordinary()");
        assertThat(nameOf(names, "[test-template:add(int)]/[test-template-invocation:#1]")).isEqualTo("add(int) [1] 1");
        assertThat(nameOf(names, "[test-template:repeated()]/[test-template-invocation:#1]"))
            .isEqualTo("repeated() repetition 1 of 1");
        assertThat(nameOf(names, "[test-factory:factory()]/[dynamic-test:#1]")).isEqualTo("factory() direct");
        assertThat(nameOf(names, "[nested-class:Inner]/[test-template:nestedAdd(int)]/[test-template-invocation:#1]"))
            .isEqualTo("nestedAdd(int) [1] 1");
    }

    @Test
    @DisplayName("MP-13: a dynamic test in a dynamic container is named after the method and the container")
    void dynamicTestInADynamicContainerIsNamedAfterTheMethodAndTheContainer() {
        Map<String, String> names = namesOfExecuted(GeneratedTestsFixture.class);

        assertThat(nameOf(names, "[dynamic-container:#2]/[dynamic-test:#1]")).isEqualTo("factory() group inner");
        assertThat(nameOf(names, "[test-factory:factory()]/[dynamic-container:#2]")).isEqualTo("factory() group");
    }

    @Test
    @DisplayName("MP-13: a name that already starts with the method's, and the method itself, are unchanged")
    void nameThatAlreadyStartsWithTheMethodsAndTheMethodItselfAreUnchanged() {
        Map<String, String> names = namesOfExecuted(GeneratedTestsFixture.class);

        assertThat(nameOf(names, "[test-template:named(int)]/[test-template-invocation:#1]")).isEqualTo("named(int) with 1");
        assertThat(nameOf(names, "[test-template:add(int)]")).isEqualTo("add(int)");
    }

    @Test
    @DisplayName("MP-13: only the method's display name is left out when the name starts with it, as a whole word")
    void onlyTheMethodsDisplayNameAsAWholeWordIsLeftOut() {
        Map<String, String> names = namesOfExecuted(GeneratedTestsFixture.class);

        assertThat(nameOf(names, "[test-template:sum(int)]/[test-template-invocation:#1]")).isEqualTo("sum summary of 1");
        // Not "factory() even 2", which a dynamic test "even 2" directly in the factory gets
        assertThat(nameOf(names, "[dynamic-container:#3]/[dynamic-test:#1]")).isEqualTo("factory() even even 2");
    }

    @Test
    @DisplayName("MP-13: the tests of a @ParameterizedClass keep their names")
    void testsOfAParameterizedClassKeepTheirNames() {
        Map<String, String> names = namesOfExecuted(ParameterizedClassFixture.class);

        assertThat(nameOf(names, "[class-template-invocation:#1]/[method:usesValue()]")).isEqualTo("usesValue()");
    }

    @Test
    @DisplayName("MP-13: a test under a container without a method source, below its class, keeps its name")
    void testUnderAContainerWithoutAMethodSourceKeepsItsName() {
        var plan = mock(TestPlan.class);
        var testClass = container("Calculator", ClassSource.from("com.example.Calculator"));
        var invocation = container("[0]", null);
        var test = test("adds[0]", MethodSource.from("com.example.Calculator", "adds[0]"));
        when(plan.getParent(test)).thenReturn(Optional.of(invocation));
        when(plan.getParent(invocation)).thenReturn(Optional.of(testClass));

        assertThat(new TestIdentifierAdapter(test, plan).getName()).isEqualTo("adds[0]");
    }

    @Test
    @DisplayName("MP-13: every container below the outermost test method is in the name, whatever its source")
    void everyContainerBelowTheOutermostTestMethodIsInTheName() {
        var plan = mock(TestPlan.class);
        var factory = container("factory()", MethodSource.from("com.example.Calculator", "factory"));
        var group = container("group", ClassSource.from("com.example.Other"));
        var test = test("inner", null);
        when(plan.getParent(test)).thenReturn(Optional.of(group));
        when(plan.getParent(group)).thenReturn(Optional.of(factory));

        assertThat(new TestIdentifierAdapter(test, plan).getName()).isEqualTo("factory() group inner");
    }

    @Test
    @DisplayName("MP-13: without a plan, or with one that can't place the test, the name is the display name")
    void withoutAPlaceInThePlanTheNameIsTheDisplayName() {
        var test = test("[1] 1", MethodSource.from("com.example.Calculator", "add"));
        var plan = mock(TestPlan.class);
        when(plan.getParent(test)).thenThrow(new PreconditionViolationException("not in the plan"));

        assertThat(new TestIdentifierAdapter(test).getName()).isEqualTo("[1] 1");
        assertThat(new TestIdentifierAdapter(test, plan).getName()).isEqualTo("[1] 1");
    }

    private static TestPlan discover(Class<?> fixture) {
        return LauncherFactory.create().discover(LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(fixture))
            .build());
    }

    /**
     * Runs the fixture without the listeners that JUnit registers on its own, and returns the name that the adapter
     * gives each test and container when it finishes, by unique ID.
     */
    private static Map<String, String> namesOfExecuted(Class<?> fixture) {
        Map<String, String> names = new ConcurrentHashMap<>();
        var listener = new TestExecutionListener() {
            private TestPlan plan;

            @Override
            public void testPlanExecutionStarted(TestPlan testPlan) {
                plan = testPlan;
            }

            @Override
            public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
                names.put(identifier.getUniqueId(), new TestIdentifierAdapter(identifier, plan).getName());
            }
        };
        LauncherFactory.create(LauncherConfig.builder().enableTestExecutionListenerAutoRegistration(false).build())
            .execute(LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass(fixture)).build(),
                listener);
        return names;
    }

    private static String nameOf(Map<String, String> names, String uniqueIdEnd) {
        List<String> matches = names.entrySet().stream()
            .filter(entry -> entry.getKey().endsWith(uniqueIdEnd))
            .map(Map.Entry::getValue)
            .toList();
        assertThat(matches).as("names of %s in %s", uniqueIdEnd, names.keySet()).hasSize(1);
        return matches.get(0);
    }

    private static TestIdentifier container(String displayName, TestSource source) {
        var identifier = mock(TestIdentifier.class);
        when(identifier.getDisplayName()).thenReturn(displayName);
        when(identifier.isContainer()).thenReturn(true);
        when(identifier.getSource()).thenReturn(Optional.ofNullable(source));
        return identifier;
    }

    private static TestIdentifier test(String displayName, TestSource source) {
        var identifier = mock(TestIdentifier.class);
        when(identifier.getDisplayName()).thenReturn(displayName);
        when(identifier.isTest()).thenReturn(true);
        when(identifier.getSource()).thenReturn(Optional.ofNullable(source));
        return identifier;
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
    static class GeneratedTestsFixture {

        @Test
        void ordinary() {
        }

        @ParameterizedTest
        @ValueSource(ints = 1)
        void add(int value) {
        }

        @ParameterizedTest(name = "{displayName} with {0}")
        @ValueSource(ints = 1)
        void named(int value) {
        }

        @DisplayName("sum")
        @ParameterizedTest(name = "summary of {0}")
        @ValueSource(ints = 1)
        void sum(int value) {
        }

        @RepeatedTest(1)
        void repeated() {
        }

        @TestFactory
        Stream<DynamicNode> factory() {
            return Stream.of(
                dynamicTest("direct", () -> { }),
                dynamicContainer("group", Stream.of(dynamicTest("inner", () -> { }))),
                dynamicContainer("even", Stream.of(dynamicTest("even 2", () -> { }))));
        }

        @Nested
        class Inner {

            @ParameterizedTest
            @ValueSource(ints = 1)
            void nestedAdd(int value) {
            }
        }
    }

    @Tag("scenario-fixture")
    @ParameterizedClass
    @ValueSource(ints = 1)
    static class ParameterizedClassFixture {

        @Parameter
        int value;

        @Test
        void usesValue() {
        }
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
