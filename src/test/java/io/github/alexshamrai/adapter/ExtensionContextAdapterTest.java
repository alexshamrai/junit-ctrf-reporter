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
}
