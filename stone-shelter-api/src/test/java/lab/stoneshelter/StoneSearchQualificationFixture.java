package lab.stoneshelter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import lab.stoneshelter.criteria.StoneDocumentEmbeddingCriteria;
import lab.stoneshelter.entities.StoneDocumentEmbeddingInputEntity;
import lab.stoneshelter.enums.ModelAdmissionMode;
import lab.stoneshelter.enums.StoneSize;
import lab.stoneshelter.enums.StoneType;
import lab.stoneshelter.mappers.entities.StoneCardSaveRequestToStoneCardEmbeddingEntityMapper;
import lab.stoneshelter.repositories.ModelTokenBudgetRepository;
import lab.stoneshelter.repositories.StoneExactSearchRepository;
import lab.stoneshelter.repositories.StoneRagKnowledgeRepository;
import lab.stoneshelter.repositories.StoneSemanticSearchRepository;
import lab.stoneshelter.services.ModelTokenBudgetTransactionService;
import lab.stoneshelter.services.StoneDocumentEmbeddingService;
import lab.stoneshelter.shared.StoneCardSaveRequest;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The persistent isolated target keeps actual model accounting when fixture rows are removed. */
final class StoneSearchQualificationFixture implements AutoCloseable {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String DATABASE = "stone_search_qualification_0027";
    static final String PROFILE = StoneDocumentEmbeddingInputEntity.PROFILE + "/ac6da0dfba84a81fdbfbaf330198c33cd77c4cdfc53e8bc50eb581914a15621d";
    static final String DOCUMENT = "stone-encyclopedia-en";
    private JsonNode resource;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbcTemplate;
    private final Map<String, Long> ids = new LinkedHashMap<>();
    private final Map<String, String> vectors = new LinkedHashMap<>();
    private final Map<String, String> texts = new LinkedHashMap<>();
    private String version;

