import me.linstar.afar.until.Box;

public class test {
    public static void main(String[] args) {
        Box.create(0, 0, 5).not(Box.create(-5, -5, 5)).forEach((x, z) -> {
            System.out.println("x: " + x + ", z: " + z);
        });
    }
}
