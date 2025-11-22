package io.github.alexshamrai.model;

import io.github.alexshamrai.adapter.ExtensionContextAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestDetailsTest {

    @Test
    void testRecordConstructor() {
        long startTime = System.currentTimeMillis();
        Set<String> tags = new HashSet<>();
        tags.add("tag1");
        String filePath = "path/to/test/file.java";
        String uniqueId = "id-123";
        String displayName = "My Awesome Test";

        TestDetails testDetails = new TestDetails(startTime, tags, filePath, uniqueId, displayName);

        assertEquals(startTime, testDetails.startTime());
        assertEquals(tags, testDetails.tags());
        assertEquals(filePath, testDetails.filePath());
        assertEquals(uniqueId, testDetails.uniqueId());
        assertEquals(displayName, testDetails.displayName());
    }

    @Test
    void testRecordAccessors() {
        long startTime = System.currentTimeMillis();
        var tags = new HashSet<String>();
        tags.add("tag2");
        var filePath = "path/to/another/file.java";
        var uniqueId = "id-456";
        var displayName = "Another Great Test";

        var testDetails = new TestDetails(startTime, tags, filePath, uniqueId, displayName);

        assertEquals(startTime, testDetails.startTime());
        assertEquals(tags, testDetails.tags());
        assertEquals(filePath, testDetails.filePath());
        assertEquals(uniqueId, testDetails.uniqueId());
        assertEquals(displayName, testDetails.displayName());
    }

    @Test
    void testEqualsAndHashCode() {
        long startTime = System.currentTimeMillis();
        var tags = new HashSet<String>();
        tags.add("tag-equals");
        var filePath = "path/to/test/file.java";
        var uniqueId = "id-789";
        var displayName = "Equals Test";

        var testDetails1 = new TestDetails(startTime, tags, filePath, uniqueId, displayName);
        var testDetails2 = new TestDetails(startTime, tags, filePath, uniqueId, displayName);
        var testDetails3 = new TestDetails(startTime + 1000, tags, filePath, uniqueId, displayName);
        var testDetails4 = new TestDetails(startTime, tags, filePath, "id-different", displayName);

        assertEquals(testDetails1, testDetails2);
        assertEquals(testDetails1.hashCode(), testDetails2.hashCode());

        assertNotEquals(testDetails1, testDetails3);
        assertNotEquals(testDetails1.hashCode(), testDetails3.hashCode());

        assertNotEquals(testDetails1, testDetails4);
        assertNotEquals(testDetails1.hashCode(), testDetails4.hashCode());
    }

    @Test
    void fromAdapter_shouldCreateTestDetailsWithAllFields() {
        var mockContext = mock(ExtensionContext.class);
        var tags = Set.of("unit", "fast");
        var testClassName = "io.github.alexshamrai.model.TestDetailsTest";
        var uniqueId = "[test:unique-id]";
        var displayName = "Test Display Name";

        when(mockContext.getUniqueId()).thenReturn(uniqueId);
        when(mockContext.getDisplayName()).thenReturn(displayName);
        when(mockContext.getTags()).thenReturn(tags);
        when(mockContext.getTestClass()).thenReturn(Optional.of(TestDetailsTest.class));

        var adapter = new ExtensionContextAdapter(mockContext);
        var testDetails = TestDetails.fromAdapter(adapter);

        assertNotNull(testDetails);
        assertEquals(uniqueId, testDetails.uniqueId());
        assertEquals(displayName, testDetails.displayName());
        assertEquals(tags, testDetails.tags());
        assertEquals(testClassName, testDetails.filePath());
    }

    @Test
    void fromAdapter_shouldHandleNullFilePath() {
        var mockContext = mock(ExtensionContext.class);
        var tags = Set.of("integration");
        var uniqueId = "[test:unique-id-2]";
        var displayName = "Integration Test";

        when(mockContext.getUniqueId()).thenReturn(uniqueId);
        when(mockContext.getDisplayName()).thenReturn(displayName);
        when(mockContext.getTags()).thenReturn(tags);
        when(mockContext.getTestClass()).thenReturn(Optional.empty());

        var adapter = new ExtensionContextAdapter(mockContext);
        var testDetails = TestDetails.fromAdapter(adapter);

        assertNotNull(testDetails);
        assertEquals(uniqueId, testDetails.uniqueId());
        assertEquals(displayName, testDetails.displayName());
        assertEquals(tags, testDetails.tags());
        assertNull(testDetails.filePath());
    }

    @Test
    void fromAdapter_shouldHandleEmptyTags() {
        var mockContext = mock(ExtensionContext.class);
        var uniqueId = "[test:unique-id-3]";
        var displayName = "Test With No Tags";

        when(mockContext.getUniqueId()).thenReturn(uniqueId);
        when(mockContext.getDisplayName()).thenReturn(displayName);
        when(mockContext.getTags()).thenReturn(Set.of());
        when(mockContext.getTestClass()).thenReturn(Optional.empty());

        var adapter = new ExtensionContextAdapter(mockContext);
        var testDetails = TestDetails.fromAdapter(adapter);

        assertNotNull(testDetails);
        assertEquals(uniqueId, testDetails.uniqueId());
        assertEquals(displayName, testDetails.displayName());
        assertNotNull(testDetails.tags());
        assertEquals(0, testDetails.tags().size());
    }

    @Test
    void fromAdapter_shouldSetStartTimeAutomatically() {
        var mockContext = mock(ExtensionContext.class);
        var uniqueId = "[test:unique-id-4]";
        var displayName = "Test Start Time";

        when(mockContext.getUniqueId()).thenReturn(uniqueId);
        when(mockContext.getDisplayName()).thenReturn(displayName);
        when(mockContext.getTags()).thenReturn(Set.of());
        when(mockContext.getTestClass()).thenReturn(Optional.empty());

        var beforeTime = System.currentTimeMillis();
        var adapter = new ExtensionContextAdapter(mockContext);
        var testDetails = TestDetails.fromAdapter(adapter);
        var afterTime = System.currentTimeMillis();

        assertNotNull(testDetails);
        assertNotNull(testDetails.startTime());
        // Verify startTime is set to current time (within reasonable bounds)
        assertEquals(true, testDetails.startTime() >= beforeTime && testDetails.startTime() <= afterTime);
    }
}
