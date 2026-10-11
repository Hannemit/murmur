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
    private volatile float hum = 1;
    private volatile float volume = 1;
    private Thread thread;

    /** Starts playing, or cross-fades to {@code sound} if something is already playing. */
    synchronized void play(Noise.Sound sound) {
        wanted = sound;
        if (thread == null) {
            thread = new Thread(this::run, "murmur-audio");
            thread.start();
        }
    }

    /** Sets the strength of the airplane's engine hum; see {@link Noise#setHum}. */
    void setHum(float level) {
        hum = level;
    }

    /** Sets the app's own volume, from 0 (silent) to 1 (as loud as the phone's volume allows). */
    void setVolume(float level) {
        volume = level;
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
        float level = volume;
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
            noise.setHum(hum);
            noise.render(block, BLOCK_FRAMES);
            for (int i = 0; i < block.length; i += 2) {
                if (gain < goal) gain = Math.min(goal, gain + step);
                else if (gain > goal) gain = Math.max(goal, gain - step);
                // The volume glides at the same rate, so dragging its slider never crackles.
                float volume = this.volume;
                if (level < volume) level = Math.min(volume, level + step);
                else if (level > volume) level = Math.max(volume, level - step);
                block[i] *= gain * level;
                block[i + 1] *= gain * level;
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
