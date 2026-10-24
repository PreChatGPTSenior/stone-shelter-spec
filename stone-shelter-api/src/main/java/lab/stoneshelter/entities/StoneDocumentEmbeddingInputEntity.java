package lab.stoneshelter.entities;

import java.time.Duration;

public class StoneDocumentEmbeddingInputEntity {
    public static final String MODEL = "qwen3-embedding:0.6b";
    public static final int DIMENSIONS = 1024;
    public static final String QUERY_INSTRUCTION =
            "Instruct: Given a web search query, retrieve relevant passages that answer the query\nQuery:";
    public static final String PROFILE = "qwen3-embedding:0.6b/1024/plain-document/qwen-retrieval-query-v1";

    private String endpoint;
    private String text;
    private Duration connectTimeout;
    private Duration requestTimeout;

    public StoneDocumentEmbeddingInputEntity() {
    }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
}
