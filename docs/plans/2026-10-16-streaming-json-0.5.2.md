# 0.5.2: Streaming JSON — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship patch release 0.5.2 on one branch, `fix/streaming-json`, with five commits, one per task. Writing and reading a large report again needs no more heap than 0.5.0, and a rerun that replaces another run's report needs no more than a first write.

**Architecture:**
- Task 1 adds `JsonReader`, a tokenizer over a `java.io.Reader` with its own 8 KB buffer. `JsonParser.parse(String)` becomes a wrapper over it.
- Task 2 makes `CtrfJsonMapper.read(Reader, Class)` bind tokens straight into the model, and the file service reads through a `BufferedReader`.
- Task 3 turns `JsonWriter` into an object over a `Writer`, makes `CtrfJsonMapper.write(Object, Writer)` write the model in one pass, and the file service writes through a `BufferedWriter`.
- Task 4 adds `CtrfJsonMapper.readRunId(Reader)` and `CtrfReportFileService.update(String runId, UnaryOperator)`, which reads a report whole only when it belongs to the run. The manager calls it.
- Task 5 adds a heap guard: forked JVMs record 100,000 tests under a fixed `-Xmx`. It also updates `CLAUDE.md`.

**Tech Stack:**
- Java 17 (`--release 17` on every compile task), built with the JDK 25 toolchain; the tests also run on Java 17 and 21 (`testOnJava17`, `testOnJava21`);
- Gradle 9; JUnit 5.10.5 as the compile floor for main code, JUnit 6.1.3 for tests;
- Lombok, Mockito 5, AssertJ;
- Jackson 3.2.1 (`tools.jackson`), test scope only: the reference reader in `JsonGoldenTest`, and the reader in `ScenarioRunner` and the heap guard.

**Spec:** `IMPROVEMENT_PLAN.md` on branch `feature/improvements-plan`, in the main checkout `/Users/oleksiishamrai/personal_dev/junit-ctrf-extension`, section "0.5.2 Design — streaming JSON" (commit `ec3238a`). It holds the measurements, the decisions, the design of the reader, the writer and the `runId` check, the tests and the acceptance criteria. Read it before starting.

## Global Constraints

- **Paths:**
  - `W=/Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes` is the code worktree;
  - `SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad` holds files that are never committed;
  - `LOGS=$SCRATCH/streaming-logs` holds build logs, outside the worktree, because `clean` deletes `build/`;
  - `B=$SCRATCH/heap-bench` holds the heap benchmark that measured the spec's numbers: `bisect.py`, `src/HeapBench.java`, and `cp-<name>.txt` / `classes-<name>/` per measured version.
- **Every command runs from `$W`.** The shell's working directory and variables reset between tool calls. Start each command with `cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes &&`, and redefine `SCRATCH`, `LOGS` and `B` in any command that uses them, with `mkdir -p "$LOGS"`.
- **Where:** branch `fix/streaming-json` in `$W`, created in Task 1 from `origin/master` (`6ff69d5` or later). Ignore the untracked `ctrf-report.json` and `ctrf-report.json.lock` at the root, and never commit them.
- **Commits:**
  - exactly one commit per task, five in all, never squashed; the PR is rebase-merged;
  - each uses the full message given in its task;
  - **no `Co-Authored-By` trailer and no "Generated with Claude Code" line**, anywhere, including the PR description. This is the user's standing rule, and it overrides any default.
- **Java 17:**
  - main code uses no API newer than Java 17. For example, no `List.getFirst()`, `SequencedCollection`, `StringBuilder.repeat`, pattern matching in `switch`, or record patterns. `instanceof` patterns and classic `switch` statements are fine;
  - every task checks that all classes in `build/classes/java/main` are class-file major version 61.
- **Patch release, nothing breaks:**
  - every public class, constructor and method keeps its signature;
  - new public API is additive: `CtrfReportFileService.update(String, UnaryOperator<CtrfJson>)`, and in the internal package `io.github.alexshamrai.json` the methods `CtrfJsonMapper.read(Reader, Class)`, `CtrfJsonMapper.write(Object, Writer)` and `CtrfJsonMapper.readRunId(Reader)`;
  - the report's bytes don't change: `JsonGoldenTest` must pass unchanged in every task.
- **Logging:** messages that exist today keep their exact text, in particular `Ignoring the existing report, which can't be read: <path> - <message>` and `Failed to write results to file: <path> - <message>`.
- **Checkstyle** (`config/checkstyle/checkstyle.xml`) covers main and test code:
  - lines of at most 140 characters;
  - a wrapped operator (`+`, `&&`, `||`, `?`, `:`) starts the new line;
  - no star imports and no unused imports;
  - braces on every `if`, `for`, `while` and `do`;
  - method names match `^[a-z][a-z0-9][a-zA-Z0-9_]*$`; no more than two capital letters in a row in names;
  - overloaded methods are declared next to each other;
  - a Javadoc's first sentence ends with a period; block tags in the order `@param`, `@return`, `@throws`, after a blank line;
  - no Unicode escapes for printable characters. Build such strings from `(char) 0xFEFF` or `Character.toChars(...)`. JSON escapes written as `\\u00e9` in Java strings are fine; the existing `JsonParserTest` has them.
- **Javadoc:** public classes must not `{@link}` the package-private `JsonReader`; write `{@code JsonReader}`.
- **Builds:**
  - `./gradlew :build` (with the colon) runs the root module's tests on Java 25, 17 and 21, the scenarios, and checkstyle. The root `./gradlew build` also runs the validator, so don't use it;
  - `:build` skips javadoc, so `./gradlew :javadoc` runs separately;
  - always run the validator task with `--rerun`.
- **Check A, after every task:**

  ```bash
  cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
  LOGS=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/streaming-logs
  mkdir -p "$LOGS"
  ./gradlew :build > "$LOGS/build.log" 2>&1; grep -E 'FAILED|BUILD' "$LOGS/build.log" | tail -5
  ./gradlew :javadoc > "$LOGS/javadoc.log" 2>&1; grep -E 'error|BUILD' "$LOGS/javadoc.log" | tail -3
  python3 - <<'PY'
  import pathlib, sys
  bad = [str(p) for p in pathlib.Path('build/classes/java/main').rglob('*.class')
         if int.from_bytes(p.read_bytes()[6:8], 'big') != 61]
  print('all main classes are major version 61' if not bad else 'NOT 61: ' + ', '.join(bad))
  sys.exit(1 if bad else 0)
  PY
  ```

  Expected: `BUILD SUCCESSFUL` twice, no javadoc `error`, and `all main classes are major version 61`.
- **Check B, after Tasks 2, 3, 4 and 5,** in addition to Check A:

  ```bash
  cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
  LOGS=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/streaming-logs
  mkdir -p "$LOGS"
  URL=https://github.com/alexshamrai/junit-ctrf-reporter/actions/runs/12345678
  ENV_HEALTHY=false ./gradlew clean :integration-tests-listener:test :integration-ctrf-validator:test --rerun \
    -Dforks=2 -Dthreads=2 -Dctrf.build.name=system-build -Dctrf.build.number=777 -Dctrf.build.url=$URL \
    > "$LOGS/int-listener.log" 2>&1; grep -E 'FAILED|BUILD' "$LOGS/int-listener.log" | tail -5
  ./gradlew clean :integration-tests-extension:test :integration-ctrf-validator:test --rerun \
    -Dthreads=2 -Dctrf.build.name=system-build -Dctrf.build.number=777 -Dctrf.build.url=$URL \
    -Dctrf.report.path=../integration-tests-extension/build/test-results/ctrf-report.json \
    > "$LOGS/int-extension.log" 2>&1; grep -E 'FAILED|BUILD' "$LOGS/int-extension.log" | tail -5
  compat/run.sh 5.10.5 5.14.4 6.1.3 > "$LOGS/compat.log" 2>&1; grep -E '^(PASS|FAIL)|compatibility' "$LOGS/compat.log"
  ```

  Expected:
  - both Gradle runs end with `BUILD SUCCESSFUL`. The integration modules set `ignoreFailures`, so only a validator failure fails them;
  - compat prints 12 `PASS` lines (4 consumers × 3 JUnit versions) and `all compatibility checks passed`;
  - `clean` deletes `build/`, so run Check A again afterwards if a later step needs `build/classes`.
- **How the tasks prove themselves:**
  - each task first adds characterization tests where it must keep today's behaviour exactly (error messages, positions). They are written against today's code and must **pass** before any production change. If one fails there, the table is wrong: correct the table to today's output, never the code;
  - the failing test (RED) of Tasks 1 to 4 is a test that uses the new API and doesn't compile yet. Task 4 also changes the manager test's stub, which fails until the manager changes. Task 5's RED is the heap guard run against 0.5.1's code.

## Notes on behaviour the spec leaves open

These are decided here; keep them, and don't add tests that pin the other choice.

- **Error precedence.** Today the whole text is parsed before binding, so a syntax error anywhere wins over a binding error. Now binding runs as tokens arrive, so a binding error early in the text is reported before a syntax error later in it. Both are `JsonException`, and the file service replaces the report either way; only the logged message differs, for input that has both kinds of error.
- **A repeated `runId`.** `readRunId` returns the first one, where the full read keeps the last. Our writer never repeats a name.
- **`IMPROVEMENT_PLAN.md`** lives on `feature/improvements-plan`, not on this branch. The release notes and the follow-up are updated there after the merge, as for Phase 3. Task 5 changes `CLAUDE.md` only.

## Review Focus

These are the five inputs most likely to hurt a user that the spec implies but no task's main tests exercise. Each has a test in the task that owns the code.

1. **A failure trace of 20,000 characters,** the default `ctrf.max.trace.length`, longer than the reader's and the writer's 8 KB buffers.
   - Expected: written and read back whole.
   - Tests: `CtrfJsonMapperTest.textLongerThanTheBufferIsReadWhole` (Task 2) and `CtrfJsonMapperTest.reportLargerThanTheBufferIsWrittenWhole` (Task 3).
2. **Non-ASCII text longer than the read buffers in the report file,** whose UTF-8 bytes are cut across the decoder's reads.
   - Expected: read back exactly.
   - Test: `CtrfReportFileServiceTest.longNonAsciiTextIsReadBackWhole` (Task 2).
3. **A report that another tool saved with a UTF-8 byte order mark,** as PowerShell's `Out-File` does.
   - Expected: it reaches the merge function, and its `runId` is found.
   - Tests: `CtrfReportFileServiceTest.reportWithAByteOrderMarkReachesTheMerge` (Task 2) and `CtrfJsonMapperTest.runIdOfAReportStartingWithAByteOrderMarkIsFound` (Task 4).
4. **A pretty-printed report with Windows line endings and `runId` after `results`,** as another tool, or Jackson 3 with its alphabetical order, writes it.
   - Expected: its `runId` is still found, so a report of the same run is merged.
   - Test: `CtrfJsonMapperTest.runIdOfAPrettyPrintedReportWithWindowsLineEndingsIsFound` (Task 4).
5. **Code that calls the public `update(UnaryOperator)` directly.**
   - Expected: it still gets the current report, whatever its run.
   - Test: `CtrfReportFileServiceTest.updateWithoutARunIdReadsAReportOfAnyRun` (Task 4).

---

### Task 1: `JsonReader`, a streaming tokenizer

**Files:**
- Create: `src/main/java/io/github/alexshamrai/json/JsonReader.java`
- Modify: `src/main/java/io/github/alexshamrai/json/JsonParser.java` (rewritten as a wrapper)
- Create: `src/test/java/io/github/alexshamrai/json/JsonReaderTest.java`
- Create: `src/test/java/io/github/alexshamrai/json/ChunkedReader.java`
- Modify: `src/test/java/io/github/alexshamrai/json/JsonParserTest.java` (one characterization test and its arguments)
- Modify: `src/test/java/io/github/alexshamrai/json/JsonGoldenTest.java` (`goldenReport()` becomes package-private)

