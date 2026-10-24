package lab.stoneshelter.entities;

public record StoneDocumentSourceEntity(byte[] bytes, String sourceVersion) {
    public StoneDocumentSourceEntity { bytes = bytes.clone(); }
    @Override public byte[] bytes() { return bytes.clone(); }
}
