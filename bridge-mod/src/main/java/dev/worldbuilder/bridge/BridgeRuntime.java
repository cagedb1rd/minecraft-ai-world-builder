package dev.worldbuilder.bridge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;

/** Single routing point shared by the localhost endpoint and Forge relay packets. */
public final class BridgeRuntime {
    @FunctionalInterface public interface ClientRelayEndpoint { CompletableFuture<Protocol.Response> route(Protocol.Request request); }
    private static volatile MinecraftServer hostServer;
    private static volatile BridgeDispatcher hostDispatcher;
    private static volatile ClientRelayEndpoint clientRelay;

    public static void startHost(MinecraftServer server) { hostServer = server; hostDispatcher = new BridgeDispatcher(new WorldAccess(server), BridgeConfig.HISTORY_LIMIT.get()); }
    public static void stopHost(MinecraftServer server) { if(hostServer == server){hostServer=null;hostDispatcher=null;} }
    public static void setClientRelay(ClientRelayEndpoint relay) { clientRelay = relay; }
    public static void clearClientRelay(ClientRelayEndpoint relay) { if(clientRelay == relay) clientRelay=null; }

    public static CompletableFuture<Protocol.Response> routeLocal(Protocol.Request request) {
        MinecraftServer server=hostServer; BridgeDispatcher dispatcher=hostDispatcher;
        if(server!=null&&dispatcher!=null){CompletableFuture<Protocol.Response> result=new CompletableFuture<>();server.execute(()->{BridgePrincipal principal=integratedOwner(server);if(principal==null)result.complete(unavailable(request));else result.complete(dispatcher.dispatch(request,principal));});return result;}
        ClientRelayEndpoint relay=clientRelay; return relay==null?CompletableFuture.completedFuture(unavailable(request)):relay.route(request);
    }
    public static void tick(){BridgeDispatcher dispatcher=hostDispatcher;if(dispatcher!=null)dispatcher.tick();}
    public static void handleRelay(Protocol.Request request,ServerPlayer sender,java.util.function.Consumer<Protocol.Response> reply){BridgeDispatcher dispatcher=hostDispatcher;if(dispatcher==null){reply.accept(unavailable(request));return;}reply.accept(dispatcher.dispatch(request,BridgePrincipal.player(sender.getUUID(),sender.getGameProfile().getName())));}
    public static Protocol.Response unavailable(Protocol.Request request){return Protocol.Response.error(request==null?"unknown":request.requestId(),"BRIDGE_HOST_UNAVAILABLE","No local Integrated Server and no AI Bridge channel is available on the current Minecraft connection");}
    private static BridgePrincipal integratedOwner(MinecraftServer server){
        var profile=server.getSingleplayerProfile();
        if(profile!=null&&server.isSingleplayerOwner(profile))return BridgePrincipal.integratedOwner(profile.getId(),profile.getName());
        for(ServerPlayer player:server.getPlayerList().getPlayers())if(server.isSingleplayerOwner(player.getGameProfile()))return BridgePrincipal.integratedOwner(player.getUUID(),player.getGameProfile().getName());
        return null;
    }
    private BridgeRuntime(){}
}
