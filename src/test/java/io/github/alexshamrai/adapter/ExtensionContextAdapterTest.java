package io.github.alexshamrai.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExtensionContextAdapterTest {

    @Test
    @DisplayName("Should extract unique ID from ExtensionContext")
    void shouldExtractUniqueId() {
        var context = mock(ExtensionContext.class);
        when(context.getUniqueId()).thenReturn("[unique-id]");

        var adapter = new ExtensionContextAdapter(context);

        assertThat(adapter.getUniqueId()).isEqualTo("[unique-id]");
    }

    @Test
    @DisplayName("Should extract display name from ExtensionContext")
    void shouldExtractDisplayName() {
        var context = mock(ExtensionContext.class);
        when(context.getDisplayName()).thenReturn("Test Display Name");

        var adapter = new ExtensionContextAdapter(context);

        assertThat(adapter.getDisplayName()).isEqualTo("Test Display Name");
    }

    @Test
    @DisplayName("Should extract tags from ExtensionContext")
    void shouldExtractTags() {
        var context = mock(ExtensionContext.class);
        var tags = Set.of("unit", "fast");
        when(context.getTags()).thenReturn(tags);

        var adapter = new ExtensionContextAdapter(context);

        assertThat(adapter.getTags()).isEqualTo(tags);
    }

    @Test
    @DisplayName("Should extract source location from test class")
    void shouldExtractSourceLocationFromTestClass() {
        var context = mock(ExtensionContext.class);
        when(context.getTestClass()).thenReturn(Optional.of(ExtensionContextAdapterTest.class));

        var adapter = new ExtensionContextAdapter(context);

        assertThat(adapter.getSourceLocation()).contains("io.github.alexshamrai.adapter.ExtensionContextAdapterTest");
    }

    @Test
    @DisplayName("Should return empty optional when test class is absent")
    void shouldReturnEmptyWhenTestClassAbsent() {
        var context = mock(ExtensionContext.class);
        when(context.getTestClass()).thenReturn(Optional.empty());

        var adapter = new ExtensionContextAdapter(context);

        assertThat(adapter.getSourceLocation()).isEmpty();
    }

    @Test
    @DisplayName("Should handle empty tags")
    void shouldHandleEmptyTags() {
        var context = mock(ExtensionContext.class);
        when(context.getTags()).thenReturn(Set.of());

        var adapter = new ExtensionContextAdapter(context);

        assertThat(adapter.getTags()).isEmpty();
    }

    @Test
    @DisplayName("The suite is the display names of the contexts above, without the root, which is the engine's")
    void suiteIsTheContextsAboveWithoutTheRoot() {
        var root = context("JUnit Jupiter", null);
        var outer = context("Payments", root);
        var nested = context("Refunds", outer);
        var method = context("refundsEachAmount(int)", nested);
        var invocation = context("[1] 1", method);

        var adapter = new ExtensionContextAdapter(invocation);

        assertThat(adapter.getContainerNames()).containsExactly("Payments", "Refunds", "refundsEachAmount(int)");
        assertThat(adapter.getEngineName()).contains("JUnit Jupiter");
        assertThat(adapter.getSuite()).containsExactly("Payments", "Refunds", "refundsEachAmount(int)");
    }

    @Test
    @DisplayName("A class's suite of children ends with its own name; the root's suite is the engine's name")
    void classAndRootSuites() {
        var root = context("JUnit Jupiter", null);
        var outer = context("Payments", root);
        var nested = context("Refunds", outer);

        assertThat(new ExtensionContextAdapter(nested).getSuiteOfChildren()).containsExactly("Payments", "Refunds");
        assertThat(new ExtensionContextAdapter(outer).getSuite()).containsExactly("JUnit Jupiter");
        assertThat(new ExtensionContextAdapter(outer).getSuiteOfChildren()).containsExactly("Payments");
        assertThat(new ExtensionContextAdapter(root).getContainerNames()).isEmpty();
        assertThat(new ExtensionContextAdapter(root).getSuite()).containsExactly("JUnit Jupiter");
    }

    private static ExtensionContext context(String displayName, ExtensionContext parent) {
        var context = mock(ExtensionContext.class);
        when(context.getDisplayName()).thenReturn(displayName);
        when(context.getParent()).thenReturn(Optional.ofNullable(parent));
        return context;
    }
}