**Interfaces:**
- Consumes: nothing.
- Produces, all in package `io.github.alexshamrai.json`:
  - `final class JsonReader`, package-private:
    - `JsonReader(Reader in)`;
    - `enum Kind { OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL }`;
    - `static final int MAX_DEPTH = 100`, `static final int MAX_NUMBER_LENGTH = 1000`;
    - `Kind peek() throws IOException`;
    - `boolean beginObject() throws IOException`, `String nextName() throws IOException`, `boolean nextMember() throws IOException`;
    - `boolean beginArray() throws IOException`, `boolean nextElement() throws IOException`;
    - `Object nextValue() throws IOException`, `void skipValue() throws IOException`, `void endOfText() throws IOException`.
  - `JsonParser.parse(String)`, `JsonParser.MAX_DEPTH` and `JsonParser.MAX_NUMBER_LENGTH` keep their signatures.
  - Test code: `ChunkedReader(String text, int size)`, a `Reader` that hands out at most `size` characters per read; `JsonParserTest.malformedJson()`, `static Stream<Arguments>` of (input, exact message); `JsonGoldenTest.goldenReport()`, `static String`.

- [ ] **Step 1: Create the branch**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git status --short
git fetch origin
git switch -c fix/streaming-json origin/master
git branch --unset-upstream
git log --oneline -1
```

Expected:
- `git status --short` shows only `?? ctrf-report.json` and `?? ctrf-report.json.lock`;
- HEAD is `6ff69d5` or later.

- [ ] **Step 2: Pin today's parser error messages (characterization)**

In `JsonParserTest`, add these imports:

```java
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;
```

and, after `malformedJsonThrows`, add:

```java
    /**
     * Malformed input and the exact message it gives, character position included. Captured from the parser of 0.5.1.
     */
    static Stream<Arguments> malformedJson() {
        String bom = String.valueOf((char) 0xFEFF);
        return Stream.of(
            Arguments.of("", "Unexpected end of the JSON text at character 0"),
            Arguments.of(" ", "Unexpected end of the JSON text at character 1"),
            Arguments.of("{", "Expected a member name at character 1"),
            Arguments.of("{1:2}", "Expected a member name at character 1"),
            Arguments.of("{\"a\"", "Expected ':' at character 4"),
            Arguments.of("{\"a\" 1}", "Expected ':' at character 5"),
            Arguments.of("{\"a\":}", "Unexpected character '}' at character 5"),
            Arguments.of("{\"a\":1", "Expected '}' at character 6"),
            Arguments.of("{\"a\":1 2}", "Expected '}' at character 7"),
            Arguments.of("{\"a\":1,", "Expected a member name at character 7"),
            Arguments.of("{\"a\":1,}", "Expected a member name at character 7"),
            Arguments.of("{} {}", "Unexpected text after the JSON value at character 3"),
            Arguments.of("[", "Unexpected end of the JSON text at character 1"),
            Arguments.of("[1", "Expected ']' at character 2"),
            Arguments.of("[1,]", "Unexpected character ']' at character 3"),
            Arguments.of("[1 2]", "Expected ']' at character 3"),
            Arguments.of(" [ 1 , 2 ] x", "Unexpected text after the JSON value at character 11"),
            Arguments.of("01", "Unexpected text after the JSON value at character 1"),
            Arguments.of("-", "Expected a digit at character 1"),
            Arguments.of("1.", "Expected a digit at character 2"),
            Arguments.of("1e", "Expected a digit at character 2"),
            Arguments.of(".5", "Unexpected character '.' at character 0"),
            Arguments.of("NaN", "Unexpected character 'N' at character 0"),
            Arguments.of("'single'", "Unexpected character ''' at character 0"),
            Arguments.of("tru", "Expected true at character 0"),
            Arguments.of("falsy", "Expected false at character 0"),
            Arguments.of("[true,fals]", "Expected false at character 6"),
            Arguments.of("nul", "Expected null at character 0"),
            Arguments.of("\"open", "Unterminated string at character 5"),
            Arguments.of("\"tab\there\"", "Unescaped control character in a string at character 5"),
            Arguments.of("\"\\", "Unterminated escape at character 2"),
            Arguments.of("\"end\\", "Unterminated escape at character 5"),
            Arguments.of("\"bad \\x escape\"", "Invalid escape \\x at character 7"),
            Arguments.of("\"\\u12G4\"", "Invalid \\u escape at character 3"),
            Arguments.of("\"\\u", "Truncated \\u escape at character 3"),
            Arguments.of("\"\\u12", "Truncated \\u escape at character 3"),
            Arguments.of("\"\\u1G", "Truncated \\u escape at character 3"),
            Arguments.of("  " + bom + "{}", "Unexpected character '" + bom + "' at character 2"),
            Arguments.of(bom + " \"x\" y", "Unexpected text after the JSON value at character 6"),
            Arguments.of("1".repeat(1001), "Number longer than 1000 characters at character 1001"),
            Arguments.of("-" + "2".repeat(1000), "Number longer than 1000 characters at character 1001"),
            Arguments.of("[".repeat(101), "Arrays and objects nest deeper than 100 levels at character 100"),
            Arguments.of("{\"a\":".repeat(101), "Arrays and objects nest deeper than 100 levels at character 500"));
    }

    @ParameterizedTest(name = "{index}: {1}")
    @MethodSource("malformedJson")
    void malformedJsonIsReportedWithThePositionOfTheProblem(String json, String message) {
        assertThatThrownBy(() -> JsonParser.parse(json)).isInstanceOf(JsonException.class).hasMessage(message);
    }
```

In `JsonGoldenTest`, change `private static String goldenReport()` to `static String goldenReport()`.

- [ ] **Step 3: Run it on today's parser**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
LOGS=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/streaming-logs
mkdir -p "$LOGS"
./gradlew :test --tests 'io.github.alexshamrai.json.JsonParserTest' > "$LOGS/t1-characterize.log" 2>&1
grep -E 'FAILED|BUILD|tests completed' "$LOGS/t1-characterize.log" | tail -5
```

Expected: `BUILD SUCCESSFUL`. All 43 new cases pass on today's parser.

- [ ] **Step 4: Write the failing tests for the tokenizer**

Create `src/test/java/io/github/alexshamrai/json/ChunkedReader.java`:

```java
package io.github.alexshamrai.json;

import java.io.Reader;

/**
 * Hands out at most {@code size} characters per read, so that tokens are cut across a reader's buffer refills.
 */
final class ChunkedReader extends Reader {

    private final String text;
    private final int size;
    private int position;

    ChunkedReader(String text, int size) {
        this.text = text;
        this.size = size;
    }

    @Override
    public int read(char[] target, int offset, int length) {
        if (position >= text.length()) {
            return -1;
        }
        int count = Math.min(Math.min(size, length), text.length() - position);
        text.getChars(position, position + count, target, offset);
        position += count;
        return count;
    }

    @Override
    public void close() {
        // Nothing to release
    }
}
```

Create `src/test/java/io/github/alexshamrai/json/JsonReaderTest.java`:

```java
package io.github.alexshamrai.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static io.github.alexshamrai.json.JsonReader.Kind.ARRAY;
import static io.github.alexshamrai.json.JsonReader.Kind.BOOLEAN;
import static io.github.alexshamrai.json.JsonReader.Kind.NULL;
import static io.github.alexshamrai.json.JsonReader.Kind.NUMBER;
import static io.github.alexshamrai.json.JsonReader.Kind.OBJECT;
import static io.github.alexshamrai.json.JsonReader.Kind.STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonReaderTest {

    private static final String EMOJI = new String(Character.toChars(0x1F600));

    static List<String> wellFormedJson() {
        return List.of(
            JsonGoldenTest.goldenReport(),
            (char) 0xFEFF + " \n\t{ \"a\" : [ 1 , true , null ] }\r\n",
            "\"\\\" \\\\ \\/ \\b \\f \\n \\r \\t \\u00e9 \\uD83D\\uDE00\"",
            "[0,-0,1.5,-1.5e3,1E2,4.9E-324,2147483648,9223372036854775808,123456789012345678901234567890]",
            "[[[]],{},{\"b\":1,\"a\":2,\"b\":3},[true,false,null]]");
    }

    @ParameterizedTest(name = "chunks of {0}")
    @ValueSource(ints = {1, 2, 3, 7})
    void readsTheSameInChunksAsFromAString(int size) throws IOException {
        for (String json : wellFormedJson()) {
            assertThat(read(new ChunkedReader(json, size))).as(json).isEqualTo(JsonParser.parse(json));
        }
    }

    @ParameterizedTest(name = "chunks of {0}")
    @ValueSource(ints = {1, 2, 3, 7})
    void reportsErrorsAtTheSamePositionInChunks(int size) {
        JsonParserTest.malformedJson().forEach(arguments -> {
            String json = (String) arguments.get()[0];
            String message = (String) arguments.get()[1];
            assertThatThrownBy(() -> read(new ChunkedReader(json, size)))
                .as(json)
                .isInstanceOf(JsonException.class)
                .hasMessage(message);
        });
    }

    static IntStream paddings() {
        return IntStream.rangeClosed(8150, 8200);
    }

    @ParameterizedTest(name = "{0} characters before the escapes")
    @MethodSource("paddings")
    void tokensCutByTheEndOfTheBufferAreReadWhole(int padding) throws IOException {
        String json = "[\"" + "a".repeat(padding) + "\\u00e9\\uD83D\\uDE00\",true,null,-12.5e3]";

        Object value = read(new StringReader(json));

        assertThat(value).isEqualTo(Arrays.asList("a".repeat(padding) + (char) 0xE9 + EMOJI, true, null, -12500.0));
    }

    @Test
    void peekTellsTheKindOfTheNextValue() throws IOException {
        var reader = new JsonReader(new StringReader("[{}, [], \"s\", -1, true, false, null]"));
        List<JsonReader.Kind> kinds = new ArrayList<>();

        assertThat(reader.beginArray()).isTrue();
        do {
            kinds.add(reader.peek());
            reader.skipValue();
        } while (reader.nextElement());
        reader.endOfText();

        assertThat(kinds).containsExactly(OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, BOOLEAN, NULL);
    }

    @Test
    void membersAreReadOneByOneAndAValueCanBeSkipped() throws IOException {
        var reader = new JsonReader(new StringReader("{\"skip\":{\"a\":[1,\"x\",{\"b\":null}],\"c\":true},\"keep\":2}"));

        assertThat(reader.beginObject()).isTrue();
        assertThat(reader.nextName()).isEqualTo("skip");
        reader.skipValue();
        assertThat(reader.nextMember()).isTrue();
        assertThat(reader.nextName()).isEqualTo("keep");
        assertThat(reader.nextValue()).isEqualTo(2);
        assertThat(reader.nextMember()).isFalse();
        reader.endOfText();
    }

    @Test
    void emptyObjectsAndArraysHaveNoMembersOrElements() throws IOException {
        var reader = new JsonReader(new StringReader("[ { } , [ ] ]"));

        assertThat(reader.beginArray()).isTrue();
        assertThat(reader.beginObject()).isFalse();
        assertThat(reader.nextElement()).isTrue();
        assertThat(reader.beginArray()).isFalse();
        assertThat(reader.nextElement()).isFalse();
        reader.endOfText();
    }

    @Test
    void skippingIsAsStrictAsReading() throws IOException {
        var reader = new JsonReader(new StringReader("{\"skip\":[1,]}"));
        reader.beginObject();
        reader.nextName();

        assertThatThrownBy(reader::skipValue).isInstanceOf(JsonException.class).hasMessage("Unexpected character ']' at character 11");
    }

    @Test
    void skippingKeepsTheNestingLimit() throws IOException {
        var reader = new JsonReader(new StringReader("{\"skip\":" + "[".repeat(200) + "]".repeat(200) + "}"));
        reader.beginObject();
        reader.nextName();

        assertThatThrownBy(reader::skipValue).isInstanceOf(JsonException.class).hasMessageContaining("deeper than 100 ");
    }

    private static Object read(Reader in) throws IOException {
        var reader = new JsonReader(in);
        Object value = reader.nextValue();
        reader.endOfText();
        return value;
    }
}
```

- [ ] **Step 5: Run them and watch them fail**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
LOGS=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/streaming-logs
./gradlew :compileTestJava > "$LOGS/t1-red.log" 2>&1; grep -E 'error:|BUILD' "$LOGS/t1-red.log" | head -5
```

Expected: `BUILD FAILED`, with `cannot find symbol` for `JsonReader`.

- [ ] **Step 6: Write `JsonReader`**

Create `src/main/java/io/github/alexshamrai/json/JsonReader.java`:

```java
package io.github.alexshamrai.json;

