package io.github.alexshamrai;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * The official CTRF schema, pinned at {@code schema/ctrf.schema.json}, for the tests that check reports against it.
 */
public final class CtrfSchema {

    private static final Schema SCHEMA = loadPinnedSchema();

    private CtrfSchema() {
    }

    /**
     * Validates a report against the schema.
     *
     * @param report the report's JSON text
     * @return the schema errors, empty if the report conforms
     */
    public static List<String> errorsOf(String report) {
        return SCHEMA.validate(report, InputFormat.JSON).stream().map(Object::toString).toList();
    }

    private static Schema loadPinnedSchema() {
        try (InputStream schema = CtrfSchema.class.getResourceAsStream("/ctrf.schema.json")) {
            if (schema == null) {
                throw new IllegalStateException("ctrf.schema.json is not on the test classpath; see schema/README.md");
            }
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(schema);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
