package dev.worldbuilder.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** The only MCP-facing network listener; it binds loopback only. */
public final class LocalBridgeServer implements AutoCloseable {
    private static final int MAX_REQUEST_CHARS=1_048_576,MAX_RESPONSE_BYTES=16_777_216;
    private final BridgeRuntime.ClientRelayEndpoint router; private final String token; private final Gson gson=new Gson();
    private final ExecutorService clients=Executors.newFixedThreadPool(4,r->{Thread t=new Thread(r,"ai-world-builder-local-client");t.setDaemon(true);return t;});
    private volatile boolean running; private ServerSocket socket; private Thread acceptThread;
    public LocalBridgeServer(BridgeRuntime.ClientRelayEndpoint router,String token){this.router=router;this.token=token;}
    public void start() throws IOException {if(token==null||token.length()<16)throw new IOException("Local Bridge token is invalid");socket=new ServerSocket();socket.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}),BridgeConfig.PORT.get()));running=true;acceptThread=new Thread(this::acceptLoop,"ai-world-builder-local-accept");acceptThread.setDaemon(true);acceptThread.start();AiWorldBuilderBridge.LOGGER.info("Local MCP Bridge started on 127.0.0.1:{}",socket.getLocalPort());}
    private void acceptLoop(){while(running)try{Socket client=socket.accept();if(!client.getInetAddress().isLoopbackAddress()){client.close();continue;}clients.submit(()->handle(client));}catch(SocketException e){if(running)AiWorldBuilderBridge.LOGGER.error("Local Bridge accept failed",e);}catch(IOException e){AiWorldBuilderBridge.LOGGER.error("Local Bridge accept failed",e);}}
    private void handle(Socket client){try(client;BufferedReader reader=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.UTF_8));BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(client.getOutputStream(),StandardCharsets.UTF_8))){client.setSoTimeout(15_000);String line=readBoundedLine(reader);if(line==null||line.isEmpty()){write(writer,Protocol.Response.error("unknown","MALFORMED_REQUEST","Request is empty or too large"));return;}Protocol.Request request;try{request=gson.fromJson(line,Protocol.Request.class);}catch(JsonParseException e){write(writer,Protocol.Response.error("unknown","MALFORMED_REQUEST","Invalid JSON"));return;}if(request==null||request.requestId()==null||request.requestId().isBlank()||request.auth()==null||request.auth().clientId()==null||request.auth().clientId().isBlank()||!Authentication.tokenMatches(token,request.auth().token())){write(writer,Protocol.Response.error(request==null?"unknown":request.requestId(),"AUTHENTICATION_FAILED","Invalid local Bridge credentials"));return;}try{write(writer,router.route(request).get(15,TimeUnit.SECONDS));}catch(TimeoutException e){write(writer,Protocol.Response.error(request.requestId(),"TIMEOUT","Bridge route timed out"));}catch(InterruptedException e){Thread.currentThread().interrupt();write(writer,Protocol.Response.error(request.requestId(),"BRIDGE_HOST_UNAVAILABLE","Bridge route interrupted"));}catch(ExecutionException e){write(writer,Protocol.Response.error(request.requestId(),"BRIDGE_HOST_UNAVAILABLE",e.getCause()==null?e.getMessage():e.getCause().getMessage()));}}catch(IOException e){AiWorldBuilderBridge.LOGGER.warn("Local Bridge client failed: {}",e.getMessage());}}
    private void write(BufferedWriter writer,Protocol.Response response)throws IOException{String payload=gson.toJson(response);if(payload.getBytes(StandardCharsets.UTF_8).length>MAX_RESPONSE_BYTES)payload=gson.toJson(Protocol.Response.error(response.requestId(),"LIMIT_EXCEEDED","Bridge response exceeds 16 MiB safety limit"));writer.write(payload);writer.newLine();writer.flush();}
    private String readBoundedLine(Reader reader)throws IOException{StringBuilder value=new StringBuilder();for(int i=0;i<=MAX_REQUEST_CHARS;i++){int c=reader.read();if(c<0)return value.length()==0?null:value.toString();if(c=='\n')return value.toString();if(c!='\r')value.append((char)c);}return null;}
    @Override public void close(){running=false;try{if(socket!=null)socket.close();}catch(IOException e){AiWorldBuilderBridge.LOGGER.warn("Failed to close local Bridge",e);}clients.shutdownNow();}
}
