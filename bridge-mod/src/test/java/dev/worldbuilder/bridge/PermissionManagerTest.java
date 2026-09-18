package dev.worldbuilder.bridge;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PermissionManagerTest {
    @Test void bindsRolesToMinecraftPlayerIdentityAndDefaultsToDeny(){java.util.UUID ownerId=java.util.UUID.randomUUID(),helperId=java.util.UUID.randomUUID();PermissionManager permissions=new PermissionManager(java.util.List.of(ownerId+"=OWNER",helperId+"=TRUSTED"));Protocol.Request unknown=new Protocol.Request(Protocol.VERSION,"id","get_world_info",new JsonObject(),new Protocol.Auth("token","spoofed"));assertThrows(BridgeOperationException.class,()->permissions.require(unknown,BridgePrincipal.player(java.util.UUID.randomUUID(),"helper")));Protocol.Request owner=new Protocol.Request(Protocol.VERSION,"id","undo_build",new JsonObject(),new Protocol.Auth("token","spoofed"));assertDoesNotThrow(()->permissions.require(owner,BridgePrincipal.player(ownerId,"not-client-supplied")));Protocol.Request helper=new Protocol.Request(Protocol.VERSION,"id","undo_build",new JsonObject(),new Protocol.Auth("token","owner"));assertThrows(BridgeOperationException.class,()->permissions.require(helper,BridgePrincipal.player(helperId,"helper")));assertEquals(PermissionManager.Role.TRUSTED,permissions.role(BridgePrincipal.player(helperId,"helper")));assertEquals(PermissionManager.Role.OWNER,permissions.role(BridgePrincipal.integratedOwner(ownerId,"owner")));}
}
