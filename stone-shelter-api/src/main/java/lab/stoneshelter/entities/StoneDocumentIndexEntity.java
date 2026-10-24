package lab.stoneshelter.entities;

import java.util.UUID;

public class StoneDocumentIndexEntity {
    public static final String DOCUMENT_KEY = "stone-encyclopedia-en";
    public static final String LANGUAGE = "en";

    private String documentKey;
    private String sourceVersion;
    private String embeddingProfile;
    private String language;
    private int indexedFragments;
    private UUID activeOperationKey;
    private UUID ownerToken;

    public StoneDocumentIndexEntity() {
    }

    public String getDocumentKey() { return documentKey; }
    public void setDocumentKey(String documentKey) { this.documentKey = documentKey; }
    public String getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(String sourceVersion) { this.sourceVersion = sourceVersion; }
    public String getEmbeddingProfile() { return embeddingProfile; }
    public void setEmbeddingProfile(String embeddingProfile) { this.embeddingProfile = embeddingProfile; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public int getIndexedFragments() { return indexedFragments; }
    public void setIndexedFragments(int indexedFragments) { this.indexedFragments = indexedFragments; }
    public UUID getActiveOperationKey() { return activeOperationKey; }
    public void setActiveOperationKey(UUID activeOperationKey) { this.activeOperationKey = activeOperationKey; }
    public UUID getOwnerToken() { return ownerToken; }
    public void setOwnerToken(UUID ownerToken) { this.ownerToken = ownerToken; }
}
