package io.github.hannemit.murmur;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.function.IntConsumer;

/** One switch, the sounds and their settings. All the playing happens in {@link NoiseService}. */
public class MainActivity extends Activity {
    private static final Noise.Sound[] SOUNDS = Noise.Sound.values();

    private Noise.Sound selected;
    private TextView toggle;
    private TextView status;
    private SeekBar volume;
    private SeekBar hum;
    private ViewGroup favourites;
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

        volume = slider(R.id.volume, NoiseService.MAX_VOLUME, NoiseService.savedVolume(this),
                value -> NoiseService.setVolume(this, value));
        hum = slider(R.id.hum, NoiseService.MAX_HUM, NoiseService.savedHum(this),
                value -> NoiseService.setHum(this, value));

        favourites = findViewById(R.id.favourites);
        findViewById(R.id.save_favourite).setOnClickListener(v -> askName());
        showFavourites();
    }

    private SeekBar slider(int id, int max, int value, IntConsumer onChange) {
        SeekBar bar = findViewById(id);
        bar.setMax(max);
        bar.setProgress(value);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                onChange.accept(progress);
                markFavourites();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
        return bar;
    }

    /** Rebuilds the rows of saved favourites. */
    private void showFavourites() {
        favourites.removeAllViews();
        for (Favourites.Favourite favourite : Favourites.load(this)) {
            TextView row = (TextView) getLayoutInflater()
                    .inflate(R.layout.sound_choice, favourites, false);
            row.setText(favourite.name);
            row.setTag(favourite);
            row.setOnClickListener(v -> apply(favourite));
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.remove_title, favourite.name))
                        .setPositiveButton(R.string.remove, (dialog, which) -> {
                            Favourites.remove(this, favourite.name);
                            showFavourites();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                return true;
            });
            favourites.addView(row);
        }
        markFavourites();
    }

    /** Lights up whichever favourite the current settings match, if any. */
    private void markFavourites() {
        for (int i = 0; i < favourites.getChildCount(); i++) {
            View row = favourites.getChildAt(i);
            Favourites.Favourite favourite = (Favourites.Favourite) row.getTag();
            row.setSelected(favourite.matches(selected, volume.getProgress(), hum.getProgress()));
        }
    }

    private void apply(Favourites.Favourite favourite) {
        volume.setProgress(favourite.volume);
        hum.setProgress(favourite.hum);
        NoiseService.setVolume(this, favourite.volume);
        NoiseService.setHum(this, favourite.hum);
        choose(favourite.sound);
    }

    private void askName() {
        EditText name = new EditText(this);
        name.setHint(R.string.save_hint);
        name.setSingleLine();
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        int margin = Math.round(20 * getResources().getDisplayMetrics().density);
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(margin, margin / 2, margin, 0);
        frame.addView(name);
        new AlertDialog.Builder(this)
                .setTitle(R.string.save_title)
                .setView(frame)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String text = name.getText().toString().trim();
                    if (text.isEmpty()) return;
                    Favourites.add(this, new Favourites.Favourite(
                            text, selected, volume.getProgress(), hum.getProgress()));
                    showFavourites();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        name.requestFocus();
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
        // Only when it changes: restarting the same sound would be a needless fade out and in.
        if (NoiseService.playing != null && NoiseService.playing != sound) NoiseService.play(this, sound);
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
        markFavourites();
    }
}
