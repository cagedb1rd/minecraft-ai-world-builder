package dev.worldbuilder.bridge;

import java.util.UUID;

/** Authenticated identity for an operation. Relay requests are bound to this server-side identity. */
public record BridgePrincipal(String auditId, UUID playerUuid, String playerName, boolean integratedOwner) {
    public static BridgePrincipal player(UUID uuid, String name) { return new BridgePrincipal(uuid.toString(), uuid, name, false); }
    public static BridgePrincipal integratedOwner(UUID uuid, String name) { return new BridgePrincipal(uuid.toString(), uuid, name, true); }
    public static BridgePrincipal localFallback(String id) { return new BridgePrincipal(id, null, null, false); }
}
