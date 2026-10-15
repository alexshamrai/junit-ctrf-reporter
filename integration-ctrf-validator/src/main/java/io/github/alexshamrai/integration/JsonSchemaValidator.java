package io.github.alexshamrai.integration;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import java.io.InputStream;
import java.util.List;

/**
 * Validates JSON documents against a JSON schema in draft-07 mode, the draft of the official CTRF schema.
 */
public class JsonSchemaValidator {

    private final Schema schema;

    public JsonSchemaValidator(InputStream schemaJson) {
        this.schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(schemaJson);
    }

    /**
     * Returns one message per violation, each starting with the JSON pointer of the value that fails, such as
     * {@code /results/tests/0: property 'filepath' is not defined in the schema ...}. Empty when the document is valid.
     */
    public List<String> validate(String json) {
        return schema.validate(json, InputFormat.JSON).stream().map(Object::toString).toList();
    }
}