import java.io.IOException;
import java.io.Reader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads JSON text token by token from a {@link Reader}, through its own buffer, so the text is never held whole.
 * <p>
 * A whole number becomes the smallest of {@code Integer}, {@code Long} and {@code BigInteger} that holds it, and any
 * other number a {@code Double}, as Jackson reads numbers into {@code Object}. Whitespace may surround any token, and
 * the text may start with a byte order mark. Anything else that isn't strict JSON throws, as do a number longer than
 * {@value #MAX_NUMBER_LENGTH} characters and nesting deeper than {@value #MAX_DEPTH} levels, which would otherwise
 * take minutes or overflow the stack. A message names the problem and its position, counted in characters from the
 * start of the text, the byte order mark included.
 * <p>
 * An object is read with {@link #beginObject}, then for each member {@link #nextName}, its value, and
 * {@link #nextMember}; an array with {@link #beginArray}, then for each element its value and {@link #nextElement}.
 */
final class JsonReader {

    /**
     * What a JSON value is, as {@link #peek} tells it from the value's first character.
     */
    enum Kind {
        OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL
    }

    /**
     * How deep arrays and objects may nest. Real reports nest fewer than ten levels; 100 keeps the recursive reader
     * and writer well inside small thread stacks.
     */
    static final int MAX_DEPTH = 100;

    /**
     * How many characters a number may have, as with Jackson.
     */
    static final int MAX_NUMBER_LENGTH = 1000;

    private static final char BYTE_ORDER_MARK = (char) 0xFEFF;
    private static final int BUFFER_SIZE = 8192;

    private final Reader in;
    private final char[] buffer = new char[BUFFER_SIZE];
    private final StringBuilder text = new StringBuilder();
    private int pos;
    private int limit;
    private long consumedBeforeBuffer;
    private int depth;
    private boolean started;

    JsonReader(Reader in) {
        this.in = in;
    }

    /**
     * Tells what the next value is, without reading it.
     */
    Kind peek() throws IOException {
        skipWhitespace();
        int c = peekChar();
        switch (c) {
            case -1:
                throw error("Unexpected end of the JSON text");
            case '{':
                return Kind.OBJECT;
            case '[':
                return Kind.ARRAY;
            case '"':
                return Kind.STRING;
            case 't':
            case 'f':
                return Kind.BOOLEAN;
            case 'n':
                return Kind.NULL;
            default:
                if (c == '-' || isDigit(c)) {
                    return Kind.NUMBER;
                }
                throw error("Unexpected character '" + (char) c + "'");
        }
    }

    /**
     * Reads an object's opening brace, and returns whether a member follows; if none does, the closing brace is read
     * too.
     */
    boolean beginObject() throws IOException {
        skipWhitespace();
        enterNesting();
        expect('{');
        skipWhitespace();
        if (peekChar() == '}') {
            pos++;
            depth--;
            return false;
        }
        return true;
    }

    /**
     * Reads a member's name and the colon after it.
     */
    String nextName() throws IOException {
        skipWhitespace();
        if (peekChar() != '"') {
            throw error("Expected a member name");
        }
        String name = readString();
        skipWhitespace();
        expect(':');
        return name;
    }

    /**
     * Reads what follows a member's value, and returns whether it's a comma, so another member follows, rather than
     * the object's closing brace.
     */
    boolean nextMember() throws IOException {
        skipWhitespace();
        if (peekChar() == ',') {
            pos++;
            return true;
        }
        expect('}');
        depth--;
        return false;
    }

    /**
     * Reads an array's opening bracket, and returns whether an element follows; if none does, the closing bracket is
     * read too.
     */
    boolean beginArray() throws IOException {
        skipWhitespace();
        enterNesting();
        expect('[');
        skipWhitespace();
        if (peekChar() == ']') {
            pos++;
            depth--;
            return false;
        }
        return true;
    }

    /**
     * Reads what follows an element, and returns whether it's a comma, so another element follows, rather than the
     * array's closing bracket.
     */
    boolean nextElement() throws IOException {
        skipWhitespace();
        if (peekChar() == ',') {
            pos++;
            return true;
        }
        expect(']');
        depth--;
        return false;
    }

    /**
     * Reads the next value: a {@code String}, a {@code Boolean}, a number, {@code null}, or an object or an array as a
     * {@code LinkedHashMap} or an {@code ArrayList}. An object keeps its members in order, and a repeated name keeps
     * its last value.
     */
    Object nextValue() throws IOException {
        switch (peek()) {
            case OBJECT:
                return readObject();
            case ARRAY:
                return readArray();
            case STRING:
                return readString();
            case NUMBER:
                return readNumber();
            case BOOLEAN:
                return readBoolean();
            default:
                readWord("null");
                return null;
        }
    }

    /**
     * Reads past the next value without keeping it, as strictly as {@link #nextValue}: objects and arrays aren't
     * built, and strings and numbers are dropped as soon as they're read.
     */
    void skipValue() throws IOException {
        switch (peek()) {
            case OBJECT:
                if (beginObject()) {
                    do {
                        nextName();
                        skipValue();
                    } while (nextMember());
                }
                break;
            case ARRAY:
                if (beginArray()) {
                    do {
                        skipValue();
                    } while (nextElement());
                }
                break;
            default:
                nextValue();
        }
    }

    /**
     * Checks that only whitespace follows.
     */
    void endOfText() throws IOException {
        skipWhitespace();
        if (peekChar() != -1) {
            throw error("Unexpected text after the JSON value");
        }
    }

    private Map<String, Object> readObject() throws IOException {
        Map<String, Object> object = new LinkedHashMap<>();
        if (beginObject()) {
            do {
                String name = nextName();
                object.put(name, nextValue());
            } while (nextMember());
        }
        return object;
    }

    private List<Object> readArray() throws IOException {
        List<Object> array = new ArrayList<>();
        if (beginArray()) {
            do {
                array.add(nextValue());
            } while (nextElement());
        }
        return array;
    }

    // Starts at the opening quote, which peek has seen
    private String readString() throws IOException {
        pos++;
        text.setLength(0);
        while (true) {
            if (pos == limit && ensureAvailable(1) == 0) {
                throw error("Unterminated string");
            }
            char c = buffer[pos++];
            if (c == '"') {
                return text.toString();
            } else if (c == '\\') {
                readEscape();
            } else if (c < 0x20) {
                throw error("Unescaped control character in a string");
            } else {
                text.append(c);
            }
        }
    }

    private void readEscape() throws IOException {
        if (pos == limit && ensureAvailable(1) == 0) {
            throw error("Unterminated escape");
        }
        char c = buffer[pos++];
        switch (c) {
            case '"':
            case '\\':
            case '/':
                text.append(c);
                break;
            case 'b':
                text.append('\b');
                break;
            case 'f':
                text.append('\f');
                break;
            case 'n':
                text.append('\n');
                break;
            case 'r':
                text.append('\r');
                break;
            case 't':
                text.append('\t');
                break;
            case 'u':
                text.append(readHexCharacter());
                break;
            default:
                throw error("Invalid escape \\" + c);
        }
    }

    private char readHexCharacter() throws IOException {
        if (ensureAvailable(4) < 4) {
            throw error("Truncated \\u escape");
        }
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = hexValue(buffer[pos + i]);
            if (digit < 0) {
                throw error("Invalid \\u escape");
            }
            value = value * 16 + digit;
        }
        pos += 4;
        return (char) value;
    }

    private static int hexValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    private Number readNumber() throws IOException {
        long start = position();
        text.setLength(0);
        boolean whole = true;
        if (peekChar() == '-') {
            take();
        }
        if (peekChar() == '0') {
            take();
        } else {
            readDigits();
        }
        if (peekChar() == '.') {
            whole = false;
            take();
            readDigits();
        }
        int c = peekChar();
        if (c == 'e' || c == 'E') {
            whole = false;
            take();
            c = peekChar();
            if (c == '+' || c == '-') {
                take();
            }
            readDigits();
        }
        if (position() - start > MAX_NUMBER_LENGTH) {
            throw error("Number longer than " + MAX_NUMBER_LENGTH + " characters");
        }
        String number = text.toString();
        return whole ? wholeNumber(number) : Double.valueOf(number);
    }

    // Takes the character peekChar has seen into the number; past the length limit, which throws once the number
    // ends, it isn't kept
    private void take() {
        if (text.length() <= MAX_NUMBER_LENGTH) {
            text.append(buffer[pos]);
        }
        pos++;
    }

    private void readDigits() throws IOException {
        if (!isDigit(peekChar())) {
            throw error("Expected a digit");
        }
        while (isDigit(peekChar())) {
            take();
        }
    }

    private static Number wholeNumber(String digits) {
        var value = new BigInteger(digits);
        if (value.bitLength() < Integer.SIZE) {
            return value.intValue();
        }
        if (value.bitLength() < Long.SIZE) {
            return value.longValue();
        }
        return value;
    }

    private Boolean readBoolean() throws IOException {
        if (peekChar() == 't') {
            readWord("true");
            return Boolean.TRUE;
        }
        readWord("false");
        return Boolean.FALSE;
    }

    private void readWord(String word) throws IOException {
        int available = ensureAvailable(word.length());
        for (int i = 0; i < word.length(); i++) {
            if (i >= available || buffer[pos + i] != word.charAt(i)) {
                throw error("Expected " + word);
            }
        }
        pos += word.length();
    }

    private void expect(char c) throws IOException {
        if (peekChar() != c) {
            throw error("Expected '" + c + "'");
        }
        pos++;
    }

    private static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    // The byte order mark may come only first, before any whitespace
    private void skipWhitespace() throws IOException {
        if (!started) {
            started = true;
            if (peekChar() == BYTE_ORDER_MARK) {
                pos++;
            }
        }
        while (true) {
            int c = peekChar();
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            pos++;
        }
    }

    // The next character, without taking it, or -1 at the end of the text
    private int peekChar() throws IOException {
        if (pos == limit && ensureAvailable(1) == 0) {
            return -1;
        }
        return buffer[pos];
    }

    /**
     * Makes up to {@code count} characters available from {@code pos}, at most the buffer's size, and returns how many
     * are: fewer only at the end of the text. Unread characters move to the front of the buffer first.
     */
    private int ensureAvailable(int count) throws IOException {
        if (limit - pos < count) {
            if (pos > 0) {
                System.arraycopy(buffer, pos, buffer, 0, limit - pos);
                consumedBeforeBuffer += pos;
                limit -= pos;
                pos = 0;
            }
            while (limit < count) {
                int read = in.read(buffer, limit, buffer.length - limit);
                if (read < 0) {
                    break;
                }
                limit += read;
            }
        }
        return limit - pos;
    }

    private long position() {
        return consumedBeforeBuffer + pos;
    }

    private void enterNesting() {
        depth++;
        if (depth > MAX_DEPTH) {
            throw error("Arrays and objects nest deeper than " + MAX_DEPTH + " levels");
        }
    }

    private JsonException error(String problem) {
        return new JsonException(problem + " at character " + position());
    }
}
```

- [ ] **Step 7: Make `JsonParser` a wrapper over it**

Replace the whole of `src/main/java/io/github/alexshamrai/json/JsonParser.java` with:

```java
package io.github.alexshamrai.json;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Parses JSON text into {@code null}, {@link String}, {@link Boolean}, {@link Integer}, {@link Long},
 * {@link BigInteger}, {@link Double}, {@link LinkedHashMap} and {@link ArrayList}.
 * <p>
 * A whole number becomes the smallest of {@code Integer}, {@code Long} and {@code BigInteger} that holds it, and any
 * other number a {@code Double}, as Jackson reads numbers into {@code Object}. An object keeps its members in order,
 * and a repeated name keeps its last value. Whitespace may surround any token, and the text may start with a byte
 * order mark. Anything else that isn't strict JSON throws. So do a number longer than 1000 characters and nesting
 * deeper than 100 levels, which would otherwise take minutes or overflow the stack. It reads through
 * {@code JsonReader}, which has the rules.
 */
public final class JsonParser {

    /**
     * How deep arrays and objects may nest.
     */
    static final int MAX_DEPTH = JsonReader.MAX_DEPTH;

    /**
     * How many characters a number may have, as with Jackson.
     */
    static final int MAX_NUMBER_LENGTH = JsonReader.MAX_NUMBER_LENGTH;

    private JsonParser() {
        // Utility class - prevent instantiation
    }

    /**
     * Parses one JSON value.
     *
     * @param text the JSON text
     * @return the value
     * @throws JsonException if the text isn't exactly one JSON value
     */
    public static Object parse(String text) {
        try {
            var reader = new JsonReader(new StringReader(text));
            Object value = reader.nextValue();
            reader.endOfText();
            return value;
        } catch (IOException e) {
            throw new UncheckedIOException("A StringReader doesn't fail", e);
        }
    }
}
```

- [ ] **Step 8: Run the JSON tests and watch them pass**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
LOGS=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/streaming-logs
./gradlew :test --tests 'io.github.alexshamrai.json.*' > "$LOGS/t1-green.log" 2>&1
grep -E 'FAILED|BUILD|tests completed' "$LOGS/t1-green.log" | tail -8
```

Expected: `BUILD SUCCESSFUL`. That includes `JsonParserTest` (the existing tests, the small-stack test and the 43 characterization cases, now on `JsonReader`), `JsonReaderTest`, `JsonGoldenTest`, `JsonWriterTest` and `CtrfJsonMapperTest`, unchanged.

- [ ] **Step 9: Check A**

Run Check A from the Global Constraints. Expected as stated there.

- [ ] **Step 10: Commit**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git add src/main/java/io/github/alexshamrai/json/JsonReader.java src/main/java/io/github/alexshamrai/json/JsonParser.java \
  src/test/java/io/github/alexshamrai/json/JsonReaderTest.java src/test/java/io/github/alexshamrai/json/ChunkedReader.java \
  src/test/java/io/github/alexshamrai/json/JsonParserTest.java src/test/java/io/github/alexshamrai/json/JsonGoldenTest.java
git status --short
git commit -q -F - <<'EOF'
refactor: read JSON through a streaming tokenizer

JsonReader pulls tokens from a java.io.Reader through its own 8 KB
buffer, so reading no longer needs the whole text as one String. It is
exactly as strict as JsonParser was: the same limits, number types and
error messages, with the same character positions. JsonParser.parse is
now a thin wrapper over it, so its tests run against the tokenizer, and
new tests read every input in chunks of 1, 2, 3 and 7 characters and
across the end of the buffer.

Nothing reads a report through it yet.
EOF
git log --oneline -1
```

Expected: `git status --short` before the commit lists only the six files (and the two untracked reports); one new commit.

---

### Task 2: Bind a `Reader` straight into the model

**Files:**
- Modify: `src/main/java/io/github/alexshamrai/json/CtrfJsonMapper.java` (the reading half)
- Modify: `src/main/java/io/github/alexshamrai/CtrfReportFileService.java` (`readExisting`)
- Modify: `src/test/java/io/github/alexshamrai/json/CtrfJsonMapperTest.java`
- Modify: `src/test/java/io/github/alexshamrai/json/JsonGoldenTest.java`
- Modify: `src/test/java/io/github/alexshamrai/CtrfReportFileServiceTest.java`

**Interfaces:**
- Consumes: `JsonReader` from Task 1 (`peek`, `beginObject`, `nextName`, `nextMember`, `beginArray`, `nextElement`, `nextValue`, `skipValue`, `endOfText`, `Kind`).
- Produces:
  - `public static <T> T CtrfJsonMapper.read(Reader in, Class<T> type) throws IOException`;
  - `CtrfJsonMapper.read(String, Class)` keeps its signature;
  - the private helper `static Object readValue(JsonReader reader, Type type, String path) throws IOException` in `CtrfJsonMapper`, which Task 4's `readRunId`, in the same class, calls.

- [ ] **Step 1: Pin today's mapping errors (characterization)**

In `CtrfJsonMapperTest`, add these imports:

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;
```

and add, after `textThatIsNotJsonCantBeRead`:

```java
    /**
     * Reports that are valid JSON but don't fit the model, and the exact message each gives. Captured from the mapper
     * of 0.5.1.
     */
    static Stream<Arguments> reportsThatDontFitTheModel() {
        return Stream.of(
            Arguments.of("[]", "$: expected an object"),
            Arguments.of("{\"results\":[]}", "$.results: expected an object"),
            Arguments.of("{\"results\":{\"tests\":{}}}", "$.results.tests: expected an array"),
            Arguments.of("{\"results\":{\"tests\":[{\"status\":5}]}}", "$.results.tests[0].status: unknown test status 5"),
            Arguments.of("{\"results\":{\"tests\":[{\"tags\":\"a\"}]}}", "$.results.tests[0].tags: expected an array"),
            Arguments.of("{\"results\":{\"tests\":[{\"name\":[1,{\"a\":2}]}]}}", "$.results.tests[0].name: expected a string"),
            Arguments.of("{\"results\":{\"tests\":[{\"duration\":1e30}]}}",
                "$.results.tests[0].duration: \"1.0E30\" is not a whole number in range"),
            Arguments.of("{\"results\":{\"summary\":{\"tests\":\"x\"}}}", "$.results.summary.tests: \"x\" is not a whole number in range"),
            Arguments.of("{\"results\":{\"summary\":{\"tests\":3000000000}}}", "$.results.summary.tests: 3000000000 is out of range"),
            Arguments.of("{\"results\":{\"environment\":{\"healthy\":\"maybe\"}}}",
                "$.results.environment.healthy: expected true or false, not maybe"),
            Arguments.of("{\"runId\":{}}", "$.runId: expected a string"),
            Arguments.of("{\"extra\":[1]}", "$.extra: expected an object"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("reportsThatDontFitTheModel")
    void reportThatDoesNotFitTheModelNamesTheFieldAndTheProblem(String json, String message) {
        assertThatThrownBy(() -> CtrfJsonMapper.read(json, CtrfJson.class)).isInstanceOf(JsonException.class).hasMessage(message);
    }
```

In `CtrfReportFileServiceTest`, add after `reportIsWrittenAsUtf8`:

```java
    @Test
    @DisplayName("A report that isn't valid UTF-8 reaches the merge function as null")
    void reportThatIsNotUtf8ReachesTheMergeAsNull() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        Files.write(report, new byte[] {'{', '"', 'a', '"', ':', '"', (byte) 0xC3, (byte) 0x28, '"', '}'});
        List<CtrfJson> seen = new ArrayList<>();

        serviceFor(report).update(existing -> {
            seen.add(existing);
            return marker("fresh");
        });

        assertThat(seen).containsExactly((CtrfJson) null);
        assertThat(readMarker(report)).isEqualTo("fresh");
        assertThat(logs.text()).contains("can't be read");
    }

    @Test
    @DisplayName("Review Focus 2: non-ASCII text longer than the read buffers is read back whole")
    void longNonAsciiTextIsReadBackWhole() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        String text = (String.valueOf((char) 0x65E5) + (char) 0xFC + (char) 0x672C).repeat(10_000);
        var service = serviceFor(report);
        service.update(existing -> marker(text));
        List<String> seen = new ArrayList<>();

        service.update(existing -> {
            seen.add(existing.getGeneratedBy());
            return existing;
        });

        assertThat(seen).containsExactly(text);
    }

    @Test
    @DisplayName("Review Focus 3: a report that another tool saved with a byte order mark reaches the merge function")
    void reportWithAByteOrderMarkReachesTheMerge() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        Files.writeString(report, (char) 0xFEFF + "{\"generatedBy\":\"first\"}", StandardCharsets.UTF_8);
        List<String> seen = new ArrayList<>();

        serviceFor(report).update(existing -> {
            seen.add(existing.getGeneratedBy());
            return marker("second");
        });

        assertThat(seen).containsExactly("first");
        assertThat(logs.text()).isEmpty();
    }
```

- [ ] **Step 2: Run the characterization tests on today's code**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
LOGS=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/streaming-logs
./gradlew :test --tests 'io.github.alexshamrai.json.CtrfJsonMapperTest' --tests 'io.github.alexshamrai.CtrfReportFileServiceTest' \
  > "$LOGS/t2-characterize.log" 2>&1
grep -E 'FAILED|BUILD|tests completed' "$LOGS/t2-characterize.log" | tail -5
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Write the failing tests for reading from a `Reader`**

In `CtrfJsonMapperTest`, add the imports:

```java
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.StringReader;
```

and these tests:

```java
    @ParameterizedTest(name = "chunks of {0}")
    @ValueSource(ints = {1, 7, 8191})
    void reportIsReadTheSameFromAReaderInAnyChunkSize(int size) throws IOException {
        String json = JsonGoldenTest.goldenReport();

        assertThat(CtrfJsonMapper.read(new ChunkedReader(json, size), CtrfJson.class)).isEqualTo(CtrfJsonMapper.read(json, CtrfJson.class));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Review Focus 1: a trace of 20,000 characters, longer than the reader's buffer, is read whole")
    void textLongerThanTheBufferIsReadWhole() throws IOException {
        String trace = "java.lang.AssertionError: expected \"a\"\n\tat com.example.PaymentTest.pays(PaymentTest.java:42)\n"
            .repeat(250).substring(0, 20_000);
        String json = "{\"name\":\"pays()\",\"trace\":" + JsonWriter.write(trace) + "}";

        assertThat(json.length()).isGreaterThan(2 * 8192);
        assertThat(CtrfJsonMapper.read(new StringReader(json), Test.class).getTrace()).isEqualTo(trace);
    }

    @org.junit.jupiter.api.Test
    void readerIsReadToTheEndOfTheText() {
        assertThatThrownBy(() -> CtrfJsonMapper.read(new StringReader("{\"runId\":\"run-1\"} {}"), CtrfJson.class))
            .isInstanceOf(JsonException.class)
            .hasMessage("Unexpected text after the JSON value at character 18");
    }
```

In `JsonGoldenTest`, add the imports:

```java
import java.io.StringReader;
import java.util.ArrayList;
```

and this test and helper:

```java
    @org.junit.jupiter.api.Test
    void readerReadsALargeGeneratedReportAsJacksonDoes() throws IOException {
        String json = CtrfJsonMapper.write(generatedReport(2_000));

        assertThat(CtrfJsonMapper.read(new StringReader(json), CtrfJson.class)).isEqualTo(JACKSON.readValue(json, CtrfJson.class));
    }

    /**
     * A report with {@code count} tests of every status, every tenth failing with a message and a trace, every 25th
     * retried, and every 100th with nested {@code extra} values and numeric parameters.
     */
    private static CtrfJson generatedReport(int count) {
        Test.TestStatus[] statuses = Test.TestStatus.values();
        List<Test> tests = new ArrayList<>();
        for (int n = 0; n < count; n++) {
            var test = Test.builder()
                .testId("[engine:junit-jupiter]/[class:com.example.Generated" + (n / 50) + "]/[method:test" + n + "()]")
                .name("test" + n + "()")
                .status(statuses[n % statuses.length])
                .duration(n)
                .start(1_000L + n)
                .stop(1_000L + 2L * n)
                .filePath("com.example.Generated" + (n / 50))
                .threadId("worker-" + (n % 4))
                .tags(List.of("smoke", "t" + (n % 7)));
            if (n % 10 == 0) {
                test.message("expected: <" + n + "> but was: <" + (n + 1) + ">")
                    .trace(TRICKY_TEXT + "\n\tat com.example.Generated.test" + n + "(Generated.java:" + n + ")");
            }
            if (n % 25 == 0) {
                test.retries(1).flaky(true).retryAttempts(List.of(RetryAttempt.builder()
                    .attempt(1).status(Test.TestStatus.FAILED).duration(1).start(1L).stop(2L).message("first try").build()));
            }
            if (n % 100 == 0) {
                test.extra(Extra.builder().customData(nested()).build()).parameters(numbers());
            }
            tests.add(test.build());
        }
        var results = Results.builder()
            .tool(Tool.builder().name("JUnit").version("6.1.3").build())
            .summary(Summary.builder().tests(count).start(1_000L).stop(9_000L).build())
            .tests(tests)
            .build();
        return CtrfJson.builder().runId("generated").results(results).build();
    }
```

`@org.junit.jupiter.api.Test` is written in full because the model's `Test` is imported. `TRICKY_TEXT`, `nested()`, `numbers()` and `JACKSON` already exist in `JsonGoldenTest`.

- [ ] **Step 4: Run them and watch them fail**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
LOGS=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/streaming-logs
./gradlew :compileTestJava > "$LOGS/t2-red.log" 2>&1; grep -E 'error:|BUILD' "$LOGS/t2-red.log" | head -5
```

Expected: `BUILD FAILED`, `no suitable method found for read(StringReader,Class<...>)` or `ChunkedReader cannot be converted to String`.

- [ ] **Step 5: Bind tokens straight into the model**

In `CtrfJsonMapper`:

1. Add the imports `java.io.IOException`, `java.io.Reader`, `java.io.StringReader` and `java.io.UncheckedIOException`.
2. Replace the method `read(String json, Class<T> type)` with these two:

```java
    /**
     * Reads JSON text into a model class.
     *
     * @param json the JSON text
     * @param type the model class
     * @param <T>  the model class
     * @return the model object, or {@code null} if the JSON text is the literal {@code null}
     * @throws JsonException if the text isn't valid JSON, or doesn't fit the model
     */
    public static <T> T read(String json, Class<T> type) {
        try {
            return read(new StringReader(json), type);
        } catch (IOException e) {
            throw new UncheckedIOException("A StringReader doesn't fail", e);
        }
    }

    /**
     * Reads JSON into a model class as it arrives: the text is never held whole, and only the values of
     * {@code Object} and {@code Map} fields are built as maps and lists.
     *
     * @param in   the JSON text, read to its end and not closed
     * @param type the model class
     * @param <T>  the model class
     * @return the model object, or {@code null} if the JSON text is the literal {@code null}
     * @throws IOException   if {@code in} fails
     * @throws JsonException if the text isn't valid JSON, or doesn't fit the model
     */
    public static <T> T read(Reader in, Class<T> type) throws IOException {
        var reader = new JsonReader(in);
        Object model = readValue(reader, type, "$");
        reader.endOfText();
        return type.cast(model);
    }
```

3. Replace the methods `fromJsonValue`, `toModel`, `toList` and `toMap` with these. Keep `nullValueOf`, `toText`, `toInt`, `toLong`, `wholePart`, `toBoolean`, `toStatus`, `rawClass`, `elementType`, `isModel`, `newInstance`, `get` and `set` as they are.

```java
    private static Object readValue(JsonReader reader, Type type, String path) throws IOException {
        Class<?> raw = rawClass(type);
        JsonReader.Kind kind = reader.peek();
        if (kind == JsonReader.Kind.NULL) {
            reader.nextValue();
            return nullValueOf(raw);
        }
        if (raw == List.class) {
            if (kind != JsonReader.Kind.ARRAY) {
                throw new JsonException(path + ": expected an array");
            }
            return readList(reader, elementType(type), path);
        }
        if (raw == Map.class) {
            if (kind != JsonReader.Kind.OBJECT) {
                throw new JsonException(path + ": expected an object");
            }
            return reader.nextValue();
        }
        if (raw == Object.class) {
            return reader.nextValue();
        }
        if (isModel(raw)) {
            if (kind != JsonReader.Kind.OBJECT) {
                throw new JsonException(path + ": expected an object");
            }
            return readModel(reader, raw, path);
        }
        return fromScalar(reader.nextValue(), raw, path);
    }

    // A text, number, boolean or status field. An object or an array read into one fails with the value in the message
    private static Object fromScalar(Object json, Class<?> raw, String path) {
        if (raw == String.class) {
            return toText(json, path);
        }
        if (raw == int.class || raw == Integer.class) {
            return toInt(json, path);
        }
        if (raw == long.class || raw == Long.class) {
            return toLong(json, path);
        }
        if (raw == boolean.class || raw == Boolean.class) {
            return toBoolean(json, path);
        }
        if (raw == Test.TestStatus.class) {
            return toStatus(json, path);
        }
        throw new JsonException(path + ": the model has a field of a type this mapper can't read, " + raw.getName());
    }

    private static Object readModel(JsonReader reader, Class<?> type, String path) throws IOException {
        Object model = newInstance(type);
        Map<String, Field> fields = FIELDS.get(type);
        if (reader.beginObject()) {
            do {
                String name = reader.nextName();
                Field field = fields.get(ALIASES.getOrDefault(name, name));
                if (field == null) {
                    reader.skipValue();
                } else {
                    set(field, model, readValue(reader, field.getGenericType(), path + "." + name));
                }
            } while (reader.nextMember());
        }
        return model;
    }

    private static List<Object> readList(JsonReader reader, Type elementType, String path) throws IOException {
        List<Object> list = new ArrayList<>();
        if (reader.beginArray()) {
            int index = 0;
            do {
                list.add(readValue(reader, elementType, path + "[" + index + "]"));
                index++;
            } while (reader.nextElement());
        }
        return list;
    }
```

Remove imports the new code no longer needs, if the compiler or checkstyle reports any (for example `java.util.Arrays` stays, because `toJsonValue` still uses it until Task 3).

- [ ] **Step 6: Read the report file through a `Reader`**

In `CtrfReportFileService`, add `import java.io.Reader;`, and replace `readExisting` with:

```java
    private CtrfJson readExisting(Path report) {
        if (!Files.exists(report)) {
            return null;
        }
        try (Reader in = Files.newBufferedReader(report, StandardCharsets.UTF_8)) {
            return CtrfJsonMapper.read(in, CtrfJson.class);
        } catch (IOException | RuntimeException e) {
            LOGGER.warning("Ignoring the existing report, which can't be read: " + report + " - " + e.getMessage());
            return null;
        }
    }
```

`Files.newBufferedReader` decodes with a decoder that reports malformed input, as `Files.readString` did, so invalid UTF-8 still throws `MalformedInputException` and the report is replaced.

- [ ] **Step 7: Run the tests and watch them pass**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
LOGS=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/streaming-logs
./gradlew :test --tests 'io.github.alexshamrai.json.*' --tests 'io.github.alexshamrai.CtrfReportFileServiceTest' \
  --tests 'io.github.alexshamrai.scenario.PersistenceScenarioTest' > "$LOGS/t2-green.log" 2>&1
grep -E 'FAILED|BUILD|tests completed' "$LOGS/t2-green.log" | tail -8
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Check A and Check B**

Run Check A, then Check B, from the Global Constraints. Expected as stated there.

- [ ] **Step 9: Commit**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git add src/main/java/io/github/alexshamrai/json/CtrfJsonMapper.java src/main/java/io/github/alexshamrai/CtrfReportFileService.java \
  src/test/java/io/github/alexshamrai/json/CtrfJsonMapperTest.java src/test/java/io/github/alexshamrai/json/JsonGoldenTest.java \
  src/test/java/io/github/alexshamrai/CtrfReportFileServiceTest.java
git status --short
git commit -q -F - <<'EOF'
fix: read the report straight into the model, from the file

CtrfJsonMapper.read(Reader, Class) binds tokens directly into the
model's fields, by each field's declared type. It builds a tree only
for Object and Map fields, such as extra, and skips unknown members
token by token. Every reading rule and error message stays.

The file service reads the report through a BufferedReader instead of
Files.readString, so reading a report no longer holds its whole text
and a tree of it next to the model. Invalid UTF-8 is still rejected,
and such a report is still replaced.
EOF
git log --oneline -1
```

---

### Task 3: Write straight from the model to a `Writer`

**Files:**
- Modify: `src/main/java/io/github/alexshamrai/json/JsonWriter.java` (rewritten)
- Modify: `src/main/java/io/github/alexshamrai/json/CtrfJsonMapper.java` (the writing half, and the class Javadoc)
- Modify: `src/main/java/io/github/alexshamrai/CtrfReportFileService.java` (`writeAtomically`)
- Modify: `src/test/java/io/github/alexshamrai/json/JsonGoldenTest.java`
- Modify: `src/test/java/io/github/alexshamrai/json/CtrfJsonMapperTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `public static void CtrfJsonMapper.write(Object model, Writer out) throws IOException`; `CtrfJsonMapper.write(Object)` keeps its signature;
  - `JsonWriter(Writer out)`, package-private, with package-private `value(Object)`, `beginObject()`, `endObject()`, `beginArray()`, `endArray()`, `name(String)` and `flush()`, all `throws IOException`; `JsonWriter.write(Object)` keeps its signature.

- [ ] **Step 1: Write the failing tests**

In `JsonGoldenTest`, add `import java.io.StringWriter;` and:

```java
    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void writerWritesExactlyTheGoldenJsonToAWriter(String name, Object model, String golden) throws IOException {
        var out = new StringWriter();

        CtrfJsonMapper.write(model, out);

        assertThat(out).hasToString(golden);
    }
```

In `CtrfJsonMapperTest`, add `import java.io.StringWriter;` and `import java.io.Writer;`, and:

```java
    @org.junit.jupiter.api.Test
    void ioExceptionFromTheWriterPropagates() {
        Writer failing = new Writer() {
            @Override
            public void write(char[] chars, int offset, int length) throws IOException {
                throw new IOException("disk full");
            }

            @Override
            public void flush() {
                // Nothing buffered
            }

            @Override
            public void close() {
                // Nothing to release
            }
        };

        assertThatThrownBy(() -> CtrfJsonMapper.write(Test.builder().name("t()").build(), failing))
            .isInstanceOf(IOException.class)
            .hasMessage("disk full");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Review Focus 1: a report larger than the writer's buffer is written whole")
    void reportLargerThanTheBufferIsWrittenWhole() throws IOException {
        String trace = "x".repeat(20_000);
        var out = new StringWriter();

        CtrfJsonMapper.write(Test.builder().trace(trace).build(), out);

        assertThat(out).hasToString("{\"duration\":0,\"trace\":\"" + trace + "\"}");
    }
```

- [ ] **Step 2: Run them and watch them fail**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
LOGS=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/streaming-logs
./gradlew :compileTestJava > "$LOGS/t3-red.log" 2>&1; grep -E 'error:|BUILD' "$LOGS/t3-red.log" | head -5
```

Expected: `BUILD FAILED`, with `no suitable method found for write(Object,StringWriter)` or similar.

- [ ] **Step 3: Rewrite `JsonWriter` over a `Writer`**

Replace the whole of `src/main/java/io/github/alexshamrai/json/JsonWriter.java` with:

```java
package io.github.alexshamrai.json;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Map;

/**
 * Writes JSON as compact text, with no whitespace between tokens, to a {@link Writer}.
 * <p>
 * It writes {@code null}, {@link String}, {@link Boolean}, {@link Number}, {@link Map} with {@code String} keys and
 * {@link Iterable}. Strings are escaped as Jackson escapes them: the quotation mark and the backslash, the short
 * escapes for backspace, tab, newline, form feed and carriage return, and a six-character escape with upper-case hex
 * digits for the other control characters. Every other character is written as it is, except surrogates: a character
 * outside the Basic Multilingual Plane, such as an emoji, is written as the escapes of its two surrogates, as Jackson
 * writes it to bytes, and a surrogate without its pair, which UTF-8 can't encode, is escaped the same way.
 * A double or a float that isn't finite is written as a string, such as {@code "NaN"}, as Jackson writes it.
 * <p>
 * It collects text in its own buffer, passes it on whenever the buffer fills, and on {@code flush()}. It puts the
 * commas between members and elements itself.
 */
public final class JsonWriter {

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
    private static final int BUFFER_SIZE = 8192;

    private final Writer out;
    private final char[] buffer = new char[BUFFER_SIZE];
    private int count;
    private boolean afterValue;

    JsonWriter(Writer out) {
        this.out = out;
    }

    /**
     * Writes a JSON value as text.
     *
     * @param value the value
     * @return its JSON text
     * @throws JsonException if the value, or a value inside it, has no JSON form
     */
    public static String write(Object value) {
        var text = new StringWriter();
        try {
            var writer = new JsonWriter(text);
            writer.value(value);
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("A StringWriter doesn't fail", e);
        }
        return text.toString();
    }

    /**
     * Writes a value: {@code null}, a string, a boolean, a number, a map with string keys, or an iterable of values.
     */
    void value(Object value) throws IOException {
        if (value instanceof Map<?, ?> object) {
            beginObject();
            for (Map.Entry<?, ?> member : object.entrySet()) {
                if (!(member.getKey() instanceof String name)) {
                    throw new JsonException("A JSON member name must be a string, not " + member.getKey());
                }
                name(name);
                value(member.getValue());
            }
            endObject();
        } else if (value instanceof Iterable<?> array) {
            beginArray();
            for (Object element : array) {
                value(element);
            }
            endArray();
        } else {
            separate();
            scalar(value);
            afterValue = true;
        }
    }

    void beginObject() throws IOException {
        separate();
        append('{');
        afterValue = false;
    }

    void endObject() throws IOException {
        append('}');
        afterValue = true;
    }

    void beginArray() throws IOException {
        separate();
        append('[');
        afterValue = false;
    }

    void endArray() throws IOException {
        append(']');
        afterValue = true;
    }

    /**
     * Writes a member's name and the colon after it.
     */
    void name(String name) throws IOException {
        separate();
        quote(name);
        append(':');
        afterValue = false;
    }

    /**
     * Passes the buffered text on to the {@code Writer}, and flushes it.
     */
    void flush() throws IOException {
        out.write(buffer, 0, count);
        count = 0;
        out.flush();
    }

    private void separate() throws IOException {
        if (afterValue) {
            append(',');
        }
    }

    private void scalar(Object value) throws IOException {
        if (value == null) {
            append("null");
        } else if (value instanceof String text) {
            quote(text);
        } else if (value instanceof Boolean) {
            append(value.toString());
        } else if (value instanceof Number number) {
            boolean floatingPoint = number instanceof Double || number instanceof Float;
            if (floatingPoint && !Double.isFinite(number.doubleValue())) {
                quote(number.toString());
            } else {
                append(number.toString());
            }
        } else {
            throw new JsonException("No JSON form for a " + value.getClass().getName());
        }
    }

    private void quote(String text) throws IOException {
        append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                append('\\');
                append(c);
            } else if (c < 0x20) {
                controlCharacter(c);
            } else if (Character.isSurrogate(c)) {
                unicodeEscape(c);
            } else {
                append(c);
            }
        }
        append('"');
    }

    private void controlCharacter(char c) throws IOException {
        switch (c) {
            case '\b':
                append("\\b");
                break;
            case '\t':
                append("\\t");
                break;
            case '\n':
                append("\\n");
                break;
            case '\f':
                append("\\f");
                break;
            case '\r':
                append("\\r");
                break;
            default:
                unicodeEscape(c);
        }
    }

    private void unicodeEscape(char c) throws IOException {
        append('\\');
        append('u');
        append(HEX_DIGITS[(c >> 12) & 0xF]);
        append(HEX_DIGITS[(c >> 8) & 0xF]);
        append(HEX_DIGITS[(c >> 4) & 0xF]);
        append(HEX_DIGITS[c & 0xF]);
    }

    private void append(char c) throws IOException {
        if (count == buffer.length) {
            out.write(buffer, 0, count);
            count = 0;
        }
        buffer[count++] = c;
    }

    private void append(String text) throws IOException {
        for (int i = 0; i < text.length(); i++) {
            append(text.charAt(i));
        }
    }
}
```

- [ ] **Step 4: Write the model in one pass**

In `CtrfJsonMapper`:

1. Add the imports `java.io.StringWriter` and `java.io.Writer`.
2. Replace the method `write(Object model)` and the methods `toJsonValue` and `modelToJson` with:

```java
    /**
     * Writes a model object as compact JSON.
     *
     * @param model an object of a model class
     * @return its JSON text
     * @throws JsonException if a value in it can't be written
     */
    public static String write(Object model) {
        var text = new StringWriter();
        try {
            write(model, text);
        } catch (IOException e) {
            throw new UncheckedIOException("A StringWriter doesn't fail", e);
        }
        return text.toString();
    }

    /**
     * Writes a model object as compact JSON to {@code out} as it goes: neither the text nor a tree of it is held
     * whole. {@code out} is flushed, not closed.
     *
     * @param model an object of a model class
     * @param out   where the JSON goes
     * @throws IOException   if {@code out} fails
     * @throws JsonException if a value in it can't be written; what came before it is already in {@code out}
     */
    public static void write(Object model, Writer out) throws IOException {
        var writer = new JsonWriter(out);
        writeValue(model, writer);
        writer.flush();
    }

    private static void writeValue(Object value, JsonWriter out) throws IOException {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number) {
            out.value(value);
        } else if (value instanceof Test.TestStatus status) {
            out.value(status.toLowerCase());
        } else if (value instanceof Enum<?> constant) {
            out.value(constant.name());
        } else if (value instanceof Map<?, ?> map) {
            out.beginObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.name(String.valueOf(entry.getKey()));
                writeValue(entry.getValue(), out);
            }
            out.endObject();
        } else if (value instanceof Iterable<?> items) {
            out.beginArray();
            for (Object item : items) {
                writeValue(item, out);
            }
            out.endArray();
        } else if (value instanceof Object[] items) {
            writeValue(Arrays.asList(items), out);
        } else if (isModel(value.getClass())) {
            writeModel(value, out);
        } else {
            out.value(value.toString());
        }
    }

    private static void writeModel(Object model, JsonWriter out) throws IOException {
        out.beginObject();
        for (Field field : FIELDS.get(model.getClass()).values()) {
            Object value = get(field, model);
            if (value != null) {
                out.name(field.getName());
                writeValue(value, out);
            }
        }
        out.endObject();
    }
```

   `write(Object)` and `write(Object, Writer)` are overloads: keep them next to each other, and keep `read(String, Class)` and `read(Reader, Class)` next to each other.
3. Remove the imports that are now unused (`java.util.LinkedHashMap` stays, because `FIELDS` uses it).
4. In the class Javadoc, after the first sentence ("Writes the CTRF model as JSON and reads it back, without a JSON library."), add: "It streams: it writes to a {@link java.io.Writer} and reads from a {@link java.io.Reader} as it goes, so it never holds a whole report as text or as a tree."

- [ ] **Step 5: Write the temp file through a `Writer`**

In `CtrfReportFileService`, add `import java.io.Writer;`, and replace `writeAtomically` and its Javadoc with:

```java
    /**
     * Streams the report into a hidden temp file next to it, then moves the temp file over the report. Not
     * {@code Files.createTempFile}, which would make the report readable by its owner only. A write that fails
     * part-way deletes the temp file and leaves the report as it was.
     */
    private void writeAtomically(Path report, CtrfJson ctrfJson) {
        Path temp = report.resolveSibling("." + report.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            try (Writer out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                CtrfJsonMapper.write(ctrfJson, out);
            }
            moveOver(temp, report);
        } catch (IOException e) {
            printWriteFailure(e);
        } finally {
            deleteQuietly(temp);
        }
    }
```

The existing `writeThatFailsHalfwayLeavesThePreviousReport` test now fails in the middle of the write, after `{"generatedBy":"second","extra":{"value":` has gone to the temp file: the temp file must still be gone and the report unchanged.

- [ ] **Step 6: Run the tests and watch them pass**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
LOGS=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/streaming-logs
./gradlew :test --tests 'io.github.alexshamrai.json.*' --tests 'io.github.alexshamrai.CtrfReportFileServiceTest' \
  --tests 'io.github.alexshamrai.CtrfSchemaConformanceTest' --tests 'io.github.alexshamrai.ctrf.model.CtrfModelJsonTest' \
  > "$LOGS/t3-green.log" 2>&1
grep -E 'FAILED|BUILD|tests completed' "$LOGS/t3-green.log" | tail -8
```

Expected: `BUILD SUCCESSFUL`. `JsonGoldenTest.writerWritesExactlyTheGoldenJson` and `JsonWriterTest` pass unchanged: the bytes are the same.

- [ ] **Step 7: Check A and Check B**

Run Check A, then Check B, from the Global Constraints. Expected as stated there.

- [ ] **Step 8: Commit**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git add src/main/java/io/github/alexshamrai/json/JsonWriter.java src/main/java/io/github/alexshamrai/json/CtrfJsonMapper.java \
  src/main/java/io/github/alexshamrai/CtrfReportFileService.java \
  src/test/java/io/github/alexshamrai/json/JsonGoldenTest.java src/test/java/io/github/alexshamrai/json/CtrfJsonMapperTest.java
git status --short
git commit -q -F - <<'EOF'
fix: write the report straight from the model to the file

CtrfJsonMapper.write(Object, Writer) walks the model and writes each
value as it goes, through JsonWriter's own 8 KB buffer, with the same
rules and escaping. The file service writes the temp file through a
BufferedWriter. Writing a report no longer builds a tree of maps, a
StringBuilder, a String and a byte array of it: about 200 MB for a
report of 100,000 tests. The output is byte-identical, as the golden
JSON shows.

A write that fails part-way still deletes the temp file and leaves the
report as it was.
EOF
git log --oneline -1
```

---

### Task 4: Replace another run's report after reading only its `runId`

**Files:**
- Modify: `src/main/java/io/github/alexshamrai/json/CtrfJsonMapper.java` (`readRunId`)
- Modify: `src/main/java/io/github/alexshamrai/CtrfReportFileService.java` (the `update` overload)
- Modify: `src/main/java/io/github/alexshamrai/CtrfReportManager.java:291`
- Modify: `src/test/java/io/github/alexshamrai/json/CtrfJsonMapperTest.java`
- Modify: `src/test/java/io/github/alexshamrai/CtrfReportFileServiceTest.java`
- Modify: `src/test/java/io/github/alexshamrai/CtrfReportManagerTest.java:68-72, 204, 405`

**Interfaces:**
- Consumes: `JsonReader` (Task 1), `CtrfJsonMapper.readValue` and `read(Reader, Class)` (Task 2).
- Produces:
  - `public static String CtrfJsonMapper.readRunId(Reader in) throws IOException`;
  - `public void CtrfReportFileService.update(String runId, UnaryOperator<CtrfJson> merge)`; `update(UnaryOperator<CtrfJson>)` keeps its behaviour.

- [ ] **Step 1: Write the failing tests**

In `CtrfJsonMapperTest`, add:

```java
    @org.junit.jupiter.api.Test
    void runIdIsReadWithoutReadingTheRest() throws IOException {
        assertThat(runIdOf("{\"reportFormat\":\"CTRF\",\"runId\":\"run-1\",\"results\": this is never read")).isEqualTo("run-1");
    }

    @org.junit.jupiter.api.Test
    void runIdAfterTheResultsIsFound() throws IOException {
        assertThat(runIdOf("{\"results\":{\"tests\":[{\"name\":\"t()\",\"extra\":{\"a\":[1,{}]}}]},\"runId\":\"run-1\"}"))
            .isEqualTo("run-1");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Review Focus 4: the runId of a pretty-printed report with Windows line endings is found after its results")
    void runIdOfAPrettyPrintedReportWithWindowsLineEndingsIsFound() throws IOException {
        String json = "{\r\n  \"results\" : {\r\n    \"tests\" : [ ]\r\n  },\r\n  \"runId\" : \"run-1\"\r\n}\r\n";

        assertThat(runIdOf(json)).isEqualTo("run-1");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Review Focus 3: the runId of a report starting with a byte order mark is found")
    void runIdOfAReportStartingWithAByteOrderMarkIsFound() throws IOException {
        assertThat(runIdOf((char) 0xFEFF + "{\"runId\":\"run-1\"}")).isEqualTo("run-1");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("A report of 0.4, or with a null runId, has no run id")
    void reportWithoutARunIdHasNone() throws IOException {
        assertThat(runIdOf("{\"reportFormat\":\"CTRF\",\"specVersion\":\"0.0.0\",\"results\":{\"tests\":[]}}")).isNull();
        assertThat(runIdOf("{}")).isNull();
        assertThat(runIdOf("{\"runId\":null}")).isNull();
    }

    @org.junit.jupiter.api.Test
    void numericRunIdIsReadAsText() throws IOException {
        assertThat(runIdOf("{\"runId\":123}")).isEqualTo("123");
    }

    @org.junit.jupiter.api.Test
    void runIdCantBeReadFromTextThatIsNotAReport() {
        assertThatThrownBy(() -> runIdOf("[]")).isInstanceOf(JsonException.class).hasMessage("$: expected an object");
        assertThatThrownBy(() -> runIdOf("{\"results\":[1,]}"))
            .isInstanceOf(JsonException.class)
            .hasMessage("Unexpected character ']' at character 14");
        assertThatThrownBy(() -> runIdOf("{\"runId\":{}}")).isInstanceOf(JsonException.class).hasMessage("$.runId: expected a string");
    }

    private static String runIdOf(String json) throws IOException {
        return CtrfJsonMapper.readRunId(new StringReader(json));
    }
```

In `CtrfReportFileServiceTest`, add:

```java
    @Test
    @DisplayName("A report of another run is replaced after reading only its runId, without a warning")
    void reportOfAnotherRunIsReplacedAfterReadingOnlyItsRunId() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        Files.writeString(report, "{\"reportFormat\":\"CTRF\",\"runId\":\"earlier-run\",\"results\":{ the rest is never read");
        List<CtrfJson> seen = new ArrayList<>();

        serviceFor(report).update("this-run", existing -> {
            seen.add(existing);
            return marker("fresh");
        });

        assertThat(seen).containsExactly((CtrfJson) null);
        assertThat(readMarker(report)).isEqualTo("fresh");
        assertThat(logs.text()).isEmpty();
    }

    @Test
    @DisplayName("A report without a runId, as 0.4 wrote them, is replaced without a warning")
    void reportWithoutARunIdIsReplaced() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        Files.writeString(report, "{\"reportFormat\":\"CTRF\",\"specVersion\":\"0.0.0\",\"results\":{\"tests\":[]}}");
        List<CtrfJson> seen = new ArrayList<>();

        serviceFor(report).update("this-run", existing -> {
            seen.add(existing);
            return marker("fresh");
        });

        assertThat(seen).containsExactly((CtrfJson) null);
        assertThat(logs.text()).isEmpty();
    }

    @Test
    @DisplayName("A report of the same run reaches the merge function")
    void reportOfTheSameRunReachesTheMerge() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        Files.writeString(report, "{\"runId\":\"this-run\",\"generatedBy\":\"first\"}");
        List<String> seen = new ArrayList<>();

        serviceFor(report).update("this-run", existing -> {
            seen.add(existing.getGeneratedBy());
            return marker("second");
        });

        assertThat(seen).containsExactly("first");
        assertThat(readMarker(report)).isEqualTo("second");
    }

    @Test
    @DisplayName("A damaged report of the same run reaches the merge function as null, with a warning")
    void damagedReportOfTheSameRunIsReplacedWithAWarning() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        Files.writeString(report, "{\"runId\":\"this-run\",\"results\":{ not json");
        List<CtrfJson> seen = new ArrayList<>();

        serviceFor(report).update("this-run", existing -> {
            seen.add(existing);
            return marker("fresh");
        });

        assertThat(seen).containsExactly((CtrfJson) null);
        assertThat(readMarker(report)).isEqualTo("fresh");
        assertThat(logs.text()).contains("can't be read");
    }

    @Test
    @DisplayName("Review Focus 5: update without a runId still passes a report of any run to the merge function")
    void updateWithoutARunIdReadsAReportOfAnyRun() throws IOException {
        Path report = tempDir.resolve("ctrf.json");
        Files.writeString(report, "{\"runId\":\"earlier-run\",\"generatedBy\":\"first\"}");
        List<String> seen = new ArrayList<>();

        serviceFor(report).update(existing -> {
            seen.add(existing.getGeneratedBy());
            return marker("second");
        });

        assertThat(seen).containsExactly("first");
    }
```

In `CtrfReportManagerTest`, change the stub in `setUp` (lines 68–72) to:

```java
        doAnswer(invocation -> {
            UnaryOperator<CtrfJson> merge = invocation.getArgument(1);
            reportOnDisk = merge.apply(reportOnDisk);
            return null;
        }).when(ctrfReportFileService).update(eq(RUN_ID), any());
```

and both `verify(ctrfReportFileService).update(any());` (lines 204 and 405) to:

```java
        verify(ctrfReportFileService).update(eq(RUN_ID), any());
```

`eq` and `any` are already imported.

- [ ] **Step 2: Run them and watch them fail**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
LOGS=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/streaming-logs
./gradlew :compileTestJava > "$LOGS/t4-red.log" 2>&1; grep -E 'error:|BUILD' "$LOGS/t4-red.log" | head -5
```

Expected: `BUILD FAILED`, with `cannot find symbol` for `readRunId` and for `update(String, …)`.

- [ ] **Step 3: Add `readRunId`**

In `CtrfJsonMapper`, after `read(Reader, Class)`:

```java
    /**
     * Reads a report's {@code runId} without reading the rest of it: the root object's members are read in order,
     * each skipped without being built, until the first {@code runId}.
     *
     * @param in the report's JSON text, not closed
     * @return the run id as text, converted as {@link #read(Reader, Class)} converts it, or {@code null} if the report
     *         has none
     * @throws IOException   if {@code in} fails
     * @throws JsonException if the text isn't a JSON object, or isn't valid JSON before the {@code runId}
     */
    public static String readRunId(Reader in) throws IOException {
        var reader = new JsonReader(in);
        if (reader.peek() != JsonReader.Kind.OBJECT) {
            throw new JsonException("$: expected an object");
        }
        if (reader.beginObject()) {
            do {
                if ("runId".equals(reader.nextName())) {
                    return (String) readValue(reader, String.class, "$.runId");
                }
                reader.skipValue();
            } while (reader.nextMember());
        }
        return null;
    }
```

Keep `read(String, Class)` and `read(Reader, Class)` next to each other; `readRunId` goes after them.

- [ ] **Step 4: Add `update(String runId, UnaryOperator)`**

In `CtrfReportFileService`, add `import java.util.function.Function;`. Replace the method `update(UnaryOperator<CtrfJson> merge)` and the method `readExisting` with these. Keep the Javadoc of the first `update` as it is today.

```java
    public void update(UnaryOperator<CtrfJson> merge) {
        replace(this::readExisting, merge);
    }

    /**
     * Like {@link #update(UnaryOperator)}, but reads the current report only if it belongs to the run {@code runId}.
     * A report of another run, or one without a run id, is replaced after reading only its {@code runId}, however
     * large it is.
     *
     * @param runId the run being reported
     * @param merge receives the current report if it belongs to {@code runId}; {@code null} if it doesn't, if there
     *              is none, or if it can't be parsed. Returns the report to write
     */
    public void update(String runId, UnaryOperator<CtrfJson> merge) {
        replace(report -> readExistingOfRun(report, runId), merge);
    }

    private void replace(Function<Path, CtrfJson> readCurrent, UnaryOperator<CtrfJson> merge) {
        Path report = Paths.get(configReader.getReportPath()).toAbsolutePath();
        synchronized (JVM_LOCK) {
            try {
                Files.createDirectories(report.getParent());
            } catch (IOException e) {
                printWriteFailure(e);
                return;
            }
            FileLock lock = lockAcrossJvms(report);
            try {
                writeAtomically(report, merge.apply(readCurrent.apply(report)));
            } catch (RuntimeException e) {
                LOGGER.warning("Failed to write results to file: " + configReader.getReportPath() + " - " + e);
            } finally {
                release(lock);
            }
        }
    }
```

and, where `readExisting` was:

```java
    private CtrfJson readExisting(Path report) {
        if (!Files.exists(report)) {
            return null;
        }
        try (Reader in = Files.newBufferedReader(report, StandardCharsets.UTF_8)) {
            return CtrfJsonMapper.read(in, CtrfJson.class);
        } catch (IOException | RuntimeException e) {
            return ignoreUnreadable(report, e);
        }
    }

    // A report whose runId isn't runId, or that has none, is replaced without reading its tests
    private CtrfJson readExistingOfRun(Path report, String runId) {
        if (!Files.exists(report)) {
            return null;
        }
        try (Reader in = Files.newBufferedReader(report, StandardCharsets.UTF_8)) {
            if (!runId.equals(CtrfJsonMapper.readRunId(in))) {
                return null;
            }
        } catch (IOException | RuntimeException e) {
            return ignoreUnreadable(report, e);
        }
        return readExisting(report);
    }

    private static CtrfJson ignoreUnreadable(Path report, Exception e) {
        LOGGER.warning("Ignoring the existing report, which can't be read: " + report + " - " + e.getMessage());
        return null;
    }
```

The two public `update` methods are overloads: keep them next to each other, with the Javadoc of today's `update` on the first one. `replace` has a different name, so it may follow them.

- [ ] **Step 5: Call it from the manager**

In `CtrfReportManager`, line 291, change

```java
        fileService.update(existing -> {
```

to

```java
        fileService.update(runId, existing -> {
```

`runId` is the manager's field. `ReportMerger.merge` keeps its own `runId` comparison: if another JVM replaced the report between the two reads, after its lock timed out, the merger still keeps another run's tests out.

- [ ] **Step 6: Run the tests and watch them pass**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
LOGS=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/streaming-logs
./gradlew :test --tests 'io.github.alexshamrai.json.*' --tests 'io.github.alexshamrai.CtrfReportFileServiceTest' \
  --tests 'io.github.alexshamrai.CtrfReportManagerTest' --tests 'io.github.alexshamrai.scenario.*' > "$LOGS/t4-green.log" 2>&1
grep -E 'FAILED|BUILD|tests completed' "$LOGS/t4-green.log" | tail -8
```

Expected: `BUILD SUCCESSFUL`. The scenario tests cover forks, retry rounds, same-JVM reruns and new builds through the real file: the same run still merges, another run is still replaced.

- [ ] **Step 7: Check A and Check B**

Run Check A, then Check B, from the Global Constraints. Expected as stated there.

- [ ] **Step 8: Commit**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git add src/main/java/io/github/alexshamrai/json/CtrfJsonMapper.java src/main/java/io/github/alexshamrai/CtrfReportFileService.java \
  src/main/java/io/github/alexshamrai/CtrfReportManager.java src/test/java/io/github/alexshamrai/json/CtrfJsonMapperTest.java \
  src/test/java/io/github/alexshamrai/CtrfReportFileServiceTest.java src/test/java/io/github/alexshamrai/CtrfReportManagerTest.java
git status --short
git commit -q -F - <<'EOF'
fix: replace another run's report after reading only its runId

At the end of a run, the manager now calls the new
CtrfReportFileService.update(runId, merge). It reads the report's
runId with CtrfJsonMapper.readRunId, which stops there; only a report
of the same run is read whole. A rerun therefore no longer loads the
previous run's report, however large: at 100,000 tests, it needs as
little heap as a first write.

update(UnaryOperator) stays as it was, since the class is public.
ReportMerger keeps its own runId check.
EOF
git log --oneline -1
```

---

### Task 5: Heap guard, and the docs

**Files:**
- Create: `src/test/java/io/github/alexshamrai/scenario/HeapGuardMain.java`
- Create: `src/test/java/io/github/alexshamrai/scenario/HeapGuardScenarioTest.java`
- Modify: `CLAUDE.md` (lines 119, 122, 138, 148 and 289)

**Interfaces:**
- Consumes: the public `CtrfReportManager.getInstance()`, `startTestRun(String)`, `onTestStart(TestDetails)`, `onTestSuccess(String)`, `onTestFailure(String, Throwable)`, `finishTestRun()`, and `io.github.alexshamrai.model.TestDetails(long, Set<String>, String, String, String)`.
- Produces: nothing other tasks use.

- [ ] **Step 1: Write the guard**

Create `src/test/java/io/github/alexshamrai/scenario/HeapGuardMain.java`:

```java
package io.github.alexshamrai.scenario;

import io.github.alexshamrai.CtrfReportManager;
import io.github.alexshamrai.model.TestDetails;

import java.util.Set;

/**
 * Entry point of the JVM that {@link HeapGuardScenarioTest} starts: records tests through the manager's public API,
 * every tenth failing with the same 1.7 KB trace, and ends the run, which writes the report.
 *
 * <p>Usage: {@code HeapGuardMain <count> <number of the first test>}
 */
public final class HeapGuardMain {

    private HeapGuardMain() {
    }

    public static void main(String[] args) {
        int count = Integer.parseInt(args[0]);
        int first = Integer.parseInt(args[1]);
        var manager = CtrfReportManager.getInstance();
        manager.startTestRun("heap-guard");
        AssertionError failure = failureWithFrames(40);
        for (int n = first; n < first + count; n++) {
            String type = "com.example.payments.service.PaymentServiceIntegrationTest" + (n / 50);
            String id = "[engine:junit-jupiter]/[class:" + type + "]/[method:paysTheInvoice" + n + "()]";
            manager.onTestStart(new TestDetails(System.currentTimeMillis(), Set.of("smoke", "payments"), type, id,
                "paysTheInvoice" + n + "()"));
            if (n % 10 == 0) {
                manager.onTestFailure(id, failure);
            } else {
                manager.onTestSuccess(id);
            }
        }
        manager.finishTestRun();
    }

    private static AssertionError failureWithFrames(int frames) {
        if (frames == 0) {
            return new AssertionError("expected: <200 OK> but was: <500 Internal Server Error> for POST /payments/42");
        }
        return failureWithFrames(frames - 1);
    }
}
```

Create `src/test/java/io/github/alexshamrai/scenario/HeapGuardScenarioTest.java`. `MAX_HEAP_MB` is set in Step 3; write `176` for now.

```java
package io.github.alexshamrai.scenario;

import io.github.alexshamrai.ctrf.model.CtrfJson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the heap that a large report needs from growing again. 0.5.1 held the report as text and as a tree of maps,
 * and needed 280 to 328 MB for 100,000 tests. Here each JVM records its tests under a fixed maximum heap, and must
 * still write a complete report.
 */
class HeapGuardScenarioTest {

    /**
     * The most this code needs on Java 17, 21 or 25 for any of the three cases, plus about 25%. Measured 2026-10-16
     * with the heap benchmark of the 0.5.2 design.
     */
    private static final int MAX_HEAP_MB = 176;
    private static final int TESTS = 100_000;

    @TempDir
    Path directory;

    @Test
    @DisplayName("A first write of 100,000 tests fits the heap")
    void firstWriteFitsTheHeap() throws Exception {
        Path report = directory.resolve("ctrf-report.json");

        record(report, "first-run", TESTS, 0);

        CtrfJson written = read(report);
        assertThat(written.getRunId()).isEqualTo("first-run");
        assertThat(written.getResults().getTests()).hasSize(TESTS);
    }

    @Test
    @DisplayName("A rerun that replaces another run's report of 100,000 tests fits the heap")
    void replacingAnotherRunsReportFitsTheHeap() throws Exception {
        Path report = directory.resolve("ctrf-report.json");
        record(report, "earlier-run", TESTS, 0);

        record(report, "rerun", TESTS, TESTS);

        CtrfJson written = read(report);
        assertThat(written.getRunId()).isEqualTo("rerun");
        assertThat(written.getResults().getTests()).hasSize(TESTS);
    }

    @Test
    @DisplayName("Two forks of 50,000 tests each, merged into one report, fit the heap")
    void mergingForksFitsTheHeap() throws Exception {
        Path report = directory.resolve("ctrf-report.json");
        record(report, "forks", TESTS / 2, 0);

        record(report, "forks", TESTS / 2, TESTS / 2);

        assertThat(read(report).getResults().getTests()).hasSize(TESTS);
    }

    private void record(Path report, String runId, int count, int first) throws IOException, InterruptedException {
        Path output = directory.resolve(runId + "-" + first + ".txt");
        Process process = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-Xmx" + MAX_HEAP_MB + "m",
            "-cp", System.getProperty("java.class.path"),
            "-Dctrf.report.path=" + report,
            "-Dctrf.run.id=" + runId,
            HeapGuardMain.class.getName(), String.valueOf(count), String.valueOf(first))
            .directory(directory.toFile())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start();
        if (!process.waitFor(3, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IllegalStateException("The guard JVM timed out:\n" + Files.readString(output));
        }
        assertThat(process.exitValue()).as(Files.readString(output)).isZero();
    }

    private static CtrfJson read(Path report) {
        return JsonMapper.builder().build().readValue(report.toFile(), CtrfJson.class);
    }
}
```

Each JVM gets an explicit `ctrf.run.id`, so the cases don't depend on how `RunIdResolver` would derive one from the Gradle worker or a CI variable.

- [ ] **Step 2: Measure what this code needs**

Build the jar, compile the benchmark against it, and run it on the three JDKs. Each JDK takes about four minutes.

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad
B=$SCRATCH/heap-bench
LOGS=$SCRATCH/streaming-logs
mkdir -p "$LOGS"
./gradlew -q :jar
echo "$PWD/build/libs/junit-ctrf-reporter-0.5.2-SNAPSHOT.jar" > "$B/cp-streaming.txt"
rm -rf "$B/classes-streaming" && javac -d "$B/classes-streaming" -cp "$(cat "$B/cp-streaming.txt")" "$B/src/HeapBench.java"
for J in 17.0.12-tem 21.0.3-tem 25-tem; do
  echo "== $J"
  BENCH_JAVA=$HOME/.sdkman/candidates/java/$J/bin/java python3 "$B/bisect.py" streaming 100000 10 first replace merge
done 2>&1 | tee "$LOGS/heap-measure.log"
```

Run it in the background, and read `$LOGS/heap-measure.log` when it finishes.

Expected: three lines per JDK, `first`, `replace` and `merge`, each in MB. By the spec's stand-in: about 128, 128 and 144 MB, and `replace` no more than `first`. Compare the JDK 21 lines with the spec's bar: no scenario above 0.5.0 (128, 224 and 144 MB), and replace no more than first. If a JDK 21 number is more than 8 MB above 0.5.0's, stop and investigate before going on. Likely causes are a buffer that isn't reused, or a string or path built for every token.

- [ ] **Step 3: Set the limit**

`MAX_HEAP_MB` is the largest of the nine numbers, times 1.25, rounded up to a multiple of 8. For example, a largest number of 144 MB gives 180, rounded up to 184. Write it into `HeapGuardScenarioTest.MAX_HEAP_MB`. The limit must stay below 280, 0.5.1's lowest need; if it doesn't, stop and investigate.

- [ ] **Step 4: Watch the guard fail on 0.5.1's code**

Run the guard against master's code in a scratch worktree:

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
SCRATCH=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad
LOGS=$SCRATCH/streaming-logs
RED=$SCRATCH/heap-guard-red
git worktree add -q --detach "$RED" 6ff69d5
cp src/test/java/io/github/alexshamrai/scenario/HeapGuardMain.java src/test/java/io/github/alexshamrai/scenario/HeapGuardScenarioTest.java \
  "$RED/src/test/java/io/github/alexshamrai/scenario/"
(cd "$RED" && ./gradlew :test --tests 'io.github.alexshamrai.scenario.HeapGuardScenarioTest' > "$LOGS/t5-red.log" 2>&1)
grep -E 'HeapGuardScenarioTest > .* (FAILED|PASSED)|OutOfMemoryError|tests completed|BUILD' "$LOGS/t5-red.log" | sort -u | head -10
git worktree remove --force "$RED"
```

Expected: `BUILD FAILED`, all three tests `FAILED`, and `java.lang.OutOfMemoryError` in the log. The scratch worktree holds only master's files and the two copied tests, so removing it with `--force` loses nothing.

- [ ] **Step 5: Watch the guard pass on the branch**

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
LOGS=/private/tmp/claude-502/-Users-oleksiishamrai-personal-dev-junit-ctrf-extension/30243f6c-138f-413c-84d5-4d8f9ad536be/scratchpad/streaming-logs
./gradlew :test --tests 'io.github.alexshamrai.scenario.HeapGuardScenarioTest' > "$LOGS/t5-green.log" 2>&1
grep -E 'FAILED|BUILD|tests completed' "$LOGS/t5-green.log" | tail -5
```

Expected: `BUILD SUCCESSFUL`, 3 tests. Check A then also runs the guard in `testOnJava17` and `testOnJava21`.

- [ ] **Step 6: Update `CLAUDE.md`**

Make these replacements:

1. Line 119, in the data-flow block: `    ├─ Reads the report fresh` becomes `    ├─ Reads the report's runId; reads the whole report, streaming, only if it's the same run`.
2. Line 122: `    └─ Writes a temp file and moves it over the report (default: ctrf-report.json)` becomes `    └─ Streams a temp file and moves it over the report (default: ctrf-report.json)`.
3. Line 138, the `CtrfReportFileService` row of the components table: replace the cell `` `update`: locked fresh read, merge function, atomic write `` with `` `update`: locked read of the same run's report, merge function, atomic streaming write ``, padded with spaces to the table's column width. This keeps the width:

   ```bash
   cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
   python3 - <<'PY'
   p = 'CLAUDE.md'; lines = open(p).read().split('\n')
   old = "`update`: locked fresh read, merge function, atomic write"
   new = "`update`: locked read of the same run's report, merge function, atomic streaming write"
   i = next(n for n, line in enumerate(lines) if old in line)
   cell = lines[i].split('|')[2]
   lines[i] = lines[i].replace(cell, ' ' + new.ljust(len(cell) - 1))
   open(p, 'w').write('\n'.join(lines))
   PY
   ```

4. Line 148, the "Jackson annotations" bullet: after "with `io.github.alexshamrai.json.CtrfJsonMapper`, which applies the same rules." add " It streams: it reads from a `Reader` and writes to a `Writer`, so it never holds a whole report as text or as a tree."
5. Line 289, in "Rerun Support": replace "At the end of each execution, `CtrfReportFileService.update` reads the report under a lock. If its root `runId` matches, `ReportMerger` unfolds its tests and appends this execution's attempts," with "At the end of each execution, `CtrfReportFileService.update` reads the report's root `runId` under a lock. Only if it matches is the whole report read, and `ReportMerger` unfolds its tests and appends this execution's attempts,", and the sentence "A report of another run is replaced." with "A report of another run is replaced without being read further, and `HeapGuardScenarioTest` keeps the heap a report of 100,000 tests needs under a fixed limit."

Check with `git diff CLAUDE.md` that exactly these five places changed.

- [ ] **Step 7: Check A and Check B**

Run Check A, then Check B, from the Global Constraints. Expected as stated there. In `$LOGS/build.log`, the three guard tests pass in each of `test`, `testOnJava17` and `testOnJava21`.

- [ ] **Step 8: Commit**

Replace `<LIMIT>` with `MAX_HEAP_MB` from Step 3.

```bash
cd /Users/oleksiishamrai/personal_dev/junit-ctrf-extension-fixes
git add src/test/java/io/github/alexshamrai/scenario/HeapGuardMain.java \
  src/test/java/io/github/alexshamrai/scenario/HeapGuardScenarioTest.java CLAUDE.md
git status --short
git commit -q -F - <<'EOF'
test: keep the heap that a large report needs from growing again

A forked JVM records 100,000 tests through the manager's public API
under a maximum heap of <LIMIT> MB: for a first write, for a rerun that
replaces another run's report, and for two forks merged into one
report. The limit is the most this code needs on Java 17, 21 or 25,
plus about 25%. 0.5.1 needed 280 to 328 MB and fails it.

CLAUDE.md describes the streaming and the runId check.
EOF
git log --oneline -6
```

Expected: five commits on top of `6ff69d5`.

---

## After the last task

- **Benchmark numbers for the PR:** the JDK 21 lines of `$LOGS/heap-measure.log`, next to the spec's table (0.5.1: 280, 328 and 280 MB; 0.5.0: 128, 224 and 144 MB).
- **Final whole-branch review**, as the execution skill describes, with this plan's Review Focus and the notes above.
- **Push:** `git push -u origin fix/streaming-json`. The user opens the PR in the browser; the `gh` account here can only read. Write the PR description to `$SCRATCH/streaming-pr-body.md`, with no attribution lines, and give the user the compare URL `https://github.com/alexshamrai/junit-ctrf-reporter/compare/master...fix/streaming-json?expand=1`. The PR is rebase-merged, so the five commits stay.
- **After the merge,** on `feature/improvements-plan`: the 0.5.2 design's status becomes Done, with the PR number and commits; the release notes get the measured numbers; the acceptance criteria are ticked; the streaming follow-up is marked done. Then release 0.5.2 with the "Prepare Release" workflow.
