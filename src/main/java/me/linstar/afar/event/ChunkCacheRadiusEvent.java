package me.linstar.afar.event;

public class ChunkCacheRadiusEvent extends AfarEvent {
    final int radius;
    public ChunkCacheRadiusEvent(int radius) {
        this.radius = radius;
    }

    public int getRadius() { return radius; }

}
