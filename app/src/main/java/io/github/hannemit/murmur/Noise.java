package io.github.hannemit.murmur;

/**
 * Synthesises the sounds one sample at a time. Nothing is recorded or looped,
 * so there is no seam to hear and no end to reach: every sound is random noise
 * pushed through fixed filters, which makes it statistically the same at 4 am
 * as it was at midnight.
 *
 * This class deliberately uses nothing from Android so tests/NoiseTest.java can
 * run it on a plain JVM.
 */
final class Noise {
    static final int SAMPLE_RATE = 44100;

    enum Sound { AIRPLANE, FAN, WHITE }

    // The engine hum is a fundamental plus two harmonics, each set close to the
    // level of the noise around it so it is heard inside the rumble rather than
    // on top of it. Its period is a whole number of samples, so one stored cycle
    // repeats exactly, with no phase that could drift over a night.
    private static final int HUM_PERIOD = 400; // 110.25 Hz
    private static final float[] HUM = new float[HUM_PERIOD];

    static {
        for (int i = 0; i < HUM_PERIOD; i++) {
            double t = 2 * Math.PI * i / HUM_PERIOD;
            HUM[i] = (float) (0.060 * Math.sin(t)
                    + 0.050 * Math.sin(2 * t + 1.1)
                    + 0.030 * Math.sin(3 * t + 2.3));
        }
    }

    private final Sound sound;
    private final Channel left;
    private final Channel right;
    private int humPos;

    Noise(Sound sound) {
        this.sound = sound;
        // Each ear gets its own noise, which sounds wide rather than like a
        // point in the middle of the head. Any non-zero seeds will do.
        left = new Channel(0x9E3779B97F4A7C15L);
        right = new Channel(0xD1B54A32D192ED03L);
    }

    /** Fills {@code out} with {@code frames} interleaved stereo frames, each sample within -1..1. */
    void render(float[] out, int frames) {
        for (int i = 0; i < frames; i++) {
            float l;
            float r;
            switch (sound) {
                case WHITE:
                    l = left.white() * 0.10f;
                    r = right.white() * 0.10f;
                    break;
                case FAN:
                    l = left.fan();
                    r = right.fan();
                    break;
                default:
                    float hum = HUM[humPos];
                    if (++humPos == HUM_PERIOD) humPos = 0;
                    l = left.airplane() + hum;
                    r = right.airplane() + hum;
                    break;
            }
            out[2 * i] = clamp(l);
            out[2 * i + 1] = clamp(r);
        }
    }

    // Filtered noise has no hard ceiling. The levels leave plenty of headroom, so
    // this only ever touches a freak sample, where it cannot be heard.
    private static float clamp(float v) {
        return v > 1f ? 1f : (v < -1f ? -1f : v);
    }

    private static final class Channel {
        private long seed;
        private double p0, p1, p2;

        // Airplane: a deep rumble, some rush of air over the fuselage, and two
        // soft resonances that give the steady hum some breath. Together they
        // fall about 3 dB per octave, as cabin noise does, and faster above 3 kHz.
        private final OnePole rumble = new OnePole(110);
        private final Biquad rumbleFloor = Biquad.highPass(30, 0.707);
        private final OnePole air = new OnePole(1500);
        private final OnePole cabin = new OnePole(3000);
        private final Biquad drone1 = Biquad.bandPass(110.25, 5);
        private final Biquad drone2 = Biquad.bandPass(220.5, 6);

        // Fan: an even whoosh with the hiss taken off the top.
        private final OnePole fanTone = new OnePole(1000);
        private final OnePole fanTop = new OnePole(4000);
        private final Biquad fanFloor = Biquad.highPass(45, 0.707);

        Channel(long seed) {
            this.seed = seed;
        }

        /** Uniform white noise in -1..1 (xorshift64, top 24 bits). */
        float white() {
            long x = seed;
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= x << 17;
            seed = x;
            return (int) (x >> 40) * (1f / (1 << 23));
        }

        /** Pink noise (equal energy per octave), by Paul Kellet's three-pole approximation. */
        private double pink(double w) {
            p0 = 0.99765 * p0 + w * 0.0990460;
            p1 = 0.96300 * p1 + w * 0.2965164;
            p2 = 0.57000 * p2 + w * 1.0526913;
            return p0 + p1 + p2 + w * 0.1848;
        }

        float fan() {
            double w = white();
            return (float) (0.12 * fanFloor.run(fanTop.run(fanTone.run(pink(w)))));
        }

        float airplane() {
            double w = white();
            double low = rumbleFloor.run(rumble.run(w));
            double rush = air.run(pink(w));
            double mix = low + 0.015 * rush + 0.12 * drone1.run(w) + 0.06 * drone2.run(w);
            return (float) (1.8 * cabin.run(mix));
        }
    }

    /** Gentle low-pass, 6 dB per octave above {@code hz}. */
    private static final class OnePole {
        private final double k;
        private double y;

        OnePole(double hz) {
            k = 1 - Math.exp(-2 * Math.PI * hz / SAMPLE_RATE);
        }

        double run(double x) {
            y += k * (x - y);
            return y;
        }
    }

    /** Second-order filter from the Audio EQ Cookbook, transposed direct form II. */
    private static final class Biquad {
        private final double b0, b1, b2, a1, a2;
        private double z1, z2;

        private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0;
            this.b1 = b1 / a0;
            this.b2 = b2 / a0;
            this.a1 = a1 / a0;
            this.a2 = a2 / a0;
        }

        static Biquad highPass(double hz, double q) {
            double w = 2 * Math.PI * hz / SAMPLE_RATE;
            double cos = Math.cos(w);
            double alpha = Math.sin(w) / (2 * q);
            return new Biquad((1 + cos) / 2, -(1 + cos), (1 + cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
        }

        static Biquad bandPass(double hz, double q) {
            double w = 2 * Math.PI * hz / SAMPLE_RATE;
            double alpha = Math.sin(w) / (2 * q);
            return new Biquad(alpha, 0, -alpha, 1 + alpha, -2 * Math.cos(w), 1 - alpha);
        }

        double run(double x) {
            double y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            return y;
        }
    }
}
