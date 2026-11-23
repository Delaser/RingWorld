package rwqa_state;

import java.io.*;
import java.nio.file.*;

public class Seen {
    public static boolean placeholder, full, drew;
    public static long swapped, last;
    public static int missed, frames;
    static PrintWriter out;

    public static void sample() {
        long now = System.nanoTime();
        try {
            if (out == null)
                out = new PrintWriter(Files.newBufferedWriter(Path.of("large-frame-times.csv")));
            if (last != 0) out.println(now + "," + (now - last) + "," + (full ? 1 : 0));
            if (frames % 100 == 0) flush();
            last = now;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void flush() {
        if (out != null) out.flush();
    }
}
