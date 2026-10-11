# Murmur

Steady noise to sleep to, for Android. Three sounds:

- **Airplane**: the deep rumble of a cabin in flight, with a soft engine hum under it
- **Fan**: an even whoosh
- **White noise**: plain static

Press Start and it plays until you press Stop, with the screen off or another app open.
There is no timer and no recording behind it: each sound is generated as it plays, so it never
reaches an end, never loops, and is the same in the last hour of the night as in the first.
Starting, stopping and switching sounds all fade.

Nothing interrupts it. Other apps' sounds (a notification, an alarm) play over it instead of
pausing it. Stop it from the app or from the Stop button on its notification.

## Install

Download `murmur.apk` from the [latest release](https://github.com/Hannemit/murmur/releases/latest)
on your phone and open it. Android will ask you to allow installs from your browser or file manager.

The first time you press Start, Android asks whether Murmur may show notifications. Allow it:
that is where the Stop button lives. If your phone is strict about background apps and the sound
ever stops by itself, set Murmur's battery use to "Unrestricted" in the phone's app settings.

## Build

Requires JDK 17 and the Android SDK (platform 35).

```
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Every push to `main` builds the APK on GitHub and publishes it as a new release.

The sounds are made in `Noise.java`, which uses nothing from Android, so the test runs on a plain JVM.
It renders ten minutes of each sound and checks that the level never dips, jumps or clips:

```
javac -d build/test app/src/main/java/io/github/hannemit/murmur/Noise.java tests/NoiseTest.java
java -cp build/test io.github.hannemit.murmur.NoiseTest
```
