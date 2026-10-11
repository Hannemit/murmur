package io.github.hannemit.murmur;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Named combinations of sound, volume and hum that the user has saved. */
final class Favourites {
    private static final String PREFS = "favourites";
    private static final String KEY = "list";

    static final class Favourite {
        final String name;
        final Noise.Sound sound;
        final int volume;
        final int hum;

        Favourite(String name, Noise.Sound sound, int volume, int hum) {
            this.name = name;
            this.sound = sound;
            this.volume = volume;
            this.hum = hum;
        }

        boolean matches(Noise.Sound sound, int volume, int hum) {
            return this.sound == sound && this.volume == volume && this.hum == hum;
        }
    }

    private Favourites() {
    }

    /** The saved favourites, oldest first. */
    static List<Favourite> load(Context context) {
        List<Favourite> list = new ArrayList<>();
        String json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                try {
                    list.add(new Favourite(o.getString("name"),
                            Noise.Sound.valueOf(o.getString("sound")),
                            o.getInt("volume"), o.getInt("hum")));
                } catch (IllegalArgumentException e) {
                    // Saved with a sound a later version no longer has; leave it out.
                }
            }
        } catch (JSONException e) {
            // Unreadable, which should not happen. Start again from nothing.
        }
        return list;
    }

    /** Adds a favourite. One already saved under the same name is replaced where it stands. */
    static void add(Context context, Favourite favourite) {
        List<Favourite> list = load(context);
        int at = indexOf(list, favourite.name);
        if (at >= 0) list.set(at, favourite);
        else list.add(favourite);
        store(context, list);
    }

    static void remove(Context context, String name) {
        List<Favourite> list = load(context);
        int at = indexOf(list, name);
        if (at >= 0) list.remove(at);
        store(context, list);
    }

    private static int indexOf(List<Favourite> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name.equals(name)) return i;
        }
        return -1;
    }

    private static void store(Context context, List<Favourite> list) {
        JSONArray array = new JSONArray();
        try {
            for (Favourite f : list) {
                array.put(new JSONObject()
                        .put("name", f.name)
                        .put("sound", f.sound.name())
                        .put("volume", f.volume)
                        .put("hum", f.hum));
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e); // only thrown for non-finite numbers
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, array.toString()).apply();
    }
}
