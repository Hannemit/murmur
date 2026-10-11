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

    enum Sound {
        AIRPLANE, AIRPLANE_SOFT, AIRPLANE_DEEP, AIRPLANE_DEEP_PINK, AIRPLANE_DRONE, PROPELLER,
        FAN, BROWN, PINK, WHITE;

        /** Whether this sound has an engine hum for {@link Noise#setHum} to adjust. */
        boolean hasHum() {
            return recipe(this).harmonics.length > 0;
        }
    }

    /**
     * What a sound is made of. Up to three kinds of noise are mixed, the mix is
     * trimmed at the bottom and the top, and an engine hum may be laid over it.
     */
    private static final class Recipe {
        /** Deep noise that falls 6 dB per octave above {@code rumbleHz}: the body of a cabin's roar. */
        double rumble, rumbleHz = 110;
        /** Pink noise, optionally dulled above {@code pinkHz}: the rush of air. */
        double pink, pinkHz;
        /**
         * Noise gathered into a broad band around {@code blowHz}. This is what
         * makes a sound read as air moving, like breath or a vent, rather than
         * as static, which is what noise with no such shape sounds like. Its
         * strength wanders by a fraction of a decibel, as moving air does.
         */
        double blow, blowHz;
        /** Unfiltered white noise: pure static. */
        double white;
        /** Nothing below this is kept; it would only be wasted speaker movement. */
        double floorHz = 30;
        /** The whole mix is rolled off above this, by 6 dB per octave for each of {@code topPoles}. */
        double topHz;
        int topPoles;
        /** Pink noise laid over the finished mix, past the roll-off, dulled only above {@code airHz}. */
        double air, airHz;
        /**
         * The hum: one cycle lasts {@code humPeriod} samples, with a fundamental
         * and overtones at the levels in {@code harmonics}. Because the period is
         * a whole number of samples, one stored cycle repeats exactly, with no
         * phase that could drift over a night.
         */
        int humPeriod = 400; // 110.25 Hz
        double[] harmonics = {};
        /** Noise ringing at the hum's pitch, which gives the hum some breath. */
        double drone;
        double gain = 1;
    }

    private static Recipe recipe(Sound sound) {
        Recipe r = new Recipe();
        switch (sound) {
            case AIRPLANE:
                // Falls about 3 dB per octave, as cabin noise does, and faster
                // above 2 kHz. Each part of the hum sits close to the level of
                // the noise around it, so it is heard inside the rumble.
                r.rumble = 1;
                r.pink = 0.008;
                r.blow = 0.38;
                r.blowHz = 500;
                r.pinkHz = 1500;
                r.topHz = 2200;
                r.topPoles = 1;
                r.harmonics = new double[] {0.060, 0.050, 0.030};
                r.drone = 0.12;
                r.gain = 1.8;
                break;
            case AIRPLANE_SOFT:
                // The same cabin with most of the hiss taken away.
                r.rumble = 1;
                r.pink = 0.004;
                r.blow = 0.38;
                r.blowHz = 400;
                r.pinkHz = 1000;
                r.topHz = 1400;
                r.topPoles = 2;
                r.harmonics = new double[] {0.060, 0.050, 0.030, 0.012};
                r.drone = 0.12;
                r.gain = 2.0;
                break;
            case AIRPLANE_DEEP:
                // Nearly all rumble, pitched lower. Needs headphones or a speaker
                // with some bass; a phone's own speaker can barely play it.
                r.rumble = 1;
                r.rumbleHz = 70;
                r.blow = 0.30;
                r.blowHz = 300;
                r.topHz = 800;
                r.topPoles = 2;
                r.humPeriod = 500; // 88.2 Hz
                r.harmonics = new double[] {0.070, 0.055, 0.035, 0.018};
                r.drone = 0.15;
                r.gain = 2.5;
                break;
            case AIRPLANE_DEEP_PINK:
                // The deep rumble again, with a little pink noise over it so the
                // top is not empty. The pink sits about 18 dB under the rumble.
                r.rumble = 1;
                r.rumbleHz = 70;
                r.blow = 0.30;
                r.blowHz = 300;
                r.topHz = 800;
                r.topPoles = 2;
                r.humPeriod = 500; // 88.2 Hz
                r.harmonics = new double[] {0.070, 0.055, 0.035, 0.018};
                r.drone = 0.15;
                r.gain = 2.5;
                r.air = 0.018;
                r.airHz = 5000;
                break;
            case AIRPLANE_DRONE:
                // A quieter bed of noise under a fuller hum with more overtones.
                r.rumble = 1;
                r.pink = 0.004;
                r.blow = 0.38;
                r.blowHz = 400;
                r.pinkHz = 1000;
                r.topHz = 1400;
                r.topPoles = 2;
                r.harmonics = new double[] {0.060, 0.060, 0.045, 0.030, 0.020, 0.012};
                r.drone = 0.30;
                r.gain = 1.5;
                break;
            case PROPELLER:
                // A buzzier engine: many overtones, the second strongest, as
                // from propeller blades passing the cabin.
                r.rumble = 1;
                r.pink = 0.006;
                r.blow = 0.38;
                r.blowHz = 450;
                r.pinkHz = 1200;
                r.topHz = 2000;
                r.topPoles = 1;
                r.humPeriod = 450; // 98 Hz
                r.harmonics = new double[] {0.040, 0.065, 0.050, 0.040, 0.028, 0.020, 0.012, 0.008};
                r.drone = 0.20;
                r.gain = 1.5;
                break;
            case FAN:
                // An even whoosh with the hiss taken off the top.
                r.pink = 0.12;
                r.pinkHz = 1000;
                r.topHz = 4000;
                r.topPoles = 1;
                r.floorHz = 45;
                break;
            case BROWN:
                // Falls 6 dB per octave all the way: a low, soft roar.
                r.rumble = 1;
                r.rumbleHz = 20;
                r.floorHz = 40;
                r.gain = 10;
                break;
            case PINK:
                // Equal energy in every octave: like steady rain.
                r.pink = 0.05;
                break;
            default:
                r.white = 0.10;
                r.floorHz = 0;
                break;
        }
        return r;
    }

    /** Hum levels run from none to this many times the standard level of 1. */
    static final float MAX_HUM = 2.5f;
    private static final float HUM_STEP = 1f / (0.3f * SAMPLE_RATE);
    // A second engine, fainter and a quarter of a hertz lower, so the hum swells
    // and ebbs very slightly every few seconds as it does between two real engines.
    private static final double SECOND_ENGINE = 0.12;

    private final Channel left;
    private final Channel right;
    private final float[] hum;
    private final float[] hum2;
    private int humPos;
    private int humPos2;
    private float humTarget = 1;
    private float humLevel = 1;

    Noise(Sound sound) {
        Recipe recipe = recipe(sound);
        // Each ear gets its own noise, which sounds wide rather than like a
        // point in the middle of the head. Any non-zero seeds will do.
        left = new Channel(recipe, 0x9E3779B97F4A7C15L);
        right = new Channel(recipe, 0xD1B54A32D192ED03L);

        boolean hasHum = recipe.harmonics.length > 0;
        hum = cycle(recipe.harmonics, hasHum ? recipe.humPeriod : 1, 1);
        hum2 = cycle(recipe.harmonics, hasHum ? recipe.humPeriod + 1 : 1, SECOND_ENGINE);
    }

    /** One cycle of the hum, {@code period} samples long. */
    private static float[] cycle(double[] harmonics, int period, double scale) {
        float[] cycle = new float[period];
        for (int i = 0; i < period; i++) {
            double v = 0;
            for (int k = 1; k <= harmonics.length; k++) {
                // Staggered phases keep the overtones' peaks from piling up.
                double phase = -Math.PI * k * (k - 1) / harmonics.length;
                v += harmonics[k - 1] * Math.sin(2 * Math.PI * k * i / period + phase);
            }
            cycle[i] = (float) (scale * v);
        }
        return cycle;
    }

    /** Sets how strong the engine hum is, for sounds that have one. Changes glide in rather than jump. */
    void setHum(float level) {
        humTarget = Math.max(0, Math.min(MAX_HUM, level));
    }

    /** Fills {@code out} with {@code frames} interleaved stereo frames, each sample within -1..1. */
    void render(float[] out, int frames) {
        for (int i = 0; i < frames; i++) {
            if (humLevel < humTarget) humLevel = Math.min(humTarget, humLevel + HUM_STEP);
            else if (humLevel > humTarget) humLevel = Math.max(humTarget, humLevel - HUM_STEP);
            float h = (hum[humPos] + hum2[humPos2]) * humLevel;
            if (++humPos == hum.length) humPos = 0;
            if (++humPos2 == hum2.length) humPos2 = 0;
            out[2 * i] = clamp(left.next() + h);
            out[2 * i + 1] = clamp(right.next() + h);
        }
    }

    // Filtered noise has no hard ceiling. The levels leave plenty of headroom, so
    // this only ever touches a freak sample, where it cannot be heard.
    private static float clamp(float v) {
        return v > 1f ? 1f : (v < -1f ? -1f : v);
    }

    private static final class Channel {
        // How fast and how far the blowing wanders: a random drift of about
        // half a decibel over a few seconds, never more than 1.5 dB.
        private static final double GUST_HZ = 0.3;
        private static final double GUST_DEPTH = 0.07;
        /** Brings {@link #gust}'s output, which is tiny, up to a spread of 1. */
        private static final double GUST_SCALE;

        static {
            double k = 1 - Math.exp(-2 * Math.PI * GUST_HZ / SAMPLE_RATE);
            GUST_SCALE = Math.sqrt(3 * (2 - k) / k);
        }

        private final Recipe recipe;
        private long seed;
        private double p0, p1, p2;

        // Stages a recipe does not use are left null and skipped.
        private final OnePole rumble;
        private final OnePole pinkTone;
        private final Biquad floor;
        private final OnePole top1;
        private final OnePole top2;
        private final OnePole airTone;
        private final Biquad blow;
        private final Biquad blowEdge;
        private final OnePole gust = new OnePole(GUST_HZ);
        private final Biquad drone1;
        private final Biquad drone2;

        Channel(Recipe recipe, long seed) {
            this.recipe = recipe;
            this.seed = seed;
            double humHz = (double) SAMPLE_RATE / recipe.humPeriod;
            rumble = recipe.rumble > 0 ? new OnePole(recipe.rumbleHz) : null;
            pinkTone = recipe.pinkHz > 0 ? new OnePole(recipe.pinkHz) : null;
            floor = recipe.floorHz > 0 ? Biquad.highPass(recipe.floorHz, 0.707) : null;
            top1 = recipe.topPoles >= 1 ? new OnePole(recipe.topHz) : null;
            top2 = recipe.topPoles >= 2 ? new OnePole(recipe.topHz) : null;
            airTone = recipe.airHz > 0 ? new OnePole(recipe.airHz) : null;
            // Twice through the same band-pass, for sides steep enough that the
            // band does not spill into the hiss range.
            blow = recipe.blow > 0 ? Biquad.bandPass(recipe.blowHz, 0.9) : null;
            blowEdge = recipe.blow > 0 ? Biquad.bandPass(recipe.blowHz, 0.9) : null;
            drone1 = recipe.drone > 0 ? Biquad.bandPass(humHz, 5) : null;
            drone2 = recipe.drone > 0 ? Biquad.bandPass(2 * humHz, 6) : null;
        }

        float next() {
            double w = white();
            double mix = recipe.white * w;
            if (rumble != null) mix += recipe.rumble * rumble.run(w);
            double p = recipe.pink > 0 || recipe.air > 0 ? pink(w) : 0;
            if (recipe.pink > 0) mix += recipe.pink * (pinkTone != null ? pinkTone.run(p) : p);
            if (floor != null) mix = floor.run(mix);
            if (blow != null) {
                double drift = Math.max(-2.5, Math.min(2.5, gust.run(white()) * GUST_SCALE));
                mix += recipe.blow * (1 + GUST_DEPTH * drift) * blowEdge.run(blow.run(w));
            }
            if (drone1 != null) mix += recipe.drone * (drone1.run(w) + 0.5 * drone2.run(w));
            if (top1 != null) mix = top1.run(mix);
            if (top2 != null) mix = top2.run(mix);
            mix *= recipe.gain;
            if (recipe.air > 0) mix += recipe.air * (airTone != null ? airTone.run(p) : p);
            return (float) mix;
        }

        /** Uniform white noise in -1..1 (xorshift64, top 24 bits). */
        private float white() {
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
