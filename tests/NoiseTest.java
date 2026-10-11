package io.github.hannemit.murmur;

/**
 * Renders ten minutes of every sound and checks that it is what a sleeper needs:
 * never silent, never clipped, and as loud in every second as in every other.
 *
 * Run with:
 *   javac -d build/test app/src/main/java/io/github/hannemit/murmur/Noise.java tests/NoiseTest.java
 *   java -cp build/test io.github.hannemit.murmur.NoiseTest
 */
public class NoiseTest {
    private static final int SECONDS = 600;
    private static int failures;

    public static void main(String[] args) {
        for (Noise.Sound sound : Noise.Sound.values()) {
            // The hum is adjustable, so its extremes have to hold up too.
            check(sound, 1);
            check(sound, 0);
            check(sound, Noise.MAX_HUM);
        }
        if (failures > 0) {
            System.out.println(failures + " check(s) failed");
            System.exit(1);
        }
        System.out.println("All sounds are steady");
    }

    private static void check(Noise.Sound sound, float hum) {
        Noise noise = new Noise(sound);
        noise.setHum(hum);
        // Let the hum finish gliding to its level before measuring.
        noise.render(new float[Noise.SAMPLE_RATE * 2], Noise.SAMPLE_RATE);
        float[] second = new float[Noise.SAMPLE_RATE * 2];
        double[] loudness = new double[SECONDS];
        double peak = 0;
        double total = 0;
        double leftRight = 0;
        boolean finite = true;

        for (int s = 0; s < SECONDS; s++) {
            noise.render(second, Noise.SAMPLE_RATE);
            double sum = 0;
            for (int i = 0; i < second.length; i += 2) {
                float l = second[i];
                float r = second[i + 1];
                if (Float.isNaN(l) || Float.isNaN(r)) finite = false;
                sum += l * l + r * r;
                leftRight += l * r;
                peak = Math.max(peak, Math.max(Math.abs(l), Math.abs(r)));
            }
            loudness[s] = Math.sqrt(sum / second.length);
            total += sum;
        }

        double rms = Math.sqrt(total / ((double) second.length * SECONDS));
        double quietest = Double.MAX_VALUE;
        double loudest = 0;
        for (double v : loudness) {
            quietest = Math.min(quietest, v);
            loudest = Math.max(loudest, v);
        }
        double firstMinute = mean(loudness, 0, 60);
        double lastMinute = mean(loudness, SECONDS - 60, SECONDS);

        System.out.printf("%-18s hum %.1f  level %.3f  peak %.2f  per-second level %.1f%%..%.1f%% of average%n",
                sound, hum, rms, peak, 100 * quietest / rms, 100 * loudest / rms);

        expect(sound, "produces only real numbers", finite);
        expect(sound, "is clearly audible", rms > 0.04);
        expect(sound, "is not too loud", rms < (hum > 1 ? 0.22 : 0.16));
        expect(sound, "never reaches the clipping point", peak < 0.99);
        expect(sound, "has no quiet second", quietest > 0.9 * rms);
        // A strong hum makes its slow swell between the two engines more of the total.
        expect(sound, "has no loud second", loudest < (hum > 1 ? 1.12 : 1.1) * rms);
        expect(sound, "is as loud at the end as at the start",
                Math.abs(lastMinute / firstMinute - 1) < 0.02);
        // The hum is shared by both ears; the noise around it must not be.
        expect(sound, "is not the same in both ears",
                hum > 1 || Math.abs(leftRight / (total / 2)) < 0.75);
    }

    private static double mean(double[] values, int from, int to) {
        double sum = 0;
        for (int i = from; i < to; i++) sum += values[i];
        return sum / (to - from);
    }

    private static void expect(Noise.Sound sound, String what, boolean ok) {
        if (ok) return;
        failures++;
        System.out.println("FAIL: " + sound + " " + what);
    }
}