    StoneSearchQualificationFixture() throws Exception {
        try (var input = getClass().getResourceAsStream("/search-query-qualification.json")) {
            if (input == null) throw new IllegalStateException("Approved fixture resource missing");
            byte[] bytes = input.readAllBytes(); resource = JSON.readTree(bytes);
            version = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
    }
    JsonNode resource() { return resource; }
    Map<String, Long> ids() { return Map.copyOf(ids); }
    String version() { return version; }
    Map<String, String> vectorCache() { return Map.copyOf(vectors); }
    JdbcTemplate jdbcTemplate() { return jdbcTemplate; }
    <T> T bean(Class<T> type) { return context.getBean(type); }
    Clock calendar() { return Clock.fixed(Instant.parse(resource.path("calendar").asString()), ZoneOffset.UTC); }

    void connect() throws Exception {
        String url = System.getenv("STONE_QUALIFICATION_JDBC_URL");
        if (!"jdbc:postgresql://127.0.0.1:55427/".concat(DATABASE).equals(url))
            throw new IllegalStateException("Refusing any target except the explicitly owned qualification database");
        DataSource dataSource = new DriverManagerDataSource(url, required("STONE_QUALIFICATION_USERNAME"), required("STONE_QUALIFICATION_PASSWORD"));
        jdbcTemplate = new JdbcTemplate(dataSource);
        if (!DATABASE.equals(jdbcTemplate.queryForObject("SELECT current_database()", String.class))) throw new IllegalStateException("Target isolation failed");
        var liquibase = new SpringLiquibase(); liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml"); liquibase.afterPropertiesSet();
        context = new AnnotationConfigApplicationContext(); context.registerBean(DataSource.class, () -> dataSource);
        context.registerBean(JdbcTemplate.class, () -> jdbcTemplate);
        context.register(StoneSemanticRetrievalTest.Transactions.class, StoneExactSearchRepository.class,
                StoneSemanticSearchRepository.class, StoneRagKnowledgeRepository.class,
                ModelTokenBudgetRepository.class, ModelTokenBudgetTransactionService.class); context.refresh();
        if (jdbcTemplate.queryForObject("SELECT count(*) FROM stone", Long.class) != 0
                || jdbcTemplate.queryForObject("SELECT count(*) FROM stone_document_index", Long.class) != 0)
            throw new IllegalStateException("Owned target contains an unfinished fixture; preserve and inspect it before a new baseline");
    }
    void seed() {
        for (JsonNode card : resource.path("cards")) {
            long id = jdbcTemplate.queryForObject("INSERT INTO stone(storage_uuid,name,stone_type,size,adoption_status,admission_date,biography) VALUES(?,?,?,?,?,?,?) RETURNING id",
                    Long.class, UUID.randomUUID(), card.path("name").asString(), card.path("type").asString(), card.path("size").asString(), card.path("status").asString(),
                    Timestamp.from(Instant.parse(card.path("admissionDate").asString())), biography(card));
            ids.put(card.path("key").asString(), id);
        }
    }
    void embed(StoneDocumentEmbeddingService embedding, Clock clock) {
        for (JsonNode card : resource.path("cards")) {
            StoneCardSaveRequest request = new StoneCardSaveRequest(); request.setStoneId(ids.get(card.path("key").asString()));
            request.setName(card.path("name").asString()); request.setStoneType(StoneType.valueOf(card.path("type").asString()));
            request.setStoneSize(StoneSize.valueOf(card.path("size").asString())); request.setBiography(biography(card));
            String text = new StoneCardSaveRequestToStoneCardEmbeddingEntityMapper().toEntity(request).getText();
            String key = card.path("key").asString();
            var vector = embedding.embed(new StoneDocumentEmbeddingCriteria(text, false, clock.instant().plusSeconds(50), ModelAdmissionMode.CUSTOMER));
            if (!PROFILE.equals(vector.profile())) throw new IllegalStateException("Approved embedding digest changed");
            vectors.put(key, Arrays.toString(vector.vector())); texts.put(key, text);
            jdbcTemplate.update("INSERT INTO stone_card_embedding(stone_id,text,embedding_profile,embedding) VALUES(?,?,?,?::vector)", ids.get(key), text, vector.profile(), vectors.get(key));
        }
        jdbcTemplate.update("INSERT INTO stone_document_index(document_key,source_version,embedding_profile,language,indexed_fragments) VALUES(?,?,?,'en',7)", DOCUMENT, version, PROFILE);
        for (JsonNode passage : resource.path("passages")) {
            String text = passage.path("type").asString() + " > " + passage.path("section").asString() + "\n" + passage.path("text").asString();
            var vector = embedding.embed(new StoneDocumentEmbeddingCriteria(text, false, clock.instant().plusSeconds(50), ModelAdmissionMode.CUSTOMER));
            if (!PROFILE.equals(vector.profile())) throw new IllegalStateException("Approved embedding digest changed");
            vectors.put(passage.path("key").asString(), Arrays.toString(vector.vector()));
            jdbcTemplate.update("INSERT INTO stone_document_fragment(document_key,fragment_order,source_version,embedding_profile,language,stone_type,section_path,passage_text,embedding) VALUES(?,?,?,?,'en',?,?,?,?::vector)",
                    DOCUMENT, passage.path("order").asInt(), version, vector.profile(), passage.path("type").asString(), passage.path("section").asString(), passage.path("text").asString(), Arrays.toString(vector.vector()));
        }
    }
    void restoreCompatibleVectors(Map<String, String> cache) {
        if (cache.size() != 22) throw new IllegalStateException("Incomplete retained fixture vectors");
        for (String vector : cache.values()) {
            JsonNode coordinates = JSON.readTree(vector);
            if (!coordinates.isArray() || coordinates.size() != 1024) throw new IllegalStateException("Incompatible cached vector");
            for (JsonNode value : coordinates) if (!value.isNumber() || !Double.isFinite(value.asDouble())) throw new IllegalStateException("Invalid cached coordinate");
        }
        vectors.putAll(cache);
        for (JsonNode card : resource.path("cards")) {
            String key = card.path("key").asString();
            StoneCardSaveRequest request = new StoneCardSaveRequest(); request.setStoneId(ids.get(key)); request.setName(card.path("name").asString());
            request.setStoneType(StoneType.valueOf(card.path("type").asString())); request.setStoneSize(StoneSize.valueOf(card.path("size").asString())); request.setBiography(biography(card));
            texts.put(key, new StoneCardSaveRequestToStoneCardEmbeddingEntityMapper().toEntity(request).getText());
            jdbcTemplate.update("INSERT INTO stone_card_embedding(stone_id,text,embedding_profile,embedding) VALUES(?,?,?,?::vector)", ids.get(key), texts.get(key), PROFILE, vectors.get(key));
        }
        jdbcTemplate.update("INSERT INTO stone_document_index(document_key,source_version,embedding_profile,language,indexed_fragments) VALUES(?,?,?,'en',7)", DOCUMENT, version, PROFILE);
        for (JsonNode passage : resource.path("passages")) jdbcTemplate.update("INSERT INTO stone_document_fragment(document_key,fragment_order,source_version,embedding_profile,language,stone_type,section_path,passage_text,embedding) VALUES(?,?,?,?,'en',?,?,?,?::vector)",
                DOCUMENT, passage.path("order").asInt(), version, PROFILE, passage.path("type").asString(), passage.path("section").asString(), passage.path("text").asString(), vectors.get(passage.path("key").asString()));
    }
    void restore() {
        for (JsonNode card : resource.path("cards")) {
            String key = card.path("key").asString();
            jdbcTemplate.update("INSERT INTO stone(id,storage_uuid,name,stone_type,size,adoption_status,admission_date,biography) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET adoption_status=excluded.adoption_status,biography=excluded.biography",
                    ids.get(key), UUID.randomUUID(), card.path("name").asString(), card.path("type").asString(), card.path("size").asString(), card.path("status").asString(),
                    Timestamp.from(Instant.parse(card.path("admissionDate").asString())), biography(card));
            jdbcTemplate.update("INSERT INTO stone_card_embedding(stone_id,text,embedding_profile,embedding) VALUES(?,?,?,?::vector) ON CONFLICT(stone_id) DO NOTHING",
                    ids.get(key), texts.get(key), PROFILE, vectors.get(key));
        }
    }
    void reserve(String key) { jdbcTemplate.update("UPDATE stone SET adoption_status='RESERVED' WHERE id=?", ids.get(key)); }
    void delete(String key) { jdbcTemplate.update("DELETE FROM stone_card_embedding WHERE stone_id=?", ids.get(key)); jdbcTemplate.update("DELETE FROM stone WHERE id=?", ids.get(key)); }
    String key(long id) { return ids.entrySet().stream().filter(entry -> entry.getValue() == id).map(Map.Entry::getKey).findFirst().orElse("UNOWNED:" + id); }
    JsonNode card(String key) { for (JsonNode card : resource.path("cards")) if (card.path("key").asString().equals(key)) return card; throw new IllegalArgumentException(key); }
    Map<String, Object> accounting() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("actualUtcDay", LocalDate.now(ZoneOffset.UTC).toString());
        value.put("days", jdbcTemplate.queryForList("SELECT budget_day::text AS budget_day,charged_tokens FROM model_token_budget_day ORDER BY budget_day"));
        value.put("reservations", jdbcTemplate.queryForList("SELECT id,budget_day::text AS budget_day,reserved_tokens,actual_tokens FROM model_token_reservation ORDER BY id"));
        return value;
    }
    private String biography(JsonNode card) { return card.path("biography").isNull() ? null : card.path("biography").asString(); }
    private String required(String key) { String value = System.getenv(key); if (value == null || value.isBlank()) throw new IllegalStateException(key + " missing"); return value; }
    @Override public void close() {
        if (jdbcTemplate != null && !ids.isEmpty()) {
            jdbcTemplate.update("DELETE FROM stone_document_fragment WHERE document_key=? AND source_version=?", DOCUMENT, version);
            jdbcTemplate.update("DELETE FROM stone_document_index WHERE document_key=? AND source_version=?", DOCUMENT, version);
            for (long id : ids.values()) { jdbcTemplate.update("DELETE FROM stone_card_embedding WHERE stone_id=?", id); jdbcTemplate.update("DELETE FROM stone WHERE id=?", id); }
        }
        // Actual ledger/reservations deliberately survive approved fixture cleanup.
        if (context != null) context.close();
    }
}
