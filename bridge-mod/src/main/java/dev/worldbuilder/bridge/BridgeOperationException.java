package dev.worldbuilder.bridge;
public final class BridgeOperationException extends RuntimeException {
    private final String code;
    public BridgeOperationException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}
