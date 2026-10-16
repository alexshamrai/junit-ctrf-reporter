package io.github.alexshamrai.integration;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * What the CTRF report of a run must contain, according to JUnit's own event log for that run.
 *
 * <p>The integration modules enable JUnit's Open Test Reporting, which writes an {@code open-test-report.xml} for
 * each JVM: each fork and each retry round. Each node's unique ID there is the report's {@code testId}.</p>
 */
public final class JunitEvents {

    private static final String EVENTS = "https://schemas.opentest4j.org/reporting/events/0.2.0";
    private static final String CORE = "https://schemas.opentest4j.org/reporting/core/0.2.0";
    private static final String JUNIT = "https://schemas.junit.org/open-test-reporting";
    private static final String CLOSING_TAG = "</e:events>";
    private static final String ROOT_START = "<e:events";
    private static final String STARTED_END = "</e:started>";
    private static final String FINISHED_END = "</e:finished>";
    private static final String FILE_NAME = "open-test-report.xml";
    private static final Set<String> CLASS_ERRORS = Set.of("initializationError", "teardownError");

    private final Map<String, Expected> expectedEntries;
    private final Set<String> skippedContainers;
    private final List<String> truncatedLogs;
    private final Map<String, Node> nodes;

    /**
     * An entry the report must contain.
     *
     * @param testId  the JUnit unique ID
     * @param status  the CTRF status of the final attempt: "passed", "failed" or "skipped"
     * @param retries the attempts before the final one
     * @param flaky   whether the final attempt passed after an earlier failed one
     */
    public record Expected(String testId, String status, int retries, boolean flaky) {
    }

    private record Attempt(Instant finished, String status) {
    }

    private record History(String type, List<Attempt> attempts) {
    }

    private record Source(String name, String xml) {
    }

    /**
     * A node JUnit started: its display name, and its parent's unique ID, {@code null} for an engine.
     */
    private record Node(String name, String parent) {
    }

    private JunitEvents(Map<String, Expected> expectedEntries, Set<String> skippedContainers, List<String> truncatedLogs,
                        Map<String, Node> nodes) {
        this.expectedEntries = Collections.unmodifiableMap(expectedEntries);
        this.skippedContainers = Collections.unmodifiableSet(skippedContainers);
        this.truncatedLogs = Collections.unmodifiableList(truncatedLogs);
        this.nodes = Collections.unmodifiableMap(nodes);
    }

    /**
     * Reads every {@code open-test-report.xml} below {@code eventsDir}.
     *
     * @throws IOException           if there is none, or a file can't be read
     * @throws IllegalStateException if a file isn't an Open Test Reporting event log; the message names the file
     */
    public static JunitEvents read(Path eventsDir) throws IOException {
        List<Source> documents = new ArrayList<>();
        if (Files.isDirectory(eventsDir)) {
            try (Stream<Path> files = Files.walk(eventsDir)) {
                for (Path file : files.filter(path -> path.getFileName().toString().equals(FILE_NAME)).sorted().toList()) {
                    documents.add(new Source(file.toString(), Files.readString(file)));
                }
            }
        }
        if (documents.isEmpty()) {
            throw new IOException("No JUnit event log (" + FILE_NAME + ") under " + eventsDir.toAbsolutePath()
                + ". The integration modules write it on every test run: run the integration test task again,"
                + " or pass -Dctrf.junit.events.dir=<directory>.");
        }
        return fold(documents);
    }

