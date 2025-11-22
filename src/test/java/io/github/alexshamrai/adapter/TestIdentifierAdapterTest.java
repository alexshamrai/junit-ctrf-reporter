package io.github.alexshamrai.adapter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;

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
}
