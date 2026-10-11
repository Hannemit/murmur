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
        for (Noise.Sound sound : Noise.Sound.values()) check(sound);
        if (failures > 0) {
            System.out.println(failures + " check(s) failed");
            System.exit(1);
        }
        System.out.println("All sounds are steady");
    }

    private static void check(Noise.Sound sound) {
        Noise noise = new Noise(sound);
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

        System.out.printf("%-9s level %.3f  peak %.2f  per-second level %.1f%%..%.1f%% of average%n",
                sound, rms, peak, 100 * quietest / rms, 100 * loudest / rms);

        expect(sound, "produces only real numbers", finite);
        expect(sound, "is clearly audible", rms > 0.04);
        expect(sound, "is not too loud", rms < 0.2);
        expect(sound, "never reaches the clipping point", peak < 0.99);
        expect(sound, "has no quiet second", quietest > 0.9 * rms);
        expect(sound, "has no loud second", loudest < 1.1 * rms);
        expect(sound, "is as loud at the end as at the start",
                Math.abs(lastMinute / firstMinute - 1) < 0.02);
        expect(sound, "is not the same in both ears",
                Math.abs(leftRight / (total / 2)) < 0.5);
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
