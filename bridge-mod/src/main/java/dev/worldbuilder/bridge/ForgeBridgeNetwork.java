package dev.worldbuilder.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/** Forge SimpleImpl channel used only for the existing Minecraft client/server connection. */
public final class ForgeBridgeNetwork {
    public static final String CHANNEL_PROTOCOL="1";
    public static final int MAX_REQUEST_PAYLOAD=1_048_576;
    public static final int MAX_RESPONSE_PAYLOAD=16_777_216;
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation(AiWorldBuilderBridge.MOD_ID,"relay"),()->CHANNEL_PROTOCOL,CHANNEL_PROTOCOL::equals,CHANNEL_PROTOCOL::equals);
    private static final Gson GSON=new Gson(); private static boolean registered;
    public static synchronized void register(){if(registered)return;int id=0;CHANNEL.registerMessage(id++,RelayRequest.class,RelayRequest::encode,RelayRequest::decode,RelayRequest::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));CHANNEL.registerMessage(id,RelayResponse.class,RelayResponse::encode,RelayResponse::decode,RelayResponse::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));registered=true;}
    public record RelayRequest(String payload){
        public static void encode(RelayRequest message,FriendlyByteBuf buffer){buffer.writeUtf(message.payload,MAX_REQUEST_PAYLOAD);}
        public static RelayRequest decode(FriendlyByteBuf buffer){return new RelayRequest(buffer.readUtf(MAX_REQUEST_PAYLOAD));}
        public static void handle(RelayRequest message,Supplier<NetworkEvent.Context> context){NetworkEvent.Context ctx=context.get();ctx.enqueueWork(()->{ServerPlayer sender=ctx.getSender();if(sender==null)return;try{Protocol.Request request=GSON.fromJson(message.payload,Protocol.Request.class);if(request==null||request.requestId()==null||request.operation()==null){send(sender,Protocol.Response.error("unknown","MALFORMED_REQUEST","Relay request is malformed"));return;}BridgeRuntime.handleRelay(request,sender,response->send(sender,response));}catch(JsonParseException|IllegalStateException e){send(sender,Protocol.Response.error("unknown","MALFORMED_REQUEST","Relay request JSON is invalid"));}});ctx.setPacketHandled(true);}
    }
    public record RelayResponse(String payload){
        public static void encode(RelayResponse message,FriendlyByteBuf buffer){buffer.writeUtf(message.payload,MAX_RESPONSE_PAYLOAD);}
        public static RelayResponse decode(FriendlyByteBuf buffer){return new RelayResponse(buffer.readUtf(MAX_RESPONSE_PAYLOAD));}
        public static void handle(RelayResponse message,Supplier<NetworkEvent.Context> context){NetworkEvent.Context ctx=context.get();ctx.enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->dev.worldbuilder.bridge.client.ClientBridgeRelay.complete(message.payload)));ctx.setPacketHandled(true);}
    }
    private static void send(ServerPlayer player,Protocol.Response response){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new RelayResponse(GSON.toJson(response)));}
    private ForgeBridgeNetwork(){}
}
