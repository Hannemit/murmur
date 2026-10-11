package io.github.hannemit.murmur;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.function.IntConsumer;

/** One switch and three sounds. All the playing happens in {@link NoiseService}. */
public class MainActivity extends Activity {
    private static final Noise.Sound[] SOUNDS = Noise.Sound.values();

    private Noise.Sound selected;
    private TextView toggle;
    private TextView status;
    private View humRow;
    private SeekBar hum;
    private final TextView[] choices = new TextView[SOUNDS.length];

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
        ViewGroup list = findViewById(R.id.sounds);
        for (int i = 0; i < SOUNDS.length; i++) {
            Noise.Sound sound = SOUNDS[i];
            choices[i] = (TextView) getLayoutInflater().inflate(R.layout.sound_choice, list, false);
            choices[i].setText(NoiseService.label(sound));
            choices[i].setOnClickListener(v -> choose(sound));
            list.addView(choices[i]);
        }

        humRow = findViewById(R.id.hum_row);
        hum = findViewById(R.id.hum);
        slider(R.id.volume, NoiseService.MAX_VOLUME, NoiseService.savedVolume(this),
                value -> NoiseService.setVolume(this, value));
        slider(R.id.hum, NoiseService.MAX_HUM, NoiseService.savedHum(this),
                value -> NoiseService.setHum(this, value));
    }

    private void slider(int id, int max, int value, IntConsumer onChange) {
        SeekBar bar = findViewById(id);
        bar.setMax(max);
        bar.setProgress(value);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) onChange.accept(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
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
            choices[i].setSelected(SOUNDS[i] == selected);
        }
        // Only the engine sounds have a hum. For the rest the slider stays in
        // place, greyed out, so the list below it does not jump about.
        humRow.setAlpha(selected.hasHum() ? 1f : 0.3f);
        hum.setEnabled(selected.hasHum());
    }
}
