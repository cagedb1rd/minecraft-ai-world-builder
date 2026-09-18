package dev.worldbuilder.bridge.client;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import dev.worldbuilder.bridge.ForgeBridgeNetwork;
import dev.worldbuilder.bridge.Protocol;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Client-side relay: packets use the currently connected Minecraft server connection. */
public final class ClientBridgeRelay {
    private static final Gson GSON=new Gson();
    private static final ConcurrentHashMap<String,CompletableFuture<Protocol.Response>> PENDING=new ConcurrentHashMap<>();
    private static volatile Protocol.Response lastHandshake;
    public static CompletableFuture<Protocol.Response> route(Protocol.Request request){Minecraft mc=Minecraft.getInstance();Connection connection=mc.getConnection()==null?null:mc.getConnection().getConnection();if(connection==null||!ForgeBridgeNetwork.CHANNEL.isRemotePresent(connection))return CompletableFuture.completedFuture(Protocol.Response.error(request.requestId(),"BRIDGE_HOST_UNAVAILABLE","当前 Minecraft 连接的服务器未安装 AI Bridge"));Protocol.Request relayRequest=new Protocol.Request(request.protocolVersion(),request.requestId(),request.operation(),request.arguments(),new Protocol.Auth("","forge-relay"));String payload=GSON.toJson(relayRequest);if(payload.getBytes(StandardCharsets.UTF_8).length>ForgeBridgeNetwork.MAX_REQUEST_PAYLOAD)return CompletableFuture.completedFuture(Protocol.Response.error(request.requestId(),"LIMIT_EXCEEDED","Relay request exceeds the Forge packet limit; use a compact BuildingPlan or region operation"));CompletableFuture<Protocol.Response> future=new CompletableFuture<>();PENDING.put(request.requestId(),future);mc.execute(()->{Connection active=mc.getConnection()==null?null:mc.getConnection().getConnection();if(active==null||!ForgeBridgeNetwork.CHANNEL.isRemotePresent(active)){complete(Protocol.Response.error(request.requestId(),"BRIDGE_HOST_UNAVAILABLE","当前 Minecraft 连接的服务器未安装 AI Bridge"));return;}try{ForgeBridgeNetwork.CHANNEL.sendToServer(new ForgeBridgeNetwork.RelayRequest(payload));}catch(RuntimeException error){complete(Protocol.Response.error(request.requestId(),"BRIDGE_HOST_UNAVAILABLE","Minecraft Forge relay could not send the request: "+error.getMessage()));}});future.orTimeout(15,TimeUnit.SECONDS).exceptionally(error->{PENDING.remove(request.requestId());return null;});future.whenComplete((value,error)->PENDING.remove(request.requestId()));return future;}
    public static void complete(String payload){try{Protocol.Response response=GSON.fromJson(payload,Protocol.Response.class);if(response==null)return;CompletableFuture<Protocol.Response> future=PENDING.remove(response.requestId());if(future!=null)future.complete(response);}catch(JsonParseException|IllegalStateException ignored){}}
    public static void complete(Protocol.Response response){CompletableFuture<Protocol.Response> future=PENDING.remove(response.requestId());if(future!=null)future.complete(response);}
    public static void discover(){Protocol.Request request=new Protocol.Request(Protocol.VERSION,UUID.randomUUID().toString(),"handshake",new com.google.gson.JsonObject(),new Protocol.Auth("client-relay","client-relay"));route(request).whenComplete((response,error)->{if(error!=null)dev.worldbuilder.bridge.AiWorldBuilderBridge.LOGGER.warn("AI Bridge capability handshake failed: {}",error.getMessage());else if(response!=null){lastHandshake=response;dev.worldbuilder.bridge.AiWorldBuilderBridge.LOGGER.info("AI Bridge server capability handshake received");}});}
    public static Protocol.Response lastHandshake(){return lastHandshake;}
    public static void clear(){lastHandshake=null;PENDING.forEach((id,f)->f.complete(Protocol.Response.error(id,"BRIDGE_HOST_UNAVAILABLE","Minecraft connection closed")));PENDING.clear();}
    private ClientBridgeRelay(){}
}
