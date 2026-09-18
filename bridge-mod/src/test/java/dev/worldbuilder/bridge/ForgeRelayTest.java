package dev.worldbuilder.bridge;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Packet-level tests for the single Minecraft connection relay. */
class ForgeRelayTest {
    @Test void requestPacketRoundTripsBuildingPlanWithoutPerBlockExpansion(){String payload="{\"protocolVersion\":\"1.0.0\",\"requestId\":\"r\",\"operation\":\"execute_building_plan\",\"arguments\":{\"plan\":{\"operations\":[{\"type\":\"box\"}]}},\"auth\":{\"token\":\"ignored-on-server\",\"clientId\":\"spoofed\"}}";FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());ForgeBridgeNetwork.RelayRequest.encode(new ForgeBridgeNetwork.RelayRequest(payload),buffer);assertEquals(payload,ForgeBridgeNetwork.RelayRequest.decode(buffer).payload());}
    @Test void handshakeBatchUndoAndPreviewPayloadsRemainCorrelated(){for(String operation:new String[] {"handshake","set_blocks_batch","undo_build","redo_build","preview_build"}){String payload="{\"protocolVersion\":\"1.0.0\",\"requestId\":\""+operation+"\",\"operation\":\""+operation+"\",\"arguments\":{},\"auth\":{\"token\":\"x\",\"clientId\":\"x\"}}";FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());ForgeBridgeNetwork.RelayRequest.encode(new ForgeBridgeNetwork.RelayRequest(payload),buffer);assertTrue(ForgeBridgeNetwork.RelayRequest.decode(buffer).payload().contains(operation));}}
    @Test void unavailableRoutingIsExplicitWhenNoServerOrConnection(){Protocol.Response response=BridgeRuntime.routeLocal(new Protocol.Request(Protocol.VERSION,"r","get_world_info",new JsonObject(),new Protocol.Auth("token","codex"))).join();assertFalse(response.success());assertEquals("BRIDGE_HOST_UNAVAILABLE",response.errors().get(0).code());}
    @Test void localRouteUsesTheExistingForgeRelayEndpoint(){Protocol.Response expected=Protocol.Response.ok("relay",new com.google.gson.JsonPrimitive("ok"));BridgeRuntime.ClientRelayEndpoint endpoint=request->java.util.concurrent.CompletableFuture.completedFuture(expected);BridgeRuntime.setClientRelay(endpoint);try{Protocol.Response actual=BridgeRuntime.routeLocal(new Protocol.Request(Protocol.VERSION,"relay","get_world_info",new JsonObject(),new Protocol.Auth("token","codex"))).join();assertEquals(expected,actual);}finally{BridgeRuntime.clearClientRelay(endpoint);}}
    @Test void responsePacketRoundTripsRemoteResult(){String payload="{\"protocolVersion\":\"1.0.0\",\"requestId\":\"r\",\"success\":false,\"warnings\":[],\"errors\":[{\"code\":\"PERMISSION_DENIED\",\"message\":\"denied\"}]}";FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());ForgeBridgeNetwork.RelayResponse.encode(new ForgeBridgeNetwork.RelayResponse(payload),buffer);assertEquals(payload,ForgeBridgeNetwork.RelayResponse.decode(buffer).payload());}
}
