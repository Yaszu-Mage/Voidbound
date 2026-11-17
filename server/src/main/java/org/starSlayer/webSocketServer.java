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
import java.util.HashMap;

public class webSocketServer extends WebSocketServer {

    private final HashMap<WebSocket, Player> players = new HashMap<>();



    public webSocketServer(InetSocketAddress address) {
        super(address);
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
        JOIN
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
                    updateJson.addProperty("type", "UPDATE");
                    updateJson.addProperty("uuid", player.uuid.toString());
                    updateJson.addProperty("x", player.getPosition().getX());
                    updateJson.addProperty("y", player.getPosition().getY());
                    updateJson.addProperty("z", player.getPosition().getZ());
                    updateJson.add("isLegal", new JsonPrimitive(isLegalMove));
                    conn.send(updateJson.toString());
                }
                System.out.println("Player moved to: " + newPosition);
            }
        }
        JsonArray playersJson = new JsonArray();
        for (Player player : players.values()) {
            playersJson.add(player.convertToJson());
        }
        conn.send(playersJson.toString());
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {

    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started.");

    }
}
