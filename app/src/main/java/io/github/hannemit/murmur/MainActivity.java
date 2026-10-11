package io.github.hannemit.murmur;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.TextView;

/** One switch and three sounds. All the playing happens in {@link NoiseService}. */
public class MainActivity extends Activity {
    private static final Noise.Sound[] SOUNDS = Noise.Sound.values();
    private static final int[] SOUND_VIEWS = {R.id.sound_airplane, R.id.sound_fan, R.id.sound_white};

    private Noise.Sound selected;
    private TextView toggle;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        View root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        selected = NoiseService.savedSound(this);
        status = findViewById(R.id.status);
        toggle = findViewById(R.id.toggle);
        toggle.setOnClickListener(v -> {
            if (NoiseService.playing == null) start();
            else NoiseService.stop(this);
        });
        for (int i = 0; i < SOUNDS.length; i++) {
            Noise.Sound sound = SOUNDS[i];
            findViewById(SOUND_VIEWS[i]).setOnClickListener(v -> choose(sound));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        NoiseService.onChange = this::refresh;
        refresh();
    }

    @Override
    protected void onStop() {
        NoiseService.onChange = null;
        super.onStop();
    }

    private void start() {
        // The notification carries the Stop button. Sound plays whatever the answer.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 0);
        }
        NoiseService.play(this, selected);
    }

    private void choose(Noise.Sound sound) {
        selected = sound;
        NoiseService.saveSound(this, sound);
        if (NoiseService.playing != null) NoiseService.play(this, sound);
        refresh();
    }

    private void refresh() {
        boolean on = NoiseService.playing != null;
        if (on) selected = NoiseService.playing;
        toggle.setSelected(on);
        toggle.setText(on ? R.string.stop : R.string.start);
        status.setText(on ? R.string.status_on : R.string.status_off);
        for (int i = 0; i < SOUNDS.length; i++) {
            findViewById(SOUND_VIEWS[i]).setSelected(SOUNDS[i] == selected);
        }
    }
}
