package io.github.hannemit.murmur;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Process;
import android.os.SystemClock;

/**
 * Streams a {@link Noise} to the speaker from its own thread until told to stop.
 *
 * Every change of level is a ramp: starting, stopping and switching sounds all
 * fade, so nothing ever clicks or jumps.
 */
final class Player {
    private static final int BLOCK_FRAMES = 2048;
    private static final float FADE_SECONDS = 0.8f;
    // Half a second of audio queued in the system rides out any hiccup in this
    // thread. The price is that taps take that long to be heard, which is fine here.
    private static final int BUFFER_BYTES = Noise.SAMPLE_RATE / 2 * 2 * 4;

    private volatile Noise.Sound wanted;
    private Thread thread;

    /** Starts playing, or cross-fades to {@code sound} if something is already playing. */
    synchronized void play(Noise.Sound sound) {
        wanted = sound;
        if (thread == null) {
            thread = new Thread(this::run, "murmur-audio");
            thread.start();
        }
    }

    /** Fades out and then releases the audio output. */
    synchronized void stop() {
        wanted = null;
    }

    private void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        float[] block = new float[BLOCK_FRAMES * 2];
        float step = 1f / (FADE_SECONDS * Noise.SAMPLE_RATE);
        float gain = 0;
        Noise noise = null;
        Noise.Sound playing = null;
        AudioTrack track = null;

        while (true) {
            Noise.Sound target = wanted;
            if (noise == null) {
                // Silent at this point: either just started or just faded out.
                if (target == null) {
                    synchronized (this) {
                        if (wanted == null) {
                            thread = null;
                            break;
                        }
                    }
                    continue;
                }
                playing = target;
                noise = new Noise(playing);
            }

            float goal = target == playing ? 1 : 0;
            noise.render(block, BLOCK_FRAMES);
            for (int i = 0; i < block.length; i += 2) {
                if (gain < goal) gain = Math.min(goal, gain + step);
                else if (gain > goal) gain = Math.max(goal, gain - step);
                block[i] *= gain;
                block[i + 1] *= gain;
            }
            if (gain == 0 && goal == 0) noise = null;

            if (track == null) track = open();
            if (track == null || !write(track, block)) {
                // The output went away under us, which happens if the system's
                // audio service restarts. Drop it and build a new one.
                if (track != null) track.release();
                track = null;
                SystemClock.sleep(250);
            }
        }

        if (track != null) {
            try {
                track.stop(); // lets the faded tail already queued play out
            } catch (IllegalStateException ignored) {
                // Already dead; nothing left to play.
            }
            track.release();
        }
    }

    private static AudioTrack open() {
        try {
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(Noise.SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(BUFFER_BYTES)
                    // Latency does not matter and the battery has to last all night.
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
                    .build();
            track.play();
            return track;
        } catch (UnsupportedOperationException | IllegalStateException | IllegalArgumentException e) {
            return null;
        }
    }

    /** Blocks until the whole block is queued. Returns false if the track has failed. */
    private static boolean write(AudioTrack track, float[] block) {
        int done = 0;
        while (done < block.length) {
            int n = track.write(block, done, block.length - done, AudioTrack.WRITE_BLOCKING);
            if (n <= 0) return false;
            done += n;
        }
        return true;
    }
}
