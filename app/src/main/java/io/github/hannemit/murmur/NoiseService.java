package io.github.hannemit.murmur;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

/**
 * Keeps the sound going while the screen is off or another app is open.
 *
 * Three things make that hold for a whole night: it is a foreground service, so
 * Android does not reclaim it; it holds a wake lock, so the processor keeps
 * producing audio while the phone sleeps; and it never asks for audio focus, so
 * a notification or another app's sound plays over it instead of pausing it.
 */
public class NoiseService extends Service {
    private static final String TAG = "Murmur";
    private static final String ACTION_PLAY = "io.github.hannemit.murmur.PLAY";
    private static final String ACTION_STOP = "io.github.hannemit.murmur.STOP";
    private static final String EXTRA_SOUND = "sound";
    private static final String CHANNEL = "playback";
    private static final int NOTIFICATION_ID = 1;
    private static final String PREFS = "murmur";

    // The player outlives any one instance of the service, so a restart by the
    // system picks up the same audio thread rather than starting a second one.
    private static final Player player = new Player();

    /** The sound playing now, or null when off. Read and written on the main thread. */
    static Noise.Sound playing;
    /** Called on the main thread whenever {@link #playing} changes. */
    static Runnable onChange;

    private PowerManager.WakeLock wakeLock;

    static void play(Context context, Noise.Sound sound) {
        context.startForegroundService(new Intent(context, NoiseService.class)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_SOUND, sound.name()));
    }

    static void stop(Context context) {
        // Only if it is running: starting a service just to stop it is pointless.
        if (playing != null) context.startService(stopIntent(context));
    }

    private static Intent stopIntent(Context context) {
        return new Intent(context, NoiseService.class).setAction(ACTION_STOP);
    }

    /** The sound last chosen, which is what the app opens on and what a restart resumes. */
    static Noise.Sound savedSound(Context context) {
        String name = context.getSharedPreferences(PREFS, MODE_PRIVATE).getString(EXTRA_SOUND, null);
        try {
            return name == null ? Noise.Sound.AIRPLANE : Noise.Sound.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Noise.Sound.AIRPLANE;
        }
    }

    static void saveSound(Context context, Noise.Sound sound) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(EXTRA_SOUND, sound.name()).apply();
    }

    static int label(Noise.Sound sound) {
        switch (sound) {
            case FAN: return R.string.sound_fan;
            case WHITE: return R.string.sound_white;
            default: return R.string.sound_airplane;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wakeLock = getSystemService(PowerManager.class)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "murmur:playback");
        wakeLock.setReferenceCounted(false);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, getString(R.string.channel_playback), NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            silence();
            stopSelf();
            return START_NOT_STICKY;
        }

        // A null intent means Android killed the process and is bringing the
        // service back, so carry on with whatever was playing.
        Noise.Sound sound = savedSound(this);
        if (intent != null && intent.hasExtra(EXTRA_SOUND)) {
            sound = Noise.Sound.valueOf(intent.getStringExtra(EXTRA_SOUND));
            saveSound(this, sound);
        }

        try {
            Notification notification = notification(sound);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (RuntimeException e) {
            // Android can refuse foreground status to a service it restarted in
            // the background. Playing without it is still better than silence.
            Log.w(TAG, "Could not enter the foreground", e);
        }

        wakeLock.acquire();
        player.play(sound);
        setPlaying(sound);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        silence();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void silence() {
        player.stop();
        if (wakeLock.isHeld()) wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        setPlaying(null);
    }

    private static void setPlaying(Noise.Sound sound) {
        playing = sound;
        if (onChange != null) onChange.run();
    }

    private Notification notification(Noise.Sound sound) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 0,
                stopIntent(this), PendingIntent.FLAG_IMMUTABLE);
        Icon icon = Icon.createWithResource(this, R.drawable.ic_notification);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(icon)
                .setContentTitle(getString(label(sound)))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(
                        icon, getString(R.string.stop), stop).build());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Otherwise Android waits ten seconds before showing it.
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }
}
