package me.linstar.afar.until;

import java.awt.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Box {
    final int minX, minY, maxX, maxY;

    public Box(int x1, int y1, int x2, int y2) {
        minX = Math.min(x1, x2);
        minY = Math.min(y1, y2);
        maxX = Math.max(x1, x2);
        maxY = Math.max(y1, y2);
    }

    public static Box create(int x, int y, int size){
        return new Box(x-size, y-size, x+size, y+size);
    }

    public Box not(Box box){
        return new Box(
                Math.max(minX, box.minX),
                Math.max(minY, box.minY),
                Math.min(maxX, box.maxX),
                Math.min(maxY, box.maxY)
        );
    }

    public boolean contain(int x, int z){
        return (x >= minX) && (x <= maxX) && (z >= minY) && (z <= maxY);
    }

    public void forEach(BiConsumer<Integer, Integer> action){
        for (int x = minX; x <= maxX; x++){
            for (int y = minY; y <= maxY; y++){
                action.accept(x, y);
            }
        }
    }
}
