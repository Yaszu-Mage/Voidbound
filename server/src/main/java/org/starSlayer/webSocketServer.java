package org.starSlayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.starSlayer.utility.Vector3;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class webSocketServer extends WebSocketServer {

    private final HashMap<WebSocket, Player> players = new HashMap<>();
    private final HashMap<Player, String> activeScripts = new HashMap<>();
    sandbox sandbox = new sandbox();
    public static ArrayList<String> BLOCKED = new ArrayList<>();


    public webSocketServer(InetSocketAddress address) {
        super(address);
        BLOCKED.add("get_tree().quit()");
        BLOCKED.add("HTTPClient");
        BLOCKED.add("websocket");
        BLOCKED.add("OS");
        BLOCKED.add("HTTP");
        BLOCKED.add("FileAccess");
        BLOCKED.add("System");
        BLOCKED.add("system");
    }
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        conn.send("Welcome to the server!");
        Player player = new Player(new Vector3(0, 0, 0), new Vector3(255, 0, 0));
        players.put(conn, player);
        JsonObject json = new JsonObject();
        json.addProperty("type", "JOIN");
        json.addProperty("uuid", player.uuid.toString());
        conn.send(json.toString());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        players.remove(conn);
    }

    enum MessageType {
        UPDATE,
        CHAT,
        PING,
        JOIN,
        CODE
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        JsonObject json = JsonParser.parseString(message).getAsJsonObject();
        MessageType messageType = MessageType.valueOf(json.get("type").getAsString());
        switch (messageType) {
            case JOIN -> {
                String username = json.get("username").getAsString();
                Player player = new Player(new Vector3(0, 0, 0), new Vector3(255, 0, 0));
                players.put(conn, player);
            }
            case UPDATE -> {
                Player player = players.get(conn);

                double x = json.get("x").getAsDouble();
                double y = json.get("y").getAsDouble();
                double z = json.get("z").getAsDouble();
                Vector3 newPosition = new Vector3(x, y, z);
                boolean isLegalMove = player.isLegal(newPosition);
                player.move(newPosition);
                if (isLegalMove) {
                    JsonObject updateJson = new JsonObject();
                    updateJson.addProperty("type", "SELF-UPDATE");
                    updateJson.addProperty("uuid", player.uuid.toString());
                    updateJson.addProperty("x", player.getPosition().getX());
                    updateJson.addProperty("y", player.getPosition().getY());
                    updateJson.addProperty("z", player.getPosition().getZ());
                    updateJson.add("isLegal", new JsonPrimitive(isLegalMove));
                    conn.send(updateJson.toString());
                }
//                System.out.println("Player moved to: " + newPosition.toString());
            }
            case CODE ->  {
                /**
                 * Figure out who it's from, check if it's safe, check if it can compile
                 */
                Player player = players.get(conn);
                String code = json.get("code").getAsString();
                System.out.println("Code received: " + code);
                if (BLOCKED.stream().anyMatch(code::contains)) {
                    JsonObject errorJson = new JsonObject();
                    errorJson.addProperty("type", "CODE-ERROR");
                    errorJson.addProperty("output", "Your code contains blocked keywords. (OS,http,websocket, etc.)");
                    conn.send(errorJson.toString());
                    return;
                }
                if (code.contains("uneditable")) {
                    JsonObject errorJson = new JsonObject();
                    errorJson.addProperty("type", "CODE-ERROR");
                    errorJson.addProperty("output", "Your code tried to edit the uneditable.");
                    conn.send(errorJson.toString());
                    return;
                }
                System.out.println("Code is safe to run.");
                CompletableFuture<String> resultFuture = new CompletableFuture<>();
                try {
                    System.out.println("Running code: " + code);
                    sandbox.runScript(code, 20,resultFuture);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                resultFuture.thenAccept(output -> {
                   JsonObject outputJson = new JsonObject();
                   if (output.contains("TIMEOUT") || output.contains("ERROR")) {
                       outputJson.addProperty("type", "CODE-ERROR");
                   } else {
                          outputJson.addProperty("type", "CODE-OUTPUT");
                          outputJson.addProperty("code", code);
                          outputJson.addProperty("uuid", player.uuid.toString());
                          activeScripts.put(player, code);
                          for (WebSocket connection : players.keySet()) {
                              if (players.get(connection).equals(player)) {
                                  continue; // Skip the player who sent the code
                              }
                              JsonObject broadcastJson = new JsonObject();
                              broadcastJson.addProperty("type", "CODE-BROADCAST");
                              broadcastJson.addProperty("uuid", player.uuid.toString());
                              broadcastJson.addProperty("code", code);
                              connection.send(broadcastJson.toString());
                          }
                   }
                   outputJson.addProperty("output", output);
                   conn.send(outputJson.toString());
                });
            }
        }
        JsonObject update = new JsonObject();
        JsonArray playersJson = new JsonArray();
        for (Player player : players.values()) {
            playersJson.add(player.convertToJson());
        }
        update.addProperty("type", "UPDATE");
        update.add("players", playersJson);
        conn.send(update.toString());
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {

    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started.");

    }
}
