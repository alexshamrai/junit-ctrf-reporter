# JUnit CTRF Reporter - Comprehensive Improvement Plan

This document provides a detailed, actionable plan for addressing code quality issues, bugs, and architectural improvements identified in the codebase review.

## How to Use This Plan

Each improvement item includes:
- **Priority**: Critical/High/Medium/Low
- **Complexity**: Simple/Moderate/Complex
- **Prompt**: Detailed instructions for implementation
- **Acceptance Criteria**: How to verify the fix is complete
- **Files Affected**: Which files need changes

---

## Table of Contents

1. [Critical Bugs](#critical-bugs)
2. [High Priority Design Issues](#high-priority-design-issues)
3. [Medium Priority Improvements](#medium-priority-improvements)
4. [Code Smells & Quality Issues](#code-smells--quality-issues)
5. [Testing Improvements](#testing-improvements)
6. [Documentation Improvements](#documentation-improvements)
7. [Security & Performance](#security--performance)

---

## Critical Bugs

### CB-1: Fix NullPointerException in CtrfReportFileService.getExistingTests()

**Priority**: Critical
**Complexity**: Simple
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportFileService.java`

#### Prompt
```
Fix the potential NullPointerException in CtrfReportFileService.getExistingTests() method at line 60.

Current code:
```java
public List<Test> getExistingTests() {
    CtrfJson existingReport = readExistingReport();
    return existingReport != null ? existingReport.getResults().getTests() : Collections.emptyList();
}
```

Problem: While existingReport is null-checked, getResults() could return null, causing NPE when calling getTests().

Requirements:
1. Add null-checks for both getResults() and getTests()
2. Follow the same pattern used in getExistingStartTime() and getExistingEnvironmentHealth() methods
3. Return Collections.emptyList() if any intermediate value is null
4. Add unit tests to verify behavior with:
   - Null report
   - Report with null results
   - Report with null tests list
   - Valid report with tests

Expected result:
```java
public List<Test> getExistingTests() {
    CtrfJson existingReport = readExistingReport();
    return existingReport != null
        && existingReport.getResults() != null
        && existingReport.getResults().getTests() != null
        ? existingReport.getResults().getTests()
        : Collections.emptyList();
}
```
```

#### Acceptance Criteria
- [ ] Method returns empty list when report is null
- [ ] Method returns empty list when results is null
- [ ] Method returns empty list when tests is null
- [ ] Method returns actual tests list when all values are present
- [ ] Unit tests cover all four scenarios
- [ ] No NullPointerExceptions in any scenario

---

### CB-2: Fix Race Condition in CtrfReportManager.finishTestRun()

**Priority**: Critical
**Complexity**: Moderate
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportManager.java`

#### Prompt
```
Fix the race condition in CtrfReportManager.finishTestRun() where test results can be lost.

Current issue:
The method uses compareAndSet to ensure single execution, but between the CAS check and tests.clear(),
other threads can add test results that will then be lost:

```java
if (!isTestRunStarted.compareAndSet(true, false)) {
    return;
}
// ... long operations including file I/O ...
tests.clear(); // <- Results added after CAS but before here are lost
```

Requirements:
1. Take a snapshot of the tests list immediately after the CAS operation
2. Use the snapshot for all report generation operations
3. Clear the original list only after snapshot is taken
4. Document the threading model in javadoc
5. Add concurrent test to verify no results are lost when:
   - finishTestRun() is called
   - Another thread calls onTestSuccess() simultaneously

Solution approach:
```java
if (!isTestRunStarted.compareAndSet(true, false)) {
    return;
}

// Take snapshot immediately to avoid race condition
List<Test> testSnapshot = new ArrayList<>(tests);
tests.clear();

// Use testSnapshot for all remaining operations
handleRerunsAndFlaky(testSnapshot);
// ... rest of the method using testSnapshot
```

Alternative approach:
Document that finishTestRun() must only be called after all test execution callbacks are complete,
and add a check to verify no tests are in progress.
```

#### Acceptance Criteria
- [ ] Test results are captured atomically
- [ ] No results lost when concurrent callbacks occur
- [ ] Clear documentation of threading requirements
- [ ] Concurrent test verifies thread safety
- [ ] Integration tests pass with parallel execution
- [ ] Performance impact is minimal

---

### CB-3: Fix Multiple ConfigReader Instantiations

**Priority**: Critical
**Complexity**: Simple
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportManager.java`

#### Prompt
```
Eliminate multiple instantiations of ConfigReader in CtrfReportManager.

Current issue:
ConfigReader is created twice:
1. In constructor at line 37
2. In finishTestRun() at line 154

This causes unnecessary object creation and potential configuration inconsistency if system properties
change during execution.

Requirements:
1. Create ConfigReader once as a final field in the constructor
2. Reuse the same instance in finishTestRun()
3. Update CtrfJsonComposer to use the stored configReader
4. Ensure the package-private test constructor also initializes configReader properly
5. Update all tests to verify configuration is read only once

Changes needed:
1. Add final field: `private final ConfigReader configReader;`
2. Initialize in constructor (line 37)
3. Remove instantiation from finishTestRun() (line 154)
4. Pass stored configReader to CtrfJsonComposer constructor
5. Update test constructor to accept ConfigReader parameter if needed

Verify that:
- Configuration is consistent throughout test execution
- No performance regression from Owner library caching
- Tests still pass, including configuration-related tests
```

#### Acceptance Criteria
- [ ] ConfigReader created only once per CtrfReportManager instance
- [ ] Stored as final field
- [ ] Reused in all methods that need configuration
- [ ] All tests pass
- [ ] No configuration inconsistencies possible
- [ ] Code is cleaner and more maintainable

---

## High Priority Design Issues

### HP-1: Refactor Singleton Pattern in CtrfReportManager

**Priority**: High
**Complexity**: Complex
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportManager.java`, `CtrfExtension.java`, `CtrfListener.java`, test files

#### Prompt
```
Refactor the inconsistent singleton pattern in CtrfReportManager to use proper dependency injection.

Current issues:
1. Static singleton instance but package-private constructor breaks singleton guarantees
2. ctrfJsonComposer set to null in constructor, then recreated in finishTestRun()
3. Dependencies directly instantiated instead of injected
4. Hard to test due to global state

Recommended approach: Remove singleton pattern entirely and use dependency injection

Steps:
1. Remove static INSTANCE field and getInstance() method
2. Make constructor public and require all dependencies as parameters
3. Update CtrfExtension and CtrfListener to create their own CtrfReportManager instances
4. For shared state (when both Extension and Listener are used), implement a proper registry pattern:
   ```java
   public class CtrfReportRegistry {
       private static final ConcurrentHashMap<String, CtrfReportManager> managers = new ConcurrentHashMap<>();

       public static CtrfReportManager getOrCreate(String key, Supplier<CtrfReportManager> factory) {
           return managers.computeIfAbsent(key, k -> factory.get());
       }
   }
   ```
5. Update all tests to use dependency injection
6. Document the new instantiation model in javadoc and CLAUDE.md

Benefits:
- Proper testability with constructor injection
- No global mutable state
- Clear dependency graph
- Can create multiple instances for testing
- Follows SOLID principles

Alternative (if singleton is absolutely required):
Implement proper singleton with:
- Private constructor
- Lazy initialization with double-checked locking
- All dependencies injected via factory methods
- Remove package-private constructor
```

#### Acceptance Criteria
- [ ] Singleton pattern removed or properly implemented
- [ ] All dependencies injected through constructor
- [ ] Tests use dependency injection
- [ ] No global mutable state accessed from constructors
- [ ] CtrfExtension and CtrfListener both work correctly
- [ ] All existing tests pass
- [ ] New unit tests verify isolation between instances
- [ ] Documentation updated

---

### HP-2: Break Up CtrfReportManager God Class

**Priority**: High
**Complexity**: Complex
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportManager.java` + new files

#### Prompt
```
Refactor CtrfReportManager into smaller, focused classes following Single Responsibility Principle.

Current issues:
- 193 lines with multiple responsibilities
- 13 public/package methods
- 6 instance fields
- Handles test lifecycle, state tracking, flaky detection, orchestration, and environment health

Proposed new structure:

1. **TestStateTracker** (new class)
   - Manages CopyOnWriteArrayList<Test> tests
   - Manages ConcurrentHashMap<String, TestDetails> testDetailsMap
   - Methods: addTest(), getTest(), getAllTests(), clear()
   - Thread-safe operations on test state

2. **FlakyTestDetector** (new class)
   - Logic from handleRerunsAndFlaky() and findTestsByName()
   - Methods: detectFlakyTests(List<Test> tests), markTestAsFlaky(Test test)
   - Pure logic, no state

3. **TestRerunHandler** (new class)
   - Handles test rerun logic
   - Merges existing tests with new test results
   - Methods: mergeTestResults(List<Test> existing, List<Test> current)

4. **ReportOrchestrator** (new class)
   - Coordinates report generation
   - Delegates to CtrfJsonComposer and CtrfReportFileService
   - Methods: generateReport(TestStateTracker state, CtrfConfig config)

5. **CtrfReportManager** (simplified)
   - Lifecycle coordinator only
   - Delegates to above classes
   - Much simpler, ~80 lines

Implementation steps:
1. Create TestStateTracker with test collection management
2. Extract FlakyTestDetector with pure functions
3. Extract TestRerunHandler with merge logic
4. Create ReportOrchestrator for composition
5. Update CtrfReportManager to delegate to new classes
6. Update all tests to work with new structure
7. Ensure thread safety is maintained
8. Update documentation

Each new class should be:
- Focused on single responsibility
- Independently testable
- Well-documented with javadoc
- Thread-safe where needed
```

#### Acceptance Criteria
- [ ] CtrfReportManager reduced to <100 lines
- [ ] Each new class has single, clear responsibility
- [ ] All existing functionality preserved
- [ ] Thread safety maintained or improved
- [ ] Each class has comprehensive unit tests
- [ ] Integration tests pass
- [ ] Code coverage maintained or improved
- [ ] Documentation updated in CLAUDE.md

---

### HP-3: Implement Proper Logging Framework

**Priority**: High
**Complexity**: Moderate
**Files**: `CtrfReportFileService.java`, `build.gradle`, and classes with System.out/err

#### Prompt
```
Replace System.out/err with proper SLF4J logging throughout the codebase.

Current issues:
1. SLF4J and Logback in dependencies but never used
2. Errors written to System.err.println() (CtrfReportFileService lines 47, 49, 113)
3. Info messages to System.out.println() (CtrfReportFileService line 110)
4. No way for users to control log levels or output
5. Cannot integrate with existing logging infrastructure

Requirements:
1. Add SLF4J Logger to each class that needs logging
2. Replace all System.out with logger.info() or logger.debug()
3. Replace all System.err with appropriate log levels (error/warn)
4. Add proper exception logging with logger.error("message", exception)
5. Use parameterized logging: logger.info("Writing report to {}", path)
6. Add logback-test.xml for test logging configuration
7. Make logging optional for library users (don't force logback implementation)
8. Document logging configuration in README

Example transformation:
```java
// Before
System.err.println("Error reading existing report: " + e.getMessage());

// After
private static final Logger logger = LoggerFactory.getLogger(CtrfReportFileService.class);
logger.error("Error reading existing report from {}", filePath, e);
```

Log level guidelines:
- ERROR: Exceptions, failures that prevent core functionality
- WARN: Issues that don't prevent operation (file exists, etc.)
- INFO: Important events (report written, tests completed)
- DEBUG: Detailed information for troubleshooting
- TRACE: Very detailed information (not needed initially)

Files to update:
1. CtrfReportFileService.java - primary candidate
2. Any other classes with System.out/err
3. build.gradle - ensure SLF4J is compile scope, Logback is test scope
4. Add logback-test.xml configuration
5. Update CLAUDE.md with logging information
```

#### Acceptance Criteria
- [ ] No System.out.println() or System.err.println() in production code
- [ ] SLF4J Logger added to relevant classes
- [ ] Appropriate log levels used
- [ ] Exceptions logged with full stack traces
- [ ] Parameterized logging used for performance
- [ ] logback-test.xml provides reasonable defaults for tests
- [ ] Documentation explains logging configuration
- [ ] Library users can provide their own SLF4J implementation
- [ ] All tests pass with logging enabled

---

### HP-4: Remove or Fix TestDetailsUtil Duplication

**Priority**: High
**Complexity**: Moderate
**Files**: `TestDetailsUtil.java`, `CtrfExtension.java`, `CtrfListener.java`

#### Prompt
```
Eliminate code duplication by consolidating the three implementations of createTestDetails() logic.

Current situation:
Three nearly identical implementations exist:
1. TestDetailsUtil.createTestDetails() - never used
2. CtrfExtension.createTestDetails() - lines 70-77
3. CtrfListener.createTestDetails() - lines 93-100

Differences:
- Extension uses ExtensionContext directly
- Listener converts tags to strings and has custom extractFilePathFromSource logic
- TestDetailsUtil is unused

Recommended approach: Create unified utility with strategy pattern

1. Create TestContextAdapter interface:
   ```java
   public interface TestContextAdapter {
       String getUniqueId();
       String getDisplayName();
       Set<String> getTags();
       Optional<String> getSourceLocation();
       String getThreadId();
   }
   ```

2. Implement adapters:
   ```java
   public class ExtensionContextAdapter implements TestContextAdapter {
       private final ExtensionContext context;
       // ... implement methods using ExtensionContext
   }

   public class TestIdentifierAdapter implements TestContextAdapter {
       private final TestIdentifier identifier;
       // ... implement methods using TestIdentifier
   }
   ```

3. Update TestDetailsUtil to accept TestContextAdapter:
   ```java
   public static TestDetails createTestDetails(TestContextAdapter context) {
       return TestDetails.builder()
           .uniqueId(context.getUniqueId())
           .displayName(context.getDisplayName())
           .tags(context.getTags())
           .filePath(context.getSourceLocation().orElse(null))
           .threadId(context.getThreadId())
           .build();
   }
   ```

4. Update CtrfExtension to use:
   ```java
   private TestDetails createTestDetails(ExtensionContext context) {
       return TestDetailsUtil.createTestDetails(new ExtensionContextAdapter(context));
   }
   ```

5. Update CtrfListener similarly

6. Add tests for all adapters and TestDetailsUtil

Benefits:
- Single source of truth for TestDetails creation
- Easy to add new context sources
- Better testability
- Clear separation of concerns
```

#### Acceptance Criteria
- [ ] Only one implementation of createTestDetails logic exists
- [ ] Both CtrfExtension and CtrfListener use shared utility
- [ ] No code duplication between entry points
- [ ] All tests pass
- [ ] New tests cover adapter pattern
- [ ] Behavior identical to previous implementation
- [ ] Code is cleaner and more maintainable

---

## Medium Priority Improvements

### MP-1: Optimize SummaryUtil with Single-Pass Counting

**Priority**: Medium
**Complexity**: Simple
**Files**: `src/main/java/io/github/alexshamrai/util/SummaryUtil.java`

#### Prompt
```
Optimize SummaryUtil.createSummary() to count test statuses in a single pass instead of five separate streams.

Current implementation (lines 10-20):
```java
.passed((int) tests.stream().filter(t -> t.getStatus() == PASSED).count())
.failed((int) tests.stream().filter(t -> t.getStatus() == FAILED).count())
.skipped((int) tests.stream().filter(t -> t.getStatus() == SKIPPED).count())
.pending((int) tests.stream().filter(t -> t.getStatus() == PENDING).count())
.other((int) tests.stream().filter(t -> t.getStatus() == OTHER).count())
```

Problem: O(5n) complexity - list is iterated 5 times

Solution: Use single stream with grouping collector

Implementation:
```java
public static Summary createSummary(List<Test> tests, long start, long stop) {
    // Single pass to count all statuses
    Map<TestStatus, Long> statusCounts = tests.stream()
        .collect(Collectors.groupingBy(Test::getStatus, Collectors.counting()));

    // Helper to get count with default 0
    java.util.function.ToIntFunction<TestStatus> getCount =
        status -> statusCounts.getOrDefault(status, 0L).intValue();

    // Count flaky tests in same pass or separate if needed
    long flakyCount = tests.stream().filter(Test::isFlaky).count();

    return Summary.builder()
        .tests(tests.size())
        .passed(getCount.applyAsInt(PASSED))
        .failed(getCount.applyAsInt(FAILED))
        .skipped(getCount.applyAsInt(SKIPPED))
        .pending(getCount.applyAsInt(PENDING))
        .other(getCount.applyAsInt(OTHER))
        .flaky((int) flakyCount)
        .start(start)
        .stop(stop)
        .build();
}
```

Performance improvement:
- Before: O(5n) - five full iterations
- After: O(n) - single iteration
- For 1000 tests: ~5x faster
- For 10000 tests: ~5x faster

Requirements:
1. Maintain identical behavior
2. Add performance benchmark test comparing old vs new approach
3. Verify all test statuses are counted correctly
4. Handle empty list edge case
5. Update unit tests to verify grouping logic
```

#### Acceptance Criteria
- [ ] Single stream operation counts all statuses
- [ ] Behavior identical to previous implementation
- [ ] All unit tests pass
- [ ] Performance benchmark shows improvement
- [ ] Edge cases handled (empty list, all same status, etc.)
- [ ] Code is more readable and maintainable

---

### MP-2: Cache readExistingReport() Results

**Priority**: Medium
**Complexity**: Moderate
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportFileService.java`

#### Prompt
```
Optimize CtrfReportFileService by caching the result of readExistingReport() to avoid multiple file reads.

Current issue:
readExistingReport() is called three times on startup:
1. getExistingTests()
2. getExistingStartTime()
3. getExistingEnvironmentHealth()

Each call reads and parses the same JSON file, which is wasteful for large reports.

Solution: Implement lazy initialization with caching

Implementation approach:
```java
public class CtrfReportFileService {
    private final ConfigReader configReader;
    private CtrfJson cachedReport; // Cache the parsed report
    private boolean reportRead = false; // Flag to track if we've attempted to read

    // Update existing methods to use cache
    private CtrfJson readExistingReportCached() {
        if (!reportRead) {
            cachedReport = readExistingReport();
            reportRead = true;
        }
        return cachedReport;
    }

    public List<Test> getExistingTests() {
        CtrfJson existingReport = readExistingReportCached();
        // ... rest of implementation
    }

    public Long getExistingStartTime() {
        CtrfJson existingReport = readExistingReportCached();
        // ... rest of implementation
    }

    public Boolean getExistingEnvironmentHealth() {
        CtrfJson existingReport = readExistingReportCached();
        // ... rest of implementation
    }

    // Add method to invalidate cache if needed
    public void invalidateCache() {
        cachedReport = null;
        reportRead = false;
    }
}
```

Thread safety considerations:
- If this service is shared across threads, add synchronization
- Or make the cache volatile with double-checked locking
- Document threading requirements

Requirements:
1. Cache the parsed CtrfJson after first read
2. Reuse cached value for subsequent calls
3. Handle thread safety if service is shared
4. Add method to invalidate cache if needed
5. Update tests to verify caching behavior
6. Measure performance improvement for large reports

Alternative simpler approach:
Read the report once in constructor or in a single initialization method called by CtrfReportManager.
```

#### Acceptance Criteria
- [ ] Report file read only once per service instance
- [ ] All three getter methods use cached value
- [ ] Behavior identical to previous implementation
- [ ] Thread safety maintained if needed
- [ ] Tests verify caching works correctly
- [ ] Performance improvement measurable for large reports
- [ ] No stale data issues

---

### MP-3: Replace CopyOnWriteArrayList with Better Alternative

**Priority**: Medium
**Complexity**: Moderate
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportManager.java`

#### Prompt
```
Evaluate and potentially replace CopyOnWriteArrayList<Test> with a more appropriate concurrent collection.

Current implementation (line 24):
```java
private final List<Test> tests = new CopyOnWriteArrayList<>();
```

Analysis:
CopyOnWriteArrayList is optimized for:
- Many reads, few writes
- Read operations don't block

Current usage pattern:
- Many writes during test execution (one per test)
- Single read at end for report generation
- For 1000 tests, the array is copied 1000 times

Performance issue:
- Each add() copies entire array: O(n) per insertion
- Total cost for n tests: O(n²)
- For 1000 tests: ~500,000 operations
- For 10,000 tests: ~50,000,000 operations

Task requirements:
1. Benchmark current implementation with 1000, 5000, 10000 tests
2. Compare alternatives:
   - Collections.synchronizedList(new ArrayList<>())
   - ConcurrentLinkedQueue (if order doesn't matter)
   - Custom solution with ReentrantLock
3. Consider read/write patterns:
   - Many concurrent writes during execution
   - Single read at end
   - Clear at end
4. Run performance benchmarks
5. Evaluate thread safety guarantees needed
6. Choose best option based on data
7. Update implementation
8. Verify all integration tests pass with parallel execution

Benchmark code template:
```java
@Test
void benchmarkTestCollection() {
    int numTests = 10000;

    // Test CopyOnWriteArrayList
    long cowStart = System.nanoTime();
    List<Test> cowList = new CopyOnWriteArrayList<>();
    for (int i = 0; i < numTests; i++) {
        cowList.add(createTestResult());
    }
    long cowTime = System.nanoTime() - cowStart;

    // Test synchronized ArrayList
    long syncStart = System.nanoTime();
    List<Test> syncList = Collections.synchronizedList(new ArrayList<>());
    for (int i = 0; i < numTests; i++) {
        syncList.add(createTestResult());
    }
    long syncTime = System.nanoTime() - syncStart;

    System.out.printf("CopyOnWrite: %d ms, Synchronized: %d ms%n",
        cowTime / 1_000_000, syncTime / 1_000_000);
}
```

Recommendation (pending benchmarks):
If order matters: `Collections.synchronizedList(new ArrayList<>())`
If order doesn't matter: `ConcurrentLinkedQueue` (fastest for concurrent writes)
```

#### Acceptance Criteria
- [ ] Performance benchmarks completed for multiple collection types
- [ ] Data-driven decision made based on benchmarks
- [ ] Implementation replaced with better alternative
- [ ] Thread safety maintained or improved
- [ ] All tests pass, including parallel integration tests
- [ ] Performance improvement measurable
- [ ] Documentation updated with rationale

---

### MP-4: Add Comprehensive Input Validation

**Priority**: Medium
**Complexity**: Moderate
**Files**: Multiple (ConfigReader, CtrfReportManager, model classes)

#### Prompt
```
Add comprehensive input validation throughout the codebase to prevent invalid states and improve error messages.

Current issues:
1. No validation of configuration values (e.g., negative maxMessageLength)
2. No validation that report path is writable before execution
3. No validation that test names are non-null
4. No validation of time values (start > stop, negative durations)
5. Model objects can be created in invalid states

Implementation strategy:

1. **Create validation utilities**:
```java
public class Validators {
    public static <T> T requireNonNull(T obj, String paramName) {
        if (obj == null) {
            throw new IllegalArgumentException(paramName + " must not be null");
        }
        return obj;
    }

    public static int requirePositive(int value, String paramName) {
        if (value <= 0) {
            throw new IllegalArgumentException(paramName + " must be positive, got: " + value);
        }
        return value;
    }

    public static String requireNonBlank(String str, String paramName) {
        if (str == null || str.isBlank()) {
            throw new IllegalArgumentException(paramName + " must not be blank");
        }
        return str;
    }

    public static Path requireWritablePath(String pathStr, String paramName) {
        Path path = Paths.get(pathStr);
        Path parent = path.getParent();
        if (parent != null && !Files.isWritable(parent)) {
            throw new IllegalArgumentException(
                paramName + " parent directory is not writable: " + parent);
        }
        return path;
    }
}
```

2. **Add ConfigValidator**:
```java
public class ConfigValidator {
    public static void validate(CtrfConfig config) {
        requireNonBlank(config.getReportPath(), "ctrf.report.path");
        requirePositive(config.getMaxMessageLength(), "ctrf.max.message.length");
        requireWritablePath(config.getReportPath(), "ctrf.report.path");
        // ... other validations
    }
}
```

3. **Validate at entry points**:
- CtrfReportManager constructor
- CtrfReportFileService before write
- Model builders (add validation in build() methods)

4. **Add validation to critical methods**:
```java
public void onTestSuccess(String uniqueId) {
    requireNonBlank(uniqueId, "uniqueId");
    // ... rest of implementation
}
```

5. **Validate time values**:
```java
public static void validateTimeRange(long start, long stop) {
    if (start < 0) throw new IllegalArgumentException("start time must be non-negative");
    if (stop < 0) throw new IllegalArgumentException("stop time must be non-negative");
    if (stop < start) throw new IllegalArgumentException("stop time must be >= start time");
}
```

6. **Add validation tests** for:
- Each validator utility
- Configuration validation
- Model object validation
- Edge cases (null, negative, empty)

Files to update:
1. Create Validators utility class
2. Create ConfigValidator
3. Update CtrfReportManager to validate inputs
4. Update CtrfReportFileService to validate paths
5. Add validation to model builders if using Lombok @Builder
6. Update all relevant tests
7. Document validation behavior in javadoc
```

#### Acceptance Criteria
- [ ] Validators utility class created with comprehensive methods
- [ ] Configuration validated on startup
- [ ] Report path validated before writing
- [ ] All public method parameters validated
- [ ] Model objects cannot be created in invalid states
- [ ] Clear error messages for validation failures
- [ ] Tests cover all validation scenarios
- [ ] Documentation explains validation behavior
- [ ] No invalid states possible at runtime

---

## Code Smells & Quality Issues

### CQ-1: Extract Feature Envy from CtrfReportManager

**Priority**: Medium
**Complexity**: Moderate
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportManager.java` + new files

#### Prompt
```
Extract the handleRerunsAndFlaky() and findTestsByName() methods into a dedicated FlakyTestAnalyzer class.

Current issue:
These methods in CtrfReportManager (lines 166-193) operate primarily on the tests list data,
suggesting this logic belongs in a separate class. This is the "Feature Envy" code smell.

Create FlakyTestAnalyzer class:
```java
package io.github.alexshamrai.analysis;

/**
 * Analyzes test results to detect flaky tests and handle test reruns.
 *
 * A test is considered flaky if:
 * - It passed but has retries > 0 (indicating previous failures)
 * - It exists in previous runs with different outcome
 */
public class FlakyTestAnalyzer {

    /**
     * Processes a list of tests to identify and mark flaky tests based on rerun history.
     *
     * @param tests The list of tests to analyze (will be modified in place)
     */
    public void detectAndMarkFlakyTests(List<Test> tests) {
        for (Test test : tests) {
            if (isFlaky(test, tests)) {
                test.setFlaky(true);
            }
        }
    }

    /**
     * Determines if a test is flaky based on retry count and previous executions.
     */
    private boolean isFlaky(Test test, List<Test> allTests) {
        if (test.getRetries() > 0 && "passed".equals(test.getStatus())) {
            return true;
        }

        // Check if there are multiple executions with different outcomes
        List<Test> sameTests = findTestsByName(test.getName(), allTests);
        if (sameTests.size() > 1) {
            Set<String> statuses = sameTests.stream()
                .map(Test::getStatus)
                .collect(Collectors.toSet());
            return statuses.size() > 1;
        }

        return false;
    }

    /**
     * Finds all tests with the given name.
     */
    private List<Test> findTestsByName(String name, List<Test> tests) {
        return tests.stream()
            .filter(t -> name.equals(t.getName()))
            .collect(Collectors.toList());
    }

    /**
     * Merges test results from reruns, keeping the latest result but preserving history.
     *
     * @param existingTests Tests from previous runs
     * @param newTests Tests from current run
     * @return Merged list with flaky tests marked
     */
    public List<Test> mergeRerunResults(List<Test> existingTests, List<Test> newTests) {
        // Implementation of merge logic
        List<Test> merged = new ArrayList<>(newTests);

        // Add tests from existing that aren't in new
        for (Test existing : existingTests) {
            boolean foundInNew = newTests.stream()
                .anyMatch(t -> t.getName().equals(existing.getName()));
            if (!foundInNew) {
                merged.add(existing);
            }
        }

        detectAndMarkFlakyTests(merged);
        return merged;
    }
}
```

Update CtrfReportManager:
1. Add FlakyTestAnalyzer as a dependency
2. Replace handleRerunsAndFlaky() with call to analyzer
3. Remove findTestsByName() method
4. Simplify finishTestRun() method

Benefits:
- Single responsibility for each class
- Easier to test flaky detection logic in isolation
- Can add more sophisticated flaky detection algorithms
- Cleaner CtrfReportManager

Requirements:
1. Create FlakyTestAnalyzer class with comprehensive javadoc
2. Move logic from CtrfReportManager
3. Add extensive unit tests for analyzer
4. Update CtrfReportManager to use analyzer
5. Verify all integration tests pass
6. Update documentation
```

#### Acceptance Criteria
- [ ] FlakyTestAnalyzer class created with clear responsibility
- [ ] All flaky detection logic moved to analyzer
- [ ] CtrfReportManager simplified
- [ ] Comprehensive unit tests for analyzer
- [ ] All integration tests pass
- [ ] Behavior identical to previous implementation
- [ ] Documentation updated

---

### CQ-2: Replace Primitive Obsession with Value Objects

**Priority**: Low
**Complexity**: Moderate
**Files**: Multiple (model package, CtrfReportManager, TestDetails)

#### Prompt
```
Replace primitive type parameters with meaningful value objects to improve type safety and clarity.

Current primitive obsession examples:
1. String uniqueId - could be TestIdentifier type
2. long startTime, stopTime - could be TestDuration value object
3. boolean isEnvironmentHealthy - could be EnvironmentHealth enum

Implementation:

1. **Create TestIdentifier value object**:
```java
package io.github.alexshamrai.model;

/**
 * Type-safe wrapper for test unique identifiers.
 * Ensures test IDs are non-null and properly formatted.
 */
public final class TestIdentifier {
    private final String value;

    private TestIdentifier(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Test identifier must not be blank");
        }
        this.value = value;
    }

    public static TestIdentifier of(String value) {
        return new TestIdentifier(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestIdentifier)) return false;
        TestIdentifier that = (TestIdentifier) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
```

2. **Create TestDuration value object**:
```java
package io.github.alexshamrai.model;

/**
 * Represents the duration of a test execution with start and stop times.
 * Ensures temporal consistency (stop >= start).
 */
public final class TestDuration {
    private final long startTimeMillis;
    private final long stopTimeMillis;

    private TestDuration(long startTimeMillis, long stopTimeMillis) {
        if (startTimeMillis < 0) {
            throw new IllegalArgumentException("Start time must be non-negative");
        }
        if (stopTimeMillis < startTimeMillis) {
            throw new IllegalArgumentException("Stop time must be >= start time");
        }
        this.startTimeMillis = startTimeMillis;
        this.stopTimeMillis = stopTimeMillis;
    }

    public static TestDuration of(long startTimeMillis, long stopTimeMillis) {
        return new TestDuration(startTimeMillis, stopTimeMillis);
    }

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public long getStopTimeMillis() {
        return stopTimeMillis;
    }

    public long getDurationMillis() {
        return stopTimeMillis - startTimeMillis;
    }

    // equals, hashCode, toString
}
```

3. **Create EnvironmentHealth enum**:
```java
package io.github.alexshamrai.model;

/**
 * Represents the health status of the test environment.
 */
public enum EnvironmentHealth {
    HEALTHY(true),
    UNHEALTHY(false);

    private final boolean healthy;

    EnvironmentHealth(boolean healthy) {
        this.healthy = healthy;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public static EnvironmentHealth fromBoolean(boolean healthy) {
        return healthy ? HEALTHY : UNHEALTHY;
    }
}
```

4. **Update TestDetails to use value objects**:
```java
@Data
@Builder
public class TestDetails {
    private final TestIdentifier identifier;
    private final String displayName;
    private final Set<String> tags;
    private final String filePath;
    private final String threadId;
    private TestDuration duration;
}
```

5. **Update CtrfReportManager method signatures**:
```java
// Before
public void onTestStart(String uniqueId)
public void onTestSuccess(String uniqueId)

// After
public void onTestStart(TestIdentifier identifier)
public void onTestSuccess(TestIdentifier identifier)
```

6. **Update internal maps**:
```java
// Before
private final ConcurrentHashMap<String, TestDetails> testDetailsMap = new ConcurrentHashMap<>();

// After
private final ConcurrentHashMap<TestIdentifier, TestDetails> testDetailsMap = new ConcurrentHashMap<>();
```

Migration strategy:
1. Create value objects with comprehensive tests
2. Add overloaded methods accepting both old and new types
3. Deprecate old methods
4. Update internal usage to new types
5. Update entry points (Extension/Listener) to use new types
6. Remove deprecated methods in next major version

This is a significant refactoring - consider if the benefits outweigh the changes required.
```

#### Acceptance Criteria
- [ ] Value objects created with validation
- [ ] Type safety improved throughout codebase
- [ ] Invalid states prevented at compile time
- [ ] All tests updated and passing
- [ ] Backwards compatibility maintained if needed
- [ ] Documentation updated
- [ ] Clear migration path for users

---

### CQ-3: Extract Long Parameter Lists to Context Objects

**Priority**: Low
**Complexity**: Simple
**Files**: `src/main/java/io/github/alexshamrai/suite/SuiteExecutionErrorHandler.java`

#### Prompt
```
Refactor long parameter lists in SuiteExecutionErrorHandler to use context objects.

Current issue (lines 34, 49):
```java
public Optional<Test> handleInitializationError(
    ExtensionContext context, long testRunStartTime, long testRunStopTime)

public Optional<Test> handleExecutionError(
    ExtensionContext context, long testRunStartTime, long testRunStopTime)
```

Both methods take the same three parameters, which should be encapsulated.

Create ErrorContext parameter object:
```java
package io.github.alexshamrai.suite;

/**
 * Context information for handling suite execution errors.
 * Contains the test execution context and timing information.
 */
public final class ErrorContext {
    private final ExtensionContext extensionContext;
    private final long testRunStartTime;
    private final long testRunStopTime;

    private ErrorContext(ExtensionContext extensionContext,
                        long testRunStartTime,
                        long testRunStopTime) {
        this.extensionContext = Objects.requireNonNull(extensionContext, "extensionContext");
        if (testRunStartTime < 0) {
            throw new IllegalArgumentException("testRunStartTime must be non-negative");
        }
        if (testRunStopTime < testRunStartTime) {
            throw new IllegalArgumentException("testRunStopTime must be >= testRunStartTime");
        }
        this.testRunStartTime = testRunStartTime;
        this.testRunStopTime = testRunStopTime;
    }

    public static ErrorContext of(ExtensionContext extensionContext,
                                 long testRunStartTime,
                                 long testRunStopTime) {
        return new ErrorContext(extensionContext, testRunStartTime, testRunStopTime);
    }

    public ExtensionContext getExtensionContext() {
        return extensionContext;
    }

    public long getTestRunStartTime() {
        return testRunStartTime;
    }

    public long getTestRunStopTime() {
        return testRunStopTime;
    }

    public long getDuration() {
        return testRunStopTime - testRunStartTime;
    }
}
```

Update SuiteExecutionErrorHandler:
```java
public Optional<Test> handleInitializationError(ErrorContext context) {
    return handleError(
        context.getExtensionContext(),
        context.getTestRunStartTime(),
        context.getTestRunStopTime(),
        "INITIALIZATION_ERROR"
    );
}

public Optional<Test> handleExecutionError(ErrorContext context) {
    return handleError(
        context.getExtensionContext(),
        context.getTestRunStartTime(),
        context.getTestRunStopTime(),
        "EXECUTION_ERROR"
    );
}
```

Update call sites in CtrfExtension:
```java
// Before
handler.handleInitializationError(context, testRunStartTime, testRunStopTime);

// After
ErrorContext errorContext = ErrorContext.of(context, testRunStartTime, testRunStopTime);
handler.handleInitializationError(errorContext);
```

Benefits:
- Fewer parameters to pass around
- Encapsulated validation in one place
- Easier to add new context fields in the future
- More readable method signatures
```

#### Acceptance Criteria
- [ ] ErrorContext class created with validation
- [ ] Method signatures simplified to single parameter
- [ ] All call sites updated
- [ ] Tests updated and passing
- [ ] Validation ensures valid time ranges
- [ ] Documentation updated

---

### CQ-4: Extract Magic Numbers and Strings to Constants

**Priority**: Low
**Complexity**: Simple
**Files**: `TestProcessor.java`, configuration files, multiple classes

#### Prompt
```
Extract magic numbers and strings throughout the codebase to named constants for better maintainability.

Current magic values:

1. **TestProcessor.java:22** - Truncation indicator
```java
? trace.substring(0, maxMessageLength) + "..."  // "..." is magic string
```

2. **Default configuration values** (scattered):
- 500 for maxMessageLength
- "ctrf-report.json" for default report path
- Test status strings: "passed", "failed", "skipped", etc.

3. **Environment variable names**:
- "ENV_HEALTHY"

Create constants class:
```java
package io.github.alexshamrai.constants;

/**
 * Application-wide constants for the CTRF reporter.
 */
public final class CtrfConstants {

    private CtrfConstants() {
        throw new UnsupportedOperationException("Constants class");
    }

    // File names and paths
    public static final String DEFAULT_REPORT_FILENAME = "ctrf-report.json";

    // Configuration defaults
    public static final int DEFAULT_MAX_MESSAGE_LENGTH = 500;
    public static final boolean DEFAULT_CALCULATE_STARTUP_DURATION = false;

    // Test status values (matching CTRF spec)
    public static final String STATUS_PASSED = "passed";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_SKIPPED = "skipped";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_OTHER = "other";

    // Error type identifiers
    public static final String ERROR_TYPE_INITIALIZATION = "INITIALIZATION_ERROR";
    public static final String ERROR_TYPE_EXECUTION = "EXECUTION_ERROR";

    // Environment variable names
    public static final String ENV_VAR_ENVIRONMENT_HEALTHY = "ENV_HEALTHY";

    // Message formatting
    public static final String MESSAGE_TRUNCATION_INDICATOR = "...";
    public static final String MESSAGE_NO_ERROR_MESSAGE = "No error message available";

    // File operation messages
    public static final String MSG_FILE_ALREADY_EXISTS = "File already exists, will be overwritten: %s";
    public static final String MSG_ERROR_READING_REPORT = "Error reading existing report: %s";
    public static final String MSG_ERROR_WRITING_REPORT = "Error writing report to file: %s";
}
```

Update usages:

1. **TestProcessor.java**:
```java
import static io.github.alexshamrai.constants.CtrfConstants.MESSAGE_TRUNCATION_INDICATOR;

// Before
? trace.substring(0, maxMessageLength) + "..."

// After
? trace.substring(0, maxMessageLength) + MESSAGE_TRUNCATION_INDICATOR
```

2. **Configuration interfaces**:
```java
@DefaultValue("${ctrf.report.path:ctrf-report.json}")

// After
import static io.github.alexshamrai.constants.CtrfConstants.DEFAULT_REPORT_FILENAME;

@DefaultValue("${ctrf.report.path:" + DEFAULT_REPORT_FILENAME + "}")
```

3. **EnvironmentHealthTracker**:
```java
// Before
System.getenv("ENV_HEALTHY")

// After
import static io.github.alexshamrai.constants.CtrfConstants.ENV_VAR_ENVIRONMENT_HEALTHY;
System.getenv(ENV_VAR_ENVIRONMENT_HEALTHY)
```

Requirements:
1. Create CtrfConstants class with all magic values
2. Update all usages to reference constants
3. Use static imports where it improves readability
4. Add javadoc explaining each constant
5. Group related constants together
6. Verify all tests still pass
7. Update documentation
```

#### Acceptance Criteria
- [ ] CtrfConstants class created with comprehensive constants
- [ ] No magic numbers in code (except 0, 1, -1 in obvious contexts)
- [ ] No magic strings in code
- [ ] All usages updated to reference constants
- [ ] Constants are well-documented
- [ ] All tests pass
- [ ] Code is more maintainable

---

## Testing Improvements

### TI-1: Add Comprehensive Concurrency Tests

**Priority**: High
**Complexity**: Complex
**Files**: New test file `src/test/java/io/github/alexshamrai/CtrfReportManagerConcurrencyTest.java`

#### Prompt
```
Create comprehensive tests to verify thread safety of CtrfReportManager under concurrent access.

Current gap:
No tests verify behavior when multiple threads simultaneously:
- Add test results
- Finish test runs
- Access shared state

Create comprehensive concurrency test suite:

```java
package io.github.alexshamrai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for thread safety and concurrent access to CtrfReportManager.
 */
class CtrfReportManagerConcurrencyTest {

    @RepeatedTest(10) // Repeat to catch intermittent race conditions
    void testConcurrentTestResultAddition() throws Exception {
        // Arrange
        CtrfReportManager manager = createManager();
        int numThreads = 10;
        int testsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        // Act - All threads add test results simultaneously
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    for (int j = 0; j < testsPerThread; j++) {
                        String uniqueId = "test-" + threadId + "-" + j;
                        manager.onTestStart(uniqueId);
                        manager.onTestSuccess(uniqueId);
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);

        // Assert
        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(numThreads * testsPerThread);

        manager.finishTestRun();
        // Verify report contains all tests

        executor.shutdown();
    }

    @Test
    void testConcurrentFinishTestRun() throws Exception {
        // Test that only one finishTestRun actually executes
        CtrfReportManager manager = createManager();

        // Add some test results
        manager.onTestStart("test-1");
        manager.onTestSuccess("test-1");

        // Try to finish from multiple threads
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger executionCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    manager.finishTestRun();
                    executionCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Verify finishTestRun was called by all threads but only executed once
        assertThat(executionCount.get()).isEqualTo(numThreads);
        // Verify report was written only once (check file system)
    }

    @Test
    void testRaceConditionBetweenAddAndFinish() throws Exception {
        // Test the race condition identified in CB-2
        CtrfReportManager manager = createManager();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger testsAdded = new AtomicInteger(0);

        // Thread 1: Keep adding tests
        executor.submit(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 100; i++) {
                    String uniqueId = "test-" + i;
                    manager.onTestStart(uniqueId);
                    manager.onTestSuccess(uniqueId);
                    testsAdded.incrementAndGet();
                    Thread.sleep(1); // Small delay
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Thread 2: Try to finish in the middle
        executor.submit(() -> {
            try {
                startLatch.await();
                Thread.sleep(50); // Let some tests accumulate
                manager.finishTestRun();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        startLatch.countDown();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // Verify all tests that were added before finishTestRun are in the report
        // This test may fail if CB-2 is not fixed
    }

    @Test
    void testConcurrentEnvironmentHealthAccess() throws Exception {
        // Test concurrent access to environment health tracking
        CtrfReportManager manager = createManager();
        int numThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        // Half threads mark unhealthy, half check status
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (threadId % 2 == 0) {
                        EnvironmentHealthTracker.markEnvironmentUnhealthy();
                    } else {
                        boolean healthy = EnvironmentHealthTracker.isEnvironmentHealthy();
                        // Just read, don't assert (will be false after first mark)
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(EnvironmentHealthTracker.isEnvironmentHealthy()).isFalse();

        executor.shutdown();
    }

    @Test
    void testHighVolumeParallelExecution() {
        // Simulate real-world scenario with thousands of tests in parallel
        CtrfReportManager manager = createManager();
        int numTests = 10000;

        // Use parallel stream to simulate parallel test execution
        long start = System.currentTimeMillis();

        IntStream.range(0, numTests).parallel().forEach(i -> {
            String uniqueId = "test-" + i;
            manager.onTestStart(uniqueId);

            // Simulate test execution
            if (i % 10 == 0) {
                manager.onTestFailure(uniqueId, new AssertionError("Test failed"));
            } else if (i % 5 == 0) {
                manager.onTestAborted(uniqueId, new RuntimeException("Test aborted"));
            } else {
                manager.onTestSuccess(uniqueId);
            }
        });

        manager.finishTestRun();
        long duration = System.currentTimeMillis() - start;

        System.out.println("Processed " + numTests + " tests in " + duration + "ms");

        // Verify report correctness
        // All 10000 tests should be in report
        // Correct counts for passed/failed/skipped
    }

    private CtrfReportManager createManager() {
        // Create manager with test configuration
        return new CtrfReportManager();
    }
}
```

Additional requirements:
1. Use @RepeatedTest to catch intermittent issues
2. Test with different thread pool sizes
3. Test under high load (1000+ tests)
4. Verify no data loss
5. Verify no duplicate results
6. Verify atomic operations work correctly
7. Use Thread.sleep() strategically to expose race conditions
8. Check for deadlocks with timeout assertions
9. Profile performance under concurrent load

Tools to use:
- ExecutorService for thread pools
- CountDownLatch for synchronization
- AtomicInteger for counting
- @RepeatedTest for flakiness detection
- AssertJ for fluent assertions
```

#### Acceptance Criteria
- [ ] Comprehensive concurrency test suite created
- [ ] Tests cover all concurrent access patterns
- [ ] Tests reliably detect race conditions
- [ ] Tests pass consistently (run 100 times)
- [ ] High-volume test verifies performance
- [ ] Tests document expected thread-safe behavior
- [ ] Any race conditions discovered are documented

---

### TI-2: Add File System Error Tests

**Priority**: Medium
**Complexity**: Moderate
**Files**: `src/test/java/io/github/alexshamrai/CtrfReportFileServiceTest.java`

#### Prompt
```
Add comprehensive tests for file system errors and edge cases in CtrfReportFileService.

Current gap:
Tests don't cover error scenarios like:
- Unwritable directories
- Disk full
- File locked by another process
- Corrupted JSON files
- Permission errors

Add test cases:

```java
@Test
void testWriteToUnwritableDirectory() {
    // Arrange
    Path readOnlyDir = Files.createTempDirectory("readonly");
    readOnlyDir.toFile().setWritable(false);
    Path reportPath = readOnlyDir.resolve("report.json");

    CtrfReportFileService service = new CtrfReportFileService(
        configWithPath(reportPath.toString())
    );

    // Act & Assert
    CtrfJson report = createValidReport();

    // Should log error but not throw exception
    service.writeReportToFile(report);

    // Cleanup
    readOnlyDir.toFile().setWritable(true);
}

@Test
void testReadCorruptedJsonFile() throws IOException {
    // Arrange
    Path reportPath = tempDir.resolve("corrupted.json");
    Files.writeString(reportPath, "{invalid json}]}");

    CtrfReportFileService service = new CtrfReportFileService(
        configWithPath(reportPath.toString())
    );

    // Act
    List<Test> tests = service.getExistingTests();

    // Assert - Should return empty list, not throw
    assertThat(tests).isEmpty();
}

@Test
void testReadTruncatedJsonFile() throws IOException {
    // Arrange - Simulate file truncated mid-write
    Path reportPath = tempDir.resolve("truncated.json");
    String validJson = "{\"results\":{\"tests\":[{\"name\":\"test1\"}";
    Files.writeString(reportPath, validJson);

    CtrfReportFileService service = new CtrfReportFileService(
        configWithPath(reportPath.toString())
    );

    // Act
    CtrfJson report = service.readExistingReport();

    // Assert
    assertThat(report).isNull();
}

@Test
void testWriteLargeReport() {
    // Test with report containing 10,000 tests
    CtrfJson largeReport = createReportWithTests(10_000);

    CtrfReportFileService service = new CtrfReportFileService(configReader);

    // Should complete without error
    assertThatCode(() -> service.writeReportToFile(largeReport))
        .doesNotThrowAnyException();

    // Verify file can be read back
    CtrfJson readBack = service.readExistingReport();
    assertThat(readBack.getResults().getTests()).hasSize(10_000);
}

@Test
void testConcurrentWriteAttempts() throws Exception {
    // Test what happens if two processes try to write simultaneously
    // (Difficult to test reliably, but document behavior)
}

@Test
void testWriteWithSpecialCharactersInPath() {
    // Test paths with spaces, unicode, etc.
    String pathWithSpaces = tempDir.resolve("report with spaces.json").toString();
    CtrfReportFileService service = new CtrfReportFileService(
        configWithPath(pathWithSpaces)
    );

    CtrfJson report = createValidReport();
    service.writeReportToFile(report);

    assertThat(Paths.get(pathWithSpaces)).exists();
}

@Test
void testReadFileWithBOM() throws IOException {
    // Test reading file with UTF-8 BOM (byte order mark)
    Path reportPath = tempDir.resolve("with-bom.json");
    byte[] bom = new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF};
    byte[] jsonBytes = validJsonString.getBytes(StandardCharsets.UTF_8);
    byte[] withBom = new byte[bom.length + jsonBytes.length];
    System.arraycopy(bom, 0, withBom, 0, bom.length);
    System.arraycopy(jsonBytes, 0, withBom, bom.length, jsonBytes.length);
    Files.write(reportPath, withBom);

    CtrfReportFileService service = new CtrfReportFileService(
        configWithPath(reportPath.toString())
    );

    CtrfJson report = service.readExistingReport();
    assertThat(report).isNotNull();
}
```

Use mocking for some scenarios:
```java
@Test
void testIOExceptionDuringWrite() {
    // Mock ObjectMapper to throw IOException
    ObjectMapper mockMapper = mock(ObjectMapper.class);
    when(mockMapper.writeValue(any(File.class), any()))
        .thenThrow(new IOException("Simulated IO error"));

    // Inject mock and verify error handling
}
```

Requirements:
1. Test all error paths in CtrfReportFileService
2. Verify graceful error handling
3. Test edge cases (empty files, huge files, special characters)
4. Test concurrent access where possible
5. Use temp directories for all file operations
6. Clean up test files in @AfterEach
7. Document expected behavior in comments
```

#### Acceptance Criteria
- [ ] All file system error scenarios tested
- [ ] Edge cases covered
- [ ] Tests use temp directories
- [ ] No files left after tests
- [ ] Error handling verified
- [ ] Large file performance tested
- [ ] Special characters in paths tested
- [ ] Documentation explains expected behavior

---

### TI-3: Improve Test Isolation in EnvironmentHealthTrackerTest

**Priority**: Medium
**Complexity**: Moderate
**Files**: `src/test/java/io/github/alexshamrai/EnvironmentHealthTrackerTest.java`

#### Prompt
```
Improve test isolation in EnvironmentHealthTrackerTest to avoid dependency on global singleton state.

Current issue:
Tests depend on global singleton state with cleanup in @BeforeEach and @AfterEach:
- Tests are not truly isolated
- Test order could matter
- Parallel execution problematic
- Fragile cleanup

Current approach:
```java
@BeforeEach
void setUp() {
    CtrfReportManager.getInstance(); // Reset via singleton
}

@AfterEach
void tearDown() {
    // Cleanup somehow
}
```

Recommended approach 1: Use package-private constructor
```java
@Test
void testEnvironmentHealthTracking() {
    // Create isolated instance for this test
    CtrfReportFileService fileService = createMockFileService();
    CtrfReportManager manager = new CtrfReportManager(
        fileService,
        new CtrfJsonComposer(...),
        ...
    );

    // Test with this isolated instance
    assertThat(manager.isEnvironmentHealthyInternal()).isTrue();
    manager.markEnvironmentUnhealthyInternal();
    assertThat(manager.isEnvironmentHealthyInternal()).isFalse();

    // No cleanup needed - instance is garbage collected
}
```

Recommended approach 2: Add reset method for testing
```java
// In EnvironmentHealthTracker
@VisibleForTesting
static void resetForTesting() {
    CtrfReportManager.getInstance().resetEnvironmentHealth();
}

// In test
@BeforeEach
void setUp() {
    EnvironmentHealthTracker.resetForTesting();
}

@Test
void testSomething() {
    // Test with clean state
}
```

Recommended approach 3: Extract interface for testability
```java
public interface EnvironmentHealthChecker {
    boolean isHealthy();
    void markUnhealthy();
}

public class CtrfReportManager implements EnvironmentHealthChecker {
    // Implementation
}

// In tests, use mock implementation
@Test
void testWithMockHealth() {
    EnvironmentHealthChecker mockChecker = mock(EnvironmentHealthChecker.class);
    when(mockChecker.isHealthy()).thenReturn(false);

    // Use mock in test
}
```

Requirements:
1. Choose best approach (recommend #1 with package-private constructor)
2. Update all tests to use isolated instances
3. Remove @BeforeEach and @AfterEach cleanup
4. Verify tests can run in parallel
5. Verify tests can run in any order
6. Add test to verify isolation works
7. Document testing approach in javadoc

Benefits:
- True test isolation
- Can run tests in parallel
- No order dependencies
- No cleanup needed
- More reliable tests
- Follows testing best practices
```

#### Acceptance Criteria
- [ ] Tests use isolated instances
- [ ] No shared state between tests
- [ ] Tests can run in parallel
- [ ] Tests pass in random order
- [ ] No cleanup code needed
- [ ] Tests are simpler and more reliable
- [ ] Documentation explains testing approach

---

## Documentation Improvements

### DI-1: Add Comprehensive Javadoc

**Priority**: Medium
**Complexity**: Moderate
**Files**: All public classes and methods

#### Prompt
```
Add comprehensive Javadoc documentation to all public APIs.

Current state:
- 56 public methods across 14 files
- Only 31 @param, @return, @throws annotations
- Missing documentation for CtrfReportManager public methods
- No package-level documentation

Requirements for each public class:
1. Class-level javadoc with:
   - Brief description (one line)
   - Detailed explanation of purpose
   - Usage examples
   - Thread safety guarantees
   - @since tag
   - @author tag (optional)

2. Method-level javadoc with:
   - Brief description
   - @param for each parameter
   - @return for non-void methods
   - @throws for checked and important unchecked exceptions
   - Usage examples for complex methods
   - Thread safety notes if relevant

3. Package-level documentation:
   - Create package-info.java in each package
   - Explain package purpose
   - List main classes
   - Explain relationships

Example for CtrfReportManager:
```java
package io.github.alexshamrai;

/**
 * Central coordinator for CTRF test report generation.
 *
 * <p>This singleton class manages the lifecycle of test execution reporting,
 * collecting test results from JUnit callbacks and orchestrating the generation
 * of CTRF-compliant JSON reports.
 *
 * <h2>Thread Safety</h2>
 * This class is thread-safe and designed for concurrent test execution. Test results
 * can be reported from multiple threads simultaneously. The {@link #finishTestRun()}
 * method uses atomic operations to ensure report generation occurs exactly once.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * CtrfReportManager manager = CtrfReportManager.getInstance();
 *
 * // In test callbacks:
 * manager.onTestStart("test-id");
 * manager.onTestSuccess("test-id");
 *
 * // After all tests complete:
 * manager.finishTestRun();
 * }</pre>
 *
 * <h2>Flaky Test Detection</h2>
 * Tests are automatically marked as flaky if they pass after previous failures
 * (retries > 0) or if multiple executions have different outcomes.
 *
 * @since 0.1.0
 * @see CtrfExtension
 * @see CtrfListener
 * @see EnvironmentHealthTracker
 */
public class CtrfReportManager {

    /**
     * Retrieves the singleton instance of the report manager.
     *
     * <p>The instance is eagerly initialized and thread-safe.
     *
     * @return the singleton instance, never {@code null}
     */
    public static CtrfReportManager getInstance() {
        return INSTANCE;
    }

    /**
     * Records the start of test execution.
     *
     * <p>This method should be called when a test begins execution, before
     * any test logic runs. It initializes tracking state for the test.
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe and can be
     * called concurrently from multiple test threads.
     *
     * @param uniqueId the unique identifier for the test, must not be {@code null} or blank
     * @throws IllegalArgumentException if uniqueId is {@code null} or blank
     */
    public void onTestStart(String uniqueId) {
        // Implementation
    }

    /**
     * Records a successful test execution.
     *
     * <p>This method should be called after a test completes successfully.
     * The test result will be included in the final report with status "passed".
     *
     * <p><strong>Thread Safety:</strong> This method is thread-safe and can be
     * called concurrently from multiple test threads.
     *
     * @param uniqueId the unique identifier for the test, must match a previous
     *                 {@link #onTestStart(String)} call
     * @throws IllegalArgumentException if uniqueId is {@code null} or blank
     * @throws IllegalStateException if no matching test start was recorded
     */
    public void onTestSuccess(String uniqueId) {
        // Implementation
    }

    /**
     * Finalizes report generation and writes the CTRF JSON file.
     *
     * <p>This method should be called exactly once after all tests complete.
     * It performs the following operations:
     * <ol>
     *   <li>Detects flaky tests based on retry count and execution history</li>
     *   <li>Merges results with previous test runs if applicable</li>
     *   <li>Composes the CTRF JSON structure with metadata</li>
     *   <li>Writes the report to the configured file path</li>
     *   <li>Clears internal test state</li>
     * </ol>
     *
     * <p><strong>Thread Safety:</strong> This method uses atomic operations to
     * ensure it executes exactly once, even if called from multiple threads.
     * Subsequent calls are safely ignored.
     *
     * <p><strong>Important:</strong> This method must be called after all test
     * execution callbacks ({@link #onTestSuccess}, {@link #onTestFailure}, etc.)
     * have completed to ensure all test results are included in the report.
     *
     * @throws IllegalStateException if called before {@link #startTestRun()}
     */
    public void finishTestRun() {
        // Implementation
    }
}
```

Package-info.java example:
```java
/**
 * Core components of the CTRF test report generator.
 *
 * <p>This package contains the main classes responsible for collecting test
 * execution data and generating CTRF-compliant JSON reports.
 *
 * <h2>Main Classes</h2>
 * <ul>
 *   <li>{@link io.github.alexshamrai.CtrfReportManager} - Central coordinator</li>
 *   <li>{@link io.github.alexshamrai.CtrfReportFileService} - File I/O operations</li>
 *   <li>{@link io.github.alexshamrai.CtrfJsonComposer} - JSON structure assembly</li>
 *   <li>{@link io.github.alexshamrai.TestProcessor} - Test result processing</li>
 * </ul>
 *
 * <h2>Integration Points</h2>
 * <p>This package is used by:
 * <ul>
 *   <li>{@link io.github.alexshamrai.jupiter.CtrfExtension} - JUnit Jupiter Extension</li>
 *   <li>{@link io.github.alexshamrai.launcher.CtrfListener} - JUnit Platform Listener</li>
 * </ul>
 *
 * @since 0.1.0
 */
package io.github.alexshamrai;
```

Files requiring documentation:
1. CtrfReportManager - all public methods
2. EnvironmentHealthTracker - all public methods
3. CtrfReportFileService - all public methods
4. CtrfExtension - class and callbacks
5. CtrfListener - class and callbacks
6. All model classes - class level
7. All utility classes
8. Create package-info.java for each package

Guidelines:
- Use present tense ("Returns" not "Will return")
- Be concise but complete
- Include examples for complex functionality
- Document thread safety guarantees
- Document null handling
- Use {@code} for code elements
- Use {@link} for cross-references
- Use <p> for paragraph breaks
- Use <ul>/<ol> for lists
- Use @throws for exceptions
```

#### Acceptance Criteria
- [ ] All public classes have class-level javadoc
- [ ] All public methods have complete javadoc
- [ ] All @param, @return, @throws documented
- [ ] Package-info.java created for each package
- [ ] Thread safety documented where relevant
- [ ] Examples provided for complex APIs
- [ ] Javadoc builds without warnings
- [ ] Documentation is accurate and helpful

---

### DI-2: Add Architecture Documentation

**Priority**: Medium
**Complexity**: Moderate
**Files**: New file `ARCHITECTURE.md`

#### Prompt
```
Create comprehensive architecture documentation explaining the design and implementation of the library.

Create ARCHITECTURE.md with the following sections:

# Architecture Documentation

## Overview
High-level description of the library's purpose and architecture

## Design Principles
- Single Responsibility Principle
- Thread Safety
- Extensibility
- etc.

## Component Diagram
```
[CtrfExtension] ────┐
                    ├──→ [CtrfReportManager] ──→ [CtrfJsonComposer]
[CtrfListener] ─────┘              │                     │
                                   │                     ↓
                                   ↓              [CtrfReportFileService]
                            [TestProcessor]              │
                                                         ↓
                                                   [JSON File]
```

## Core Components

### CtrfReportManager
- Role: Central coordinator
- Responsibilities:
  - Test lifecycle management
  - State tracking
  - Report orchestration
- Thread Safety: Uses concurrent collections
- Singleton Pattern: Why and how

### Entry Points

#### CtrfExtension
- JUnit Jupiter Extension
- Lifecycle hooks
- When to use

#### CtrfListener
- JUnit Platform Listener
- Registration methods
- When to use

### Data Flow

Detailed sequence diagram showing:
1. Test starts
2. Extension/Listener notified
3. Manager records state
4. Test completes
5. Manager updates result
6. All tests complete
7. Report generated
8. File written

### Concurrency Model

Explain thread safety approach:
- CopyOnWriteArrayList for tests
- ConcurrentHashMap for test details
- AtomicBoolean for flags
- Why no explicit locks
- Race condition considerations

### Configuration

- Configuration loading priority
- Owner library integration
- System properties vs files
- Extension points

### Error Handling Strategy

- Where errors are caught
- How errors are reported
- Graceful degradation
- User notification

## Extension Points

How to extend the library:
- Custom metadata
- Alternative storage
- Custom test status mapping

## Design Decisions

Document key decisions and rationale:

### Why Singleton?
Pros, cons, alternatives considered

### Why Two Entry Points?
Extension vs Listener tradeoffs

### Why Lombok?
Benefits and drawbacks

### Why CopyOnWriteArrayList?
Performance considerations

## Testing Strategy

- Unit test approach
- Integration test approach
- Concurrency testing
- Test isolation techniques

## Performance Considerations

- Memory usage
- CPU usage
- File I/O impact
- Large test suite handling
- Benchmarks

## Future Enhancements

- Planned improvements
- Extension possibilities
- API evolution

## Glossary

Define terms:
- CTRF
- Flaky test
- Test rerun
- Environment health
etc.

Requirements:
1. Include sequence diagrams (can use ASCII art or mermaid.js)
2. Include component diagrams
3. Explain all design patterns used
4. Document threading model clearly
5. Explain all major design decisions
6. Link to relevant code locations
7. Keep diagrams up to date with code
```

#### Acceptance Criteria
- [ ] ARCHITECTURE.md created
- [ ] All major components documented
- [ ] Diagrams explain data flow
- [ ] Threading model explained clearly
- [ ] Design decisions documented with rationale
- [ ] Extension points documented
- [ ] Testing strategy explained
- [ ] Document is kept up to date

---

## Security & Performance

### SP-1: Add Path Validation for Security

**Priority**: Low
**Complexity**: Simple
**Files**: `src/main/java/io/github/alexshamrai/CtrfReportFileService.java`

#### Prompt
```
Add path validation to prevent path traversal vulnerabilities in report file path configuration.

Current issue:
No validation of the report path. Malicious configuration could write to arbitrary locations:
- ../../../../etc/passwd
- /tmp/malicious.json
- C:\Windows\System32\config\sam

While this is configuration-controlled (not user input), it's still a security risk in
shared environments or CI/CD pipelines.

Implementation:

1. **Create PathValidator utility**:
```java
package io.github.alexshamrai.validation;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

/**
 * Validates file paths for security and accessibility.
 */
public class PathValidator {

    /**
     * Validates that a path is safe for writing report files.
     *
     * <p>Checks performed:
     * <ul>
     *   <li>Path is not absolute outside current working directory (configurable)</li>
     *   <li>Path does not contain ".." traversal</li>
     *   <li>Parent directory exists or can be created</li>
     *   <li>Parent directory is writable</li>
     *   <li>Filename has valid extension</li>
     * </ul>
     *
     * @param pathString the path to validate
     * @param allowAbsolutePaths whether to allow absolute paths
     * @return validated Path object
     * @throws SecurityException if path is unsafe
     * @throws IllegalArgumentException if path is invalid
     */
    public static Path validateReportPath(String pathString, boolean allowAbsolutePaths) {
        if (pathString == null || pathString.isBlank()) {
            throw new IllegalArgumentException("Report path must not be blank");
        }

        Path path = Paths.get(pathString);
        Path normalizedPath = path.normalize();

        // Check for path traversal
        if (normalizedPath.toString().contains("..")) {
            throw new SecurityException(
                "Report path contains illegal '..' traversal: " + pathString);
        }

        // Check absolute paths if not allowed
        if (!allowAbsolutePaths && path.isAbsolute()) {
            // Allow absolute paths only within project directory
            Path currentDir = Paths.get("").toAbsolutePath();
            try {
                Path resolved = currentDir.resolve(normalizedPath).normalize();
                if (!resolved.startsWith(currentDir)) {
                    throw new SecurityException(
                        "Absolute report path outside project directory: " + pathString);
                }
            } catch (Exception e) {
                throw new SecurityException("Invalid report path: " + pathString, e);
            }
        }

        // Validate parent directory
        Path parentDir = normalizedPath.getParent();
        if (parentDir != null) {
            if (!Files.exists(parentDir)) {
                // Try to create parent directories
                try {
                    Files.createDirectories(parentDir);
                } catch (IOException e) {
                    throw new IllegalArgumentException(
                        "Cannot create parent directory: " + parentDir, e);
                }
            }

            if (!Files.isWritable(parentDir)) {
                throw new IllegalArgumentException(
                    "Parent directory is not writable: " + parentDir);
            }
        }

        // Validate filename
        String filename = normalizedPath.getFileName().toString();
        if (!filename.endsWith(".json")) {
            // Warning, not error (allow other extensions but log)
            System.err.println("Warning: Report file does not have .json extension: " + filename);
        }

        return normalizedPath;
    }

    /**
     * Validates with default settings (allow absolute paths).
     */
    public static Path validateReportPath(String pathString) {
        return validateReportPath(pathString, true);
    }
}
```

2. **Update CtrfReportFileService constructor**:
```java
public CtrfReportFileService(ConfigReader configReader) {
    this.configReader = configReader;

    // Validate path on construction
    String configuredPath = configReader.getReportPath();
    this.validatedPath = PathValidator.validateReportPath(configuredPath);
}
```

3. **Add configuration option**:
```java
// In CtrfConfig interface
@Key("ctrf.validate.report.path")
@DefaultValue("true")
boolean validateReportPath();

@Key("ctrf.allow.absolute.paths")
@DefaultValue("true")
boolean allowAbsolutePaths();
```

4. **Add tests**:
```java
@Test
void testPathTraversalBlocked() {
    assertThatThrownBy(() ->
        PathValidator.validateReportPath("../../../../etc/passwd"))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("path traversal");
}

@Test
void testAbsolutePathOutsideProjectBlocked() {
    assertThatThrownBy(() ->
        PathValidator.validateReportPath("/tmp/report.json", false))
        .isInstanceOf(SecurityException.class);
}

@Test
void testValidRelativePath() {
    Path validated = PathValidator.validateReportPath("build/ctrf-report.json");
    assertThat(validated).isNotNull();
}

@Test
void testValidAbsolutePathInProject() {
    Path projectPath = Paths.get("").toAbsolutePath();
    String absolutePath = projectPath.resolve("report.json").toString();

    Path validated = PathValidator.validateReportPath(absolutePath);
    assertThat(validated).isNotNull();
}
```

5. **Document security considerations**:
- Add section to README about path configuration
- Document in CLAUDE.md
- Add javadoc warnings

Requirements:
1. Implement PathValidator with comprehensive checks
2. Integrate into CtrfReportFileService
3. Add configuration options
4. Add comprehensive tests for all scenarios
5. Document security behavior
6. Consider backward compatibility (don't break existing valid paths)
```

#### Acceptance Criteria
- [ ] PathValidator created with security checks
- [ ] Path traversal attempts blocked
- [ ] Dangerous absolute paths blocked or restricted
- [ ] Valid paths work correctly
- [ ] Configuration options added
- [ ] Comprehensive security tests
- [ ] Documentation explains security model
- [ ] Backward compatibility maintained
- [ ] No false positives on valid paths

---

### SP-2: Performance Benchmarking Suite

**Priority**: Low
**Complexity**: Moderate
**Files**: New file `src/test/java/io/github/alexshamrai/benchmark/PerformanceBenchmarks.java`

#### Prompt
```
Create a performance benchmarking suite to measure and track performance characteristics.

Goals:
1. Establish performance baselines
2. Detect performance regressions
3. Identify bottlenecks
4. Guide optimization efforts

Create comprehensive benchmarks:

```java
package io.github.alexshamrai.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

/**
 * Performance benchmarks for the CTRF reporter.
 *
 * <p>These tests measure performance characteristics and are disabled by default.
 * Enable with: -DenableBenchmarks=true
 *
 * <p>Results should be tracked over time to detect regressions.
 */
@Disabled("Enable manually for benchmarking")
class PerformanceBenchmarks {

    @Test
    void benchmarkTestResultCollection() {
        // Measure overhead of recording test results
        int[] testCounts = {100, 1000, 5000, 10000};

        for (int numTests : testCounts) {
            CtrfReportManager manager = new CtrfReportManager();

            long startTime = System.nanoTime();
            long startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            for (int i = 0; i < numTests; i++) {
                String id = "test-" + i;
                manager.onTestStart(id);
                manager.onTestSuccess(id);
            }

            long endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long endTime = System.nanoTime();

            long durationMs = (endTime - startTime) / 1_000_000;
            long memoryUsedMb = (endMemory - startMemory) / (1024 * 1024);
            double perTestUs = (endTime - startTime) / 1000.0 / numTests;

            System.out.printf("%d tests: %d ms (%.2f μs/test), Memory: %d MB%n",
                numTests, durationMs, perTestUs, memoryUsedMb);
        }
    }

    @Test
    void benchmarkReportGeneration() {
        // Measure time to generate report
        int[] testCounts = {100, 1000, 5000, 10000};

        for (int numTests : testCounts) {
            CtrfReportManager manager = new CtrfReportManager();

            // Populate with test results
            for (int i = 0; i < numTests; i++) {
                String id = "test-" + i;
                manager.onTestStart(id);
                if (i % 10 == 0) {
                    manager.onTestFailure(id, new AssertionError("Failed"));
                } else {
                    manager.onTestSuccess(id);
                }
            }

            // Measure report generation
            long startTime = System.nanoTime();
            manager.finishTestRun();
            long endTime = System.nanoTime();

            long durationMs = (endTime - startTime) / 1_000_000;

            System.out.printf("%d tests: Report generation took %d ms%n",
                numTests, durationMs);
        }
    }

    @Test
    void benchmarkFileWritePerformance() {
        // Measure file I/O performance
        int[] testCounts = {100, 1000, 5000, 10000};

        for (int numTests : testCounts) {
            CtrfJson report = createReportWithTests(numTests);
            Path tempFile = Files.createTempFile("benchmark-", ".json");

            CtrfReportFileService service = new CtrfReportFileService(configReader);

            long startTime = System.nanoTime();
            service.writeReportToFile(report);
            long endTime = System.nanoTime();

            long fileSizeKb = Files.size(tempFile) / 1024;
            long durationMs = (endTime - startTime) / 1_000_000;

            System.out.printf("%d tests: Write took %d ms, File size: %d KB%n",
                numTests, durationMs, fileSizeKb);

            Files.delete(tempFile);
        }
    }

    @Test
    void benchmarkConcurrentExecution() {
        // Measure performance under concurrent load
        int numThreads = Runtime.getRuntime().availableProcessors();
        int testsPerThread = 1000;

        CtrfReportManager manager = new CtrfReportManager();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);

        long startTime = System.nanoTime();

        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int i = 0; i < testsPerThread; i++) {
                        String id = "test-" + threadId + "-" + i;
                        manager.onTestStart(id);
                        manager.onTestSuccess(id);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        long endTime = System.nanoTime();

        executor.shutdown();

        int totalTests = numThreads * testsPerThread;
        long durationMs = (endTime - startTime) / 1_000_000;
        double throughput = totalTests * 1000.0 / durationMs;

        System.out.printf("%d threads × %d tests = %d total: %d ms (%.0f tests/sec)%n",
            numThreads, testsPerThread, totalTests, durationMs, throughput);
    }

    @Test
    void benchmarkFlakyTestDetection() {
        // Measure cost of flaky test detection algorithm
        int numTests = 1000;
        int numReruns = 10;

        List<Test> tests = new ArrayList<>();
        for (int i = 0; i < numTests; i++) {
            for (int r = 0; r < numReruns; r++) {
                Test test = Test.builder()
                    .name("test-" + i)
                    .status(r % 2 == 0 ? "passed" : "failed")
                    .build();
                tests.add(test);
            }
        }

        CtrfReportManager manager = new CtrfReportManager();

        long startTime = System.nanoTime();
        // Call flaky detection method
        long endTime = System.nanoTime();

        long durationMs = (endTime - startTime) / 1_000_000;

        System.out.printf("Flaky detection for %d tests × %d reruns: %d ms%n",
            numTests, numReruns, durationMs);
    }

    @Test
    void benchmarkCollectionPerformance() {
        // Compare CopyOnWriteArrayList vs alternatives
        int numTests = 10000;

        // Benchmark CopyOnWriteArrayList
        long cowTime = benchmarkCollection(new CopyOnWriteArrayList<>(), numTests);

        // Benchmark synchronized ArrayList
        long syncTime = benchmarkCollection(
            Collections.synchronizedList(new ArrayList<>()), numTests);

        // Benchmark ConcurrentLinkedQueue
        Queue<Test> queue = new ConcurrentLinkedQueue<>();
        long queueTime = benchmarkQueue(queue, numTests);

        System.out.printf("CopyOnWrite: %d ms%n", cowTime);
        System.out.printf("Synchronized: %d ms%n", syncTime);
        System.out.printf("Queue: %d ms%n", queueTime);
    }

    private long benchmarkCollection(List<Test> list, int numTests) {
        long startTime = System.nanoTime();
        for (int i = 0; i < numTests; i++) {
            list.add(createTestResult());
        }
        long endTime = System.nanoTime();
        return (endTime - startTime) / 1_000_000;
    }
}
```

Additional requirements:
1. Create benchmark runner script
2. Document how to run benchmarks
3. Set up CI job to track performance over time
4. Create performance regression tests (fail if > X% slower)
5. Generate performance reports
6. Track results in CSV/JSON for trend analysis

Output format:
```
=== CTRF Reporter Performance Benchmarks ===
Date: 2025-01-15
JVM: OpenJDK 17.0.2
OS: Linux 5.15.0

Test Result Collection:
  100 tests: 5 ms (50 μs/test), Memory: 1 MB
  1000 tests: 45 ms (45 μs/test), Memory: 8 MB
  5000 tests: 225 ms (45 μs/test), Memory: 35 MB
  10000 tests: 450 ms (45 μs/test), Memory: 70 MB

Report Generation:
  100 tests: 25 ms
  1000 tests: 180 ms
  5000 tests: 850 ms
  10000 tests: 1700 ms

... etc
```
```

#### Acceptance Criteria
- [ ] Comprehensive benchmark suite created
- [ ] Benchmarks cover all major operations
- [ ] Results are reproducible
- [ ] Benchmarks documented
- [ ] CI integration planned
- [ ] Performance baselines established
- [ ] Regression detection implemented
- [ ] Reports are easy to understand

---

## Implementation Order

For best results, implement in this order:

### Phase 1: Critical Fixes (Week 1)
1. CB-1: Fix NullPointerException
2. CB-2: Fix race condition
3. CB-3: Fix ConfigReader duplication

### Phase 2: High Priority Refactoring (Weeks 2-3)
4. HP-3: Implement logging
5. HP-4: Fix TestDetailsUtil duplication
6. HP-1: Refactor singleton pattern
7. HP-2: Break up god class

### Phase 3: Quality Improvements (Weeks 4-5)
8. MP-1: Optimize SummaryUtil
9. MP-2: Cache file reads
10. MP-4: Add validation
11. TI-1: Add concurrency tests
12. DI-1: Add javadoc

### Phase 4: Polish (Week 6)
13. Remaining medium priority items
14. Documentation improvements
15. Performance benchmarks
16. Security hardening

---

## Notes

- Each improvement can be implemented independently
- Tests should be added/updated for each change
- Document breaking changes in CHANGELOG
- Consider semantic versioning for releases
- Keep CLAUDE.md updated with architectural changes

---

Generated: 2025-01-20
Review Date: TBD