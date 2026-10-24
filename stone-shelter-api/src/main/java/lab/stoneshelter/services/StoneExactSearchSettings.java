package lab.stoneshelter.services;

import java.time.Duration;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class StoneExactSearchSettings {
    public static final String INSTRUCTIONS = """
            Interpret English stone-catalog requests. Return only the required JSON decision.
            Support only type, size, availability and admission date. Use SEARCH with narrowing type,
            size or date conditions; OR values within each group and AND groups. Only AVAILABLE stones
            are visible. For all available stones with no narrowing condition use CLARIFY and ask at
            least two specific questions about type, size or admission date. Use UNSUPPORTED for color,
            intended use, care or geology; explain exact supported conditions without dropping wishes.
            Empty criteria arrays mean no constraint. Dates use UTC inclusive-start/exclusive-end
            ISO timestamps; today is %s, yesterday %s. A date without a year requires
            clarification. Use resetSearch=false for a follow-up and true for an explicit new search.
            References are ORDINAL (one-based) or PAGE_CONTEXT; never invent stone IDs or names.
            Without a reference use reference=null. For SEARCH use questions=[] and reason="".
            """;
    public static final String SCHEMA = """
            {"type":"object","additionalProperties":false,
             "required":["action","criteria","resetSearch","reference","questions","reason"],
             "properties":{
               "action":{"type":"string","enum":["CLARIFY","SEARCH","UNSUPPORTED"]},
               "criteria":{"type":"object","additionalProperties":false,
                 "required":["stoneTypes","stoneSizes","availability","admissionDateRanges"],
                 "properties":{
                   "stoneTypes":{"type":"array","items":{"type":"string","enum":["BASALT","GRANITE","OBSIDIAN","PUMICE","LIMESTONE","SANDSTONE","SHALE","MARBLE","GNEISS","SLATE"]}},
                   "stoneSizes":{"type":"array","items":{"type":"string","enum":["SMALL","MEDIUM","LARGE"]}},
                   "availability":{"type":"array","items":{"type":"string","enum":["AVAILABLE","RESERVED","ADOPTED"]}},
                   "admissionDateRanges":{"type":"array","items":{"type":"object","additionalProperties":false,
                     "required":["startInclusive","endExclusive"],
                     "properties":{"startInclusive":{"type":"string"},"endExclusive":{"type":"string"}}}}
                 }},
               "resetSearch":{"type":"boolean"},
               "reference":{"anyOf":[{"type":"null"},{"type":"object","additionalProperties":false,
                 "required":["kind","ordinal"],"properties":{"kind":{"type":"string","enum":["ORDINAL","PAGE_CONTEXT"]},"ordinal":{"type":["integer","null"]}}}]},
               "questions":{"type":"array","items":{"type":"string"}},
               "reason":{"type":"string"}
             }}
            """;

    private final String endpoint;
    private final int contextTokens;
    private final int outputTokens;
    private final Duration callTimeout;
    public static final int REPAIR_PROMPT_ALLOWANCE = 200;
    public static final String QUALIFIED_TEMPLATE_SHA256 = "eb4402837c7829a690fa845de4d7f3fd842c2adee476d5341da8a46ea9255175";

    public StoneExactSearchSettings(Environment environment) {
        endpoint = environment.getProperty("CHAT_MODEL_OLLAMA_BASE_URL",
                environment.getProperty("SPRING_AI_OLLAMA_BASE_URL",
                        environment.getProperty("STONE_DOCUMENT_OLLAMA_BASE_URL")));
        contextTokens = environment.getProperty("CHAT_MODEL_CONTEXT_TOKENS", Integer.class, 8192);
        outputTokens = environment.getProperty("CHAT_MODEL_OUTPUT_TOKENS", Integer.class, 512);
        callTimeout = Duration.parse(environment.getProperty("CHAT_MODEL_CALL_TIMEOUT", "PT50S"));
        if (endpoint == null || endpoint.isBlank() || contextTokens < 8192 || contextTokens > 32768 || outputTokens < 1 || outputTokens > 512
                || callTimeout.isNegative() || callTimeout.isZero() || callTimeout.compareTo(Duration.ofSeconds(50)) > 0) {
            throw new IllegalArgumentException("Unqualified exact-search settings");
        }
    }

    public String endpoint() { return endpoint; }
    public int contextTokens() { return contextTokens; }
    public int outputTokens() { return outputTokens; }
    public Duration callTimeout() { return callTimeout; }

    public String systemInstructions(LocalDate utcDate, String repairReason) {
        String correction = repairReason == null ? "" : "\nCorrect invalid output: " + repairReason;
        if (correction.getBytes(StandardCharsets.UTF_8).length > REPAIR_PROMPT_ALLOWANCE) {
            throw new IllegalArgumentException("Repair instruction exceeds its qualified prompt allowance");
        }
        return INSTRUCTIONS.formatted(utcDate, utcDate.minusDays(1)) + correction;
    }

    public static boolean isExplicitAvailabilityOnly(String request) {
        return request.strip().toLowerCase(Locale.ROOT).matches(
                "(?:please\\s+)?(?:(?:show|list|find|search(?:\\s+for)?|give\\s+me)\\s+)?(?:me\\s+)?(?:all\\s+)?(?:the\\s+)?available\\s+stones[.!?]?");
    }

    public static Map<String, Object> schemaFor(String request, boolean inheritedNarrowing) {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        ObjectNode schema = (ObjectNode) jsonMapper.readTree(SCHEMA);
        if (!inheritedNarrowing && isExplicitAvailabilityOnly(request)) {
            ObjectNode properties = (ObjectNode) schema.get("properties");
            ((ObjectNode) properties.get("action")).putArray("enum").add("CLARIFY");
            ((ObjectNode) properties.get("questions")).put("minItems", 2);
            ObjectNode criteria = (ObjectNode) properties.get("criteria").get("properties");
            for (String name : new String[]{"stoneTypes", "stoneSizes", "admissionDateRanges"}) {
                ((ObjectNode) criteria.get(name)).put("maxItems", 0);
            }
        }
        return jsonMapper.convertValue(schema, Map.class);
    }
}