    /**
     * Folds the event documents of one run, one per JVM, in any order. Errors name a document as "document 1", "document 2", ...
     *
     * @throws IllegalStateException if a document isn't an Open Test Reporting event log
     */
    public static JunitEvents fromXml(List<String> documents) {
        List<Source> sources = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            sources.add(new Source("document " + (i + 1), documents.get(i)));
        }
        return fold(sources);
    }

    private static JunitEvents fold(List<Source> sources) {
        Map<String, History> histories = new LinkedHashMap<>();
        Map<String, Node> nodes = new HashMap<>();
        List<String> truncated = new ArrayList<>();
        for (Source source : sources) {
            String xml = source.xml().trim();
            if (!xml.endsWith(CLOSING_TAG)) {
                // A JVM that died mid-run never wrote the closing tag: keep what it logged completely
                xml = closeAfterLastCompleteEvent(source, xml);
                truncated.add(source.name());
            }
            collect(source, parse(source, xml), histories, nodes);
        }

        Map<String, Expected> expected = new LinkedHashMap<>();
        Set<String> skipped = new LinkedHashSet<>();
        histories.forEach((testId, history) -> {
            // The engine has no '/'; a node that never finished belongs to a JVM that died mid-test
            if (!testId.contains("/") || history.attempts().isEmpty()) {
                return;
            }
            List<String> statuses = history.attempts().stream()
                .sorted(Comparator.comparing(Attempt::finished))
                .map(Attempt::status)
                .toList();
            String last = statuses.get(statuses.size() - 1);
            boolean everFailed = statuses.contains("FAILED");
            if (history.type().equals("CONTAINER") && !everFailed) {
                if (last.equals("SKIPPED") || last.equals("ABORTED")) {
                    skipped.add(testId);
                }
                return;
            }
            String status = ctrfStatus(last);
            boolean flaky = status.equals("passed") && statuses.subList(0, statuses.size() - 1).contains("FAILED");
            expected.put(testId, new Expected(testId, status, statuses.size() - 1, flaky));
        });
        return new JunitEvents(expected, skipped, truncated, nodes);
    }

    /**
     * The entries the report must contain, by {@code testId}.
     */
    public Map<String, Expected> expectedEntries() {
        return expectedEntries;
    }

    /**
     * Containers JUnit skipped or aborted as a whole. JUnit never starts their tests, so the log can't list them;
     * the report must have at least one entry under each, all skipped.
     */
    public Set<String> skippedContainers() {
        return skippedContainers;
    }

    /**
     * Whether {@code testId} is something the report may contain: an entry JUnit expects, or the skipped container itself
     * or anything within it (JUnit never starts the tests of a skipped container, so its log can't list them).
     */
    public boolean accountsFor(String testId) {
        return expectedEntries.containsKey(testId) || skippedContainers.stream().anyMatch(container -> within(testId, container));
    }

    /**
     * The skipped containers with no expected entry within them. JUnit's log can't list their tests, so the report must
     * have at least one entry within each. A container aborted after its tests ran is not among them.
     */
    public Set<String> containersWithoutListedTests() {
        Set<String> result = new LinkedHashSet<>();
        for (String container : skippedContainers) {
            if (expectedEntries.keySet().stream().noneMatch(testId -> within(testId, container))) {
                result.add(container);
            }
        }
        return result;
    }

    /**
     * Whether {@code testId} is {@code container} or below it.
     */
    public static boolean within(String testId, String container) {
        return testId.equals(container) || testId.startsWith(container + "/");
    }

    /**
     * The {@code suite} the report must give the entry for {@code testId}, by the names in JUnit's log: the display
     * names of the containers above it, outermost first, without any engine, also one that a {@code @Suite} class runs,
     * or the nearest engine's name when there are none. A class error, an entry named "initializationError" or "teardownError", is grouped under its class: the class's
     * own name comes last, and the engine's name is never used.
     *
     * @param testId    the entry's unique ID
     * @param entryName the entry's name in the report
     * @return the suite, or empty if JUnit never started {@code testId}
     */
    public Optional<List<String>> expectedSuite(String testId, String entryName) {
        Node node = nodes.get(testId);
        if (node == null) {
            return Optional.empty();
        }
        List<String> containers = new ArrayList<>();
        String engine = isEngine(testId) ? node.name() : null;
        for (String id = node.parent(); id != null && nodes.containsKey(id); id = nodes.get(id).parent()) {
            Node parent = nodes.get(id);
            if (!isEngine(id)) {
                containers.add(0, parent.name());
            } else if (engine == null) {
                engine = parent.name();
            }
        }
        if (CLASS_ERRORS.contains(entryName)) {
            containers.add(node.name());
            return Optional.of(containers);
        }
        return Optional.of(containers.isEmpty() ? List.of(Objects.requireNonNullElse(engine, node.name())) : containers);
    }

    // The last segment of an engine's unique ID, as JUnit writes it: "[engine:junit-jupiter]"
    private static boolean isEngine(String uniqueId) {
        return uniqueId.substring(uniqueId.lastIndexOf('/') + 1).startsWith("[engine:");
    }

    /**
     * The event logs that end early, because their JVM died: the file paths, or "document N" for {@link #fromXml}.
     * Each was read up to its last complete event.
     */
    public List<String> truncatedLogs() {
        return truncatedLogs;
    }

    private static void collect(Source source, Document document, Map<String, History> histories, Map<String, Node> nodes) {
        Element root = document.getDocumentElement();
        if (!EVENTS.equals(root.getNamespaceURI()) || !"events".equals(root.getLocalName())) {
            throw new IllegalStateException(source.name() + " isn't an event log this validator reads: its root element is '"
                + root.getLocalName() + "' in namespace '" + root.getNamespaceURI() + "', but the validator reads"
                + " Open Test Reporting events 0.2.0 ('events' in " + EVENTS + "). Did a JUnit upgrade change the format?");
        }
        // Event ids are numbered per file, so they map to unique IDs per document
        Map<String, String> uniqueIds = new HashMap<>();
        NodeList started = document.getElementsByTagNameNS(EVENTS, "started");
        for (int i = 0; i < started.getLength(); i++) {
            Element event = (Element) started.item(i);
            String uniqueId = text(source, event, "uniqueId");
            uniqueIds.put(event.getAttribute("id"), uniqueId);
            // A parent starts before its children, so its unique ID is already known
            nodes.putIfAbsent(uniqueId, new Node(event.getAttribute("name"), uniqueIds.get(event.getAttribute("parentId"))));
            histories.computeIfAbsent(uniqueId, id -> new History(text(source, event, "type"), new ArrayList<>()));
        }
        NodeList finished = document.getElementsByTagNameNS(EVENTS, "finished");
        for (int i = 0; i < finished.getLength(); i++) {
            Element event = (Element) finished.item(i);
            String uniqueId = uniqueIds.get(event.getAttribute("id"));
            if (uniqueId == null) {
                continue;
            }
            Element result = (Element) event.getElementsByTagNameNS(CORE, "result").item(0);
            if (result == null) {
                throw new IllegalStateException(source.name() + ": finished event " + event.getAttribute("id") + " has no result");
            }
            String status = result.getAttribute("status");
            ctrfStatus(status, source);
            try {
                histories.get(uniqueId).attempts().add(new Attempt(Instant.parse(event.getAttribute("time")), status));
            } catch (DateTimeParseException e) {
                throw new IllegalStateException(source.name() + ": finished event " + event.getAttribute("id")
                    + " has an unreadable time '" + event.getAttribute("time") + "'", e);
            }
        }
    }

    private static String text(Source source, Element event, String junitElement) {
        Element element = (Element) event.getElementsByTagNameNS(JUNIT, junitElement).item(0);
        if (element == null) {
            throw new IllegalStateException(source.name() + ": started event " + event.getAttribute("id") + " has no " + junitElement);
        }
        return element.getTextContent();
    }

    private static String ctrfStatus(String junitStatus) {
        return ctrfStatus(junitStatus, null);
    }

    private static String ctrfStatus(String junitStatus, Source source) {
        return switch (junitStatus) {
            case "SUCCESSFUL" -> "passed";
            case "FAILED" -> "failed";
            case "ABORTED", "SKIPPED" -> "skipped";
            default -> throw new IllegalStateException((source == null ? "" : source.name() + ": ")
                + "Unknown JUnit result status: " + junitStatus);
        };
    }

    /**
     * Cuts a truncated log right after its last complete started or finished event, or after the root start tag if
     * it has none, and closes the root. An event cut in the middle is dropped.
     */
    private static String closeAfterLastCompleteEvent(Source source, String xml) {
        int rootStart = xml.indexOf(ROOT_START);
        int rootEnd = rootStart < 0 ? -1 : xml.indexOf('>', rootStart);
        if (rootEnd < 0) {
            throw new IllegalStateException(source.name() + " isn't a JUnit event log: it has no <e:events> root element");
        }
        int lastStarted = xml.lastIndexOf(STARTED_END);
        int lastFinished = xml.lastIndexOf(FINISHED_END);
        int cut = Math.max(Math.max(lastStarted < 0 ? -1 : lastStarted + STARTED_END.length(),
            lastFinished < 0 ? -1 : lastFinished + FINISHED_END.length()), rootEnd + 1);
        return xml.substring(0, cut) + CLOSING_TAG;
    }

    private static Document parse(Source source, String document) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(document)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalStateException("Can't parse the JUnit event log " + source.name(), e);
        }
    }
}
