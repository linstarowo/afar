package me.linstar.afar.event;

public class WorldIdEvent extends AfarEvent {
    String worldId;
    public WorldIdEvent(String worldId) {
        this.worldId = worldId;
    }

    public String getID() {
        return worldId;
    }
}
