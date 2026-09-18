package dev.worldbuilder.bridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

public final class Protocol {
    public static final String VERSION = "1.0.0";
    public record Auth(String token, String clientId) {}
    public record Request(String protocolVersion, String requestId, String operation, JsonObject arguments, Auth auth) {}
    public record ErrorInfo(String code, String message, JsonElement details) {}
    public record Response(String protocolVersion, String requestId, boolean success, JsonElement result, List<String> warnings, List<ErrorInfo> errors) {
        public static Response ok(String requestId, JsonElement result) { return new Response(VERSION, requestId, true, result, List.of(), List.of()); }
        public static Response error(String requestId, String code, String message) { return new Response(VERSION, requestId == null ? "unknown" : requestId, false, null, List.of(), List.of(new ErrorInfo(code, message == null ? "Unknown error" : message, null))); }
    }
    private Protocol() {}
}
