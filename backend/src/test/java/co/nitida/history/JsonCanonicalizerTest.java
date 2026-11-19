package co.nitida.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.nitida.history.domain.JsonCanonicalizer;
import co.nitida.shared.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

/** RFC 8785 vectors (D3) and the P4 properties. */
class JsonCanonicalizerTest {

    @Test
    void sortsKeysAndDropsWhitespace() {
        var m = new LinkedHashMap<String, Object>();
        m.put("b", 2);
        m.put("a", List.of(true, false, "x"));
        m.put("c", null);
        assertThat(JsonCanonicalizer.canonicalize(m)).isEqualTo("{\"a\":[true,false,\"x\"],\"b\":2,\"c\":null}");
    }

    /** RFC 8785 section 3.2.3: keys are sorted by UTF-16 code units, not by code points. */
    @Test
    void sortsByUtf16CodeUnits() {
        var m = new LinkedHashMap<String, Object>();
        m.put("\u20ac", "Euro Sign");
        m.put("\r", "Carriage Return");
        m.put("\ufb33", "Hebrew Letter Dalet With Dagesh");
        m.put("1", "One");
        m.put("\ud83d\ude00", "Emoji: Grinning Face");
        m.put("\u0080", "Control");
        m.put("\u00f6", "Latin Small Letter O With Diaeresis");
        assertThat(JsonCanonicalizer.canonicalize(m)).isEqualTo(
                "{\"\\r\":\"Carriage Return\",\"1\":\"One\",\"\u0080\":\"Control\",\"\u00f6\":\"Latin Small Letter O With Diaeresis\","
                        + "\"\u20ac\":\"Euro Sign\",\"\ud83d\ude00\":\"Emoji: Grinning Face\",\"\ufb33\":\"Hebrew Letter Dalet With Dagesh\"}");
    }

    /** RFC 8785 section 3.2.2 string escaping example. */
    @Test
    void escapesLikeJsonStringify() {
        assertThat(JsonCanonicalizer.canonicalize("\u20ac$\u000f\nA'B\"\\\\\"/")).isEqualTo("\"\u20ac$\\u000f\\nA'B\\\"\\\\\\\\\\\"/\"");
        assertThat(JsonCanonicalizer.canonicalize("\b\f\t\u001f\u007f")).isEqualTo("\"\\b\\f\\t\\u001f\u007f\"");
    }

    @Test
    void loneSurrogatesAreEscapedButPairsAreNot() {
        assertThat(JsonCanonicalizer.canonicalize("\ud800")).isEqualTo("\"\\ud800\"");
        assertThat(JsonCanonicalizer.canonicalize("\ud83d\ude00")).isEqualTo("\"\ud83d\ude00\"");
    }

    @Test
    void integersOnlyAndUnknownTypesAreRejected() {
        assertThat(JsonCanonicalizer.canonicalize(List.of(0, -7, 123456789012L))).isEqualTo("[0,-7,123456789012]");
        assertThatThrownBy(() -> JsonCanonicalizer.canonicalize(1.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JsonCanonicalizer.canonicalize(new Object())).isInstanceOf(IllegalArgumentException.class);
    }

    @Provide
    Arbitrary<Map<String, Object>> documents() {
        Arbitrary<Object> leaf = Arbitraries.oneOf(
                Arbitraries.strings().ofMaxLength(12).map(s -> (Object) s),
                Arbitraries.longs().between(-1_000_000, 1_000_000).map(l -> (Object) l),
                Arbitraries.of(true, false).map(b -> (Object) b));
        Arbitrary<Object> value = Arbitraries.oneOf(leaf, leaf.list().ofMaxSize(3).map(l -> (Object) l));
        return Arbitraries.maps(Arbitraries.strings().ofMinLength(1).ofMaxLength(6), value).ofMaxSize(6);
    }

    /** P4: key order does not change the canonical form; canonicalizing the parse of a canonical form is a no-op. */
    @Property
    void keyOrderDoesNotMatterAndCanonicalFormIsStable(@ForAll("documents") Map<String, Object> doc) {
        var reversed = new LinkedHashMap<String, Object>();
        new java.util.ArrayList<>(doc.keySet()).reversed().forEach(k -> reversed.put(k, doc.get(k)));
        String canonical = JsonCanonicalizer.canonicalize(doc);
        assertThat(JsonCanonicalizer.canonicalize(reversed)).isEqualTo(canonical);
        Object reparsed = Json.mapper().readValue(canonical, Object.class);
        assertThat(JsonCanonicalizer.canonicalize(reparsed)).isEqualTo(canonical);
    }
}
