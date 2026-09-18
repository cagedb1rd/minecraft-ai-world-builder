package dev.worldbuilder.bridge;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.List;

public final class PermissionManager {
    public enum Role { OWNER, TRUSTED, NONE }
    private final List<? extends String> configuredRoles;
    public PermissionManager(){this.configuredRoles=null;}
    PermissionManager(List<? extends String> configuredRoles){this.configuredRoles=configuredRoles;}
    public Role role(BridgePrincipal principal) { if(principal==null)return Role.NONE;if(principal.integratedOwner())return Role.OWNER; var source=configuredRoles==null?BridgeConfig.PLAYER_ROLES.get():configuredRoles;Map<String,Role> roles=source.stream().map(s->s.split("=",2)).filter(p->p.length==2).collect(Collectors.toMap(p->p[0],p->{try{return Role.valueOf(p[1].toUpperCase(Locale.ROOT));}catch(IllegalArgumentException e){return Role.NONE;}},(a,b)->b)); if(principal.playerUuid()!=null&&roles.containsKey(principal.playerUuid().toString()))return roles.get(principal.playerUuid().toString());return Role.NONE; }
    /** Compatibility helper for legacy in-process callers; production routing always supplies a BridgePrincipal. */
    public Role role(String identity) {
        if (identity == null) return Role.NONE;
        var source=configuredRoles==null?BridgeConfig.PLAYER_ROLES.get():configuredRoles;
        for (String entry : source) {
            String[] parts=entry.split("=",2);
            if (parts.length==2 && parts[0].equals(identity)) {
                try { return Role.valueOf(parts[1].toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException ignored) { return Role.NONE; }
            }
        }
        return Role.NONE;
    }
    public void require(Protocol.Request request,BridgePrincipal principal) { Role role=role(principal); if(request.operation().equals("handshake"))return;if(role==Role.NONE) throw new BridgeOperationException("PERMISSION_DENIED", "Minecraft player has no configured role");
        if (isOwnerOnly(request.operation()) && role!=Role.OWNER) throw new BridgeOperationException("PERMISSION_DENIED", "Operation requires OWNER role"); }
    public void require(Protocol.Request request) { require(request,BridgePrincipal.localFallback(request.auth()==null?"unknown":request.auth().clientId())); }
    private boolean isOwnerOnly(String op) { return op.equals("undo_build")||op.equals("redo_build")||op.equals("cancel_build")||op.equals("reset_experiment_workspace")||op.startsWith("create_"); }
    /** Enforce the trusted-player build limit using the server-derived identity. */
    public void requireBuildSize(BridgePrincipal principal,int changes){if(role(principal)==Role.TRUSTED&&changes>BridgeConfig.TRUSTED_MAX_BLOCKS.get())throw new BridgeOperationException("PERMISSION_DENIED","Large build requires OWNER role");}
    /** Compatibility overload retained for older callers; new routing must use the principal overload. */
    public void requireBuildSize(String clientId,int changes){requireBuildSize(BridgePrincipal.localFallback(clientId),changes);}
}
