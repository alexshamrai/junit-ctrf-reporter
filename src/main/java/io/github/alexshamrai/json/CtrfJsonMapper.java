package io.github.alexshamrai.json;

import io.github.alexshamrai.ctrf.model.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the CTRF model as JSON and reads it back, without a JSON library. It streams: it writes to a
 * {@link java.io.Writer} and reads from a {@link java.io.Reader} as it goes, so it never holds a whole report as text
 * or as a tree.
 * <p>
 * The model is the classes of {@code io.github.alexshamrai.ctrf.model}. Each is bound field by field, in declaration
 * order, under the field's name; static fields are skipped. The rules are what the model's Jackson annotations do,
 * written out here because the annotations aren't on the classpath at run time:
 * <ul>
 *   <li>writing: a null field is left out, an empty list is kept, a primitive field is always written, and a null
 *       inside a map or a list is written as {@code null};</li>
 *   <li>a test status is written in lower case, and read ignoring case; an unknown status can't be read;</li>
 *   <li>reading: a string holding a whole number fills a number field, a number or a boolean fills a text field,
 *       {@code null} fills a primitive field with 0 or false, {@code filepath} fills {@code filePath}, and unknown
 *       fields are ignored.</li>
 * </ul>
 * In the values of a {@code Map<String, Object>} or {@code List<Object>} field, a model object is written as one,
 * an enum constant as its name, and any other object that has no JSON form as its {@code toString()}.
 */
public final class CtrfJsonMapper {

    private static final String MODEL_PACKAGE = "io.github.alexshamrai.ctrf.model";

    /**
     * Other names a field is read from: reports written before 0.5.0 call {@code filePath} {@code filepath}.
     */
    private static final Map<String, String> ALIASES = Map.of("filepath", "filePath");

    private static final ClassValue<Map<String, Field>> FIELDS = new ClassValue<>() {
        @Override
        protected Map<String, Field> computeValue(Class<?> type) {
            Map<String, Field> fields = new LinkedHashMap<>();
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    field.setAccessible(true);
                    fields.put(field.getName(), field);
                }
            }
            return fields;
        }
    };

    private CtrfJsonMapper() {
        // Utility class - prevent instantiation
    }

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

    /**
     * Reads a report's {@code runId} without reading the rest of it: the root object's members are read in order,
     * each skipped without being built, until the first {@code runId}.
     *
     * @param in the report's JSON text, not closed
     * @return the run id as text, converted as {@link #read(Reader, Class)} converts it, or {@code null} if the report
     *         has none, or is the literal {@code null}, which the full read reads as no report
     * @throws IOException   if {@code in} fails
     * @throws JsonException if the text isn't a JSON object or {@code null}, or isn't valid JSON before the
     *                       {@code runId}
     */
    public static String readRunId(Reader in) throws IOException {
        var reader = new JsonReader(in);
        JsonReader.Kind kind = reader.peek();
        if (kind == JsonReader.Kind.NULL) {
            return null;
        }
        if (kind != JsonReader.Kind.OBJECT) {
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

    private static Object nullValueOf(Class<?> type) {
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == boolean.class) {
            return false;
        }
        return null;
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

    private static String toText(Object json, String path) {
        if (json instanceof String text) {
            return text;
        }
        if (json instanceof Number || json instanceof Boolean) {
            return json.toString();
        }
        throw new JsonException(path + ": expected a string");
    }

    private static int toInt(Object json, String path) {
        long value = toLong(json, path);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new JsonException(path + ": " + json + " is out of range");
        }
        return (int) value;
    }

    private static long toLong(Object json, String path) {
        if (json instanceof Integer || json instanceof Long) {
            return ((Number) json).longValue();
        }
        try {
            if (json instanceof BigInteger number) {
                return number.longValueExact();
            }
            if (json instanceof Double number) {
                return wholePart(number);
            }
            if (json instanceof String text) {
                // 0.4.x reports wrote buildNumber as a string
                return Long.parseLong(text.strip());
            }
        } catch (ArithmeticException | NumberFormatException e) {
            throw new JsonException(path + ": \"" + json + "\" is not a whole number in range", e);
        }
        throw new JsonException(path + ": expected a number, not " + json);
    }

    // A decimal read into a whole-number field keeps its whole part, as with Jackson
    private static long wholePart(double number) {
        if (!Double.isFinite(number) || number < Long.MIN_VALUE || number >= -(double) Long.MIN_VALUE) {
            throw new ArithmeticException("out of range");
        }
        return (long) number;
    }

    private static boolean toBoolean(Object json, String path) {
        if (json instanceof Boolean value) {
            return value;
        }
        if (json instanceof String text && ("true".equalsIgnoreCase(text.strip()) || "false".equalsIgnoreCase(text.strip()))) {
            return Boolean.parseBoolean(text.strip());
        }
        if (json instanceof Integer || json instanceof Long) {
            return ((Number) json).longValue() != 0;
        }
        throw new JsonException(path + ": expected true or false, not " + json);
    }

    private static Test.TestStatus toStatus(Object json, String path) {
        if (json instanceof String text) {
            for (Test.TestStatus status : Test.TestStatus.values()) {
                if (status.name().equalsIgnoreCase(text)) {
                    return status;
                }
            }
        }
        throw new JsonException(path + ": unknown test status " + json);
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

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> raw) {
            return raw;
        }
        if (type instanceof ParameterizedType parameterized) {
            return (Class<?>) parameterized.getRawType();
        }
        return Object.class;
    }

    private static Type elementType(Type listType) {
        return listType instanceof ParameterizedType parameterized ? parameterized.getActualTypeArguments()[0] : Object.class;
    }

    private static boolean isModel(Class<?> type) {
        return MODEL_PACKAGE.equals(type.getPackageName()) && !type.isEnum() && !type.isInterface();
    }

    private static Object newInstance(Class<?> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new JsonException("Can't create a " + type.getName(), e);
        }
    }

    private static Object get(Field field, Object model) {
        try {
            return field.get(model);
        } catch (IllegalAccessException e) {
            throw new JsonException("Can't read " + field, e);
        }
    }

    private static void set(Field field, Object model, Object value) {
        try {
            field.set(model, value);
        } catch (IllegalAccessException e) {
            throw new JsonException("Can't set " + field, e);
        }
    }
}
