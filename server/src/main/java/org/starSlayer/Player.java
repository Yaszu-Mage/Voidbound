package org.starSlayer;

import com.google.gson.JsonObject;
import org.starSlayer.utility.Vector3;

import java.util.UUID;

public class Player {
    private Vector3 position;
    private Vector3 color;
    public UUID uuid;
    private String name;
    private double health;
    private final double maxHealth = 100;
    private boolean isTeleporting = false;
    private boolean isAllowedToTeleport = false;
    public void setColor(Vector3 color) {
        this.color = color;
    }
    /**
     * If a player moves more than this distance in a single tick, they will be considered to be rubberbanding and will be moved back to their previous position.
     */
    private final double maxDistance = 100;

    public Player(Vector3 position, Vector3 color) {
        this.position = position;
        this.color = color;
        this.uuid = UUID.randomUUID();
    }

    public Player(Vector3 position, Vector3 color, UUID uuid) {
        this.position = position;
        this.color = color;
        this.uuid = uuid;
    }
    public void move(Vector3 secondaryPosition) {
        if (isLegal(secondaryPosition)) {
            position.set(secondaryPosition);
        }

    }
    public int worldBorder = 1000;
    public boolean isLegal(Vector3 position) {
        //Check distance from position, if it's above a certain value and no teleport then rubberband
        if (Math.abs(position.getX()) > worldBorder || Math.abs(position.getY()) > worldBorder || Math.abs(position.getZ()) > worldBorder) {
            return false;
        }
        if (isAllowedToTeleport && isTeleporting) {
            return true;
        }
        return !(position.distanceTo(this.position) > 10);

    }
    public Vector3 getPosition() {
        return position;
    }

    public JsonObject  convertToJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "PLAYER-INFORMATION");
        json.addProperty("uuid", uuid.toString());
        json.addProperty("x", position.getX());
        json.addProperty("y", position.getY());
        json.addProperty("z", position.getZ());
        json.addProperty("r", color.getX());
        json.addProperty("g", color.getY());
        json.addProperty("b", color.getZ());
        return json;
    }


}
