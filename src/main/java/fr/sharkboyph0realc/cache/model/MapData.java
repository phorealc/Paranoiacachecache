package fr.sharkboyph0realc.cache.model;

import org.bukkit.Location;

public final class MapData {

    private final String name;
    private Location center;
    private int borderRadius;
    private Location hunterSpawn;
    private Location waitingRoom;

    public MapData(String name) {
        this.name = name;
        this.borderRadius = 150;
    }

    public String getName() {
        return name;
    }

    public Location getCenter() {
        return center;
    }

    public void setCenter(Location center) {
        this.center = center;
    }

    public int getBorderRadius() {
        return borderRadius;
    }

    public void setBorderRadius(int borderRadius) {
        this.borderRadius = Math.max(1, borderRadius);
    }

    public Location getHunterSpawn() {
        return hunterSpawn;
    }

    public void setHunterSpawn(Location hunterSpawn) {
        this.hunterSpawn = hunterSpawn;
    }

    public Location getWaitingRoom() {
        return waitingRoom;
    }

    public void setWaitingRoom(Location waitingRoom) {
        this.waitingRoom = waitingRoom;
    }

    public boolean isReady() {
        return center != null && hunterSpawn != null && waitingRoom != null && borderRadius > 0;
    }
}
