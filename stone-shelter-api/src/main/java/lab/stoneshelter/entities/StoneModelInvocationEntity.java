package lab.stoneshelter.entities;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class StoneModelInvocationEntity {
    private String endpoint;
    private String system;
    private String message;
    private List<StoneChatTurnEntity> history;
    private Map<String, Object> schema;
    private int contextTokens;
    private int outputTokens;
    private Instant deadline;
    private boolean includeExactSearchExample = true;
    public StoneModelInvocationEntity() {}
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getSystem() { return system; }
    public void setSystem(String system) { this.system = system; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<StoneChatTurnEntity> getHistory() { return history; }
    public void setHistory(List<StoneChatTurnEntity> history) { this.history = history; }
    public Map<String, Object> getSchema() { return schema; }
    public void setSchema(Map<String, Object> schema) { this.schema = schema; }
    public int getContextTokens() { return contextTokens; }
    public void setContextTokens(int contextTokens) { this.contextTokens = contextTokens; }
    public int getOutputTokens() { return outputTokens; }
    public void setOutputTokens(int outputTokens) { this.outputTokens = outputTokens; }
    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant deadline) { this.deadline = deadline; }
    public boolean getIncludeExactSearchExample() { return includeExactSearchExample; }
    public void setIncludeExactSearchExample(boolean includeExactSearchExample) { this.includeExactSearchExample = includeExactSearchExample; }
}
