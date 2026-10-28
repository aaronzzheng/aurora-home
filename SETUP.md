# Setting up Aurora Home

This guide takes a Google TV or Android TV from the box to Aurora as its home screen. It takes about 15 minutes. You need:

- the TV, with Android TV 9 or later (Google TV is fine), on your home Wi-Fi;
- a computer on the same Wi-Fi with [Android platform tools](https://developer.android.com/tools/releases/platform-tools) installed (that gives you `adb`);
- the app: either build it (see [README](README.md#building)) or use an `AuroraHome.apk` someone built for you.

## 1. Turn on developer options and network debugging

On the TV:

1. Go to **Settings → System → About**.
2. Scroll to **Android TV OS build** and press OK on it **7 times**, until it says you're a developer.
3. Go back to **Settings → System → Developer options**. Turn on **USB debugging**, and **Network debugging** (on some TVs it's called **ADB debugging** or **Wireless debugging**).
4. Find the TV's IP address under **Settings → Network & Internet →** your Wi-Fi network (for example `192.168.1.40`).

## 2. Install Aurora

On the computer, replacing the address with your TV's:

```bash
adb connect 192.168.1.40:5555
```

The TV asks **Allow USB debugging?** Tick **Always allow from this computer**, then choose **Allow**. Then install:

```bash
adb -s 192.168.1.40:5555 install -r AuroraHome.apk
```

To update later, run the same `install` line with the new APK. Your settings are kept.

## 3. Make Aurora the home screen

Press the remote's **Home** button. On many TVs a **"Select a home app"** list appears: pick **Aurora Home** and **Always**.

Google TV usually doesn't show that list and keeps going back to its own home screen. To use Aurora there, turn off Google's home screen from the computer:

```bash
adb -s 192.168.1.40:5555 shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```

Press **Home** again and Aurora comes up. Nothing is uninstalled, and you can undo it any time:

```bash
adb -s 192.168.1.40:5555 shell pm enable com.google.android.apps.tv.launcherx
```

> Google TV may switch its home screen back on after a big system update. If Home suddenly opens Google's screen again, run the `disable-user` line once more.

## 4. Turn on Aurora's background service

Bedtime, the sleep timer, night mode, gentle volume and the phone remote run in a small accessibility service. Aurora shows a reminder until it's on.

1. On the TV, go to **Settings → System → Accessibility** (on some TVs: **Settings → Device Preferences → Accessibility**).
2. Choose **Aurora Remote & Night Mode** and turn it **on**. Confirm when the TV asks.

Or from the computer:

```bash
adb -s 192.168.1.40:5555 shell settings put secure enabled_accessibility_services home.aurora/home.aurora.AuroraService
```

## 5. Optional: watch time and screensaver

- **"This week" watch time** (on the home screen and in the phone remote): needs usage access. From the computer:

  ```bash
  adb -s 192.168.1.40:5555 shell appops set home.aurora GET_USAGE_STATS allow
  ```

- **Screensaver:** in **Settings → System → Ambient mode / Screen saver**, choose **Aurora Home**. It shows the wallpapers or famous paintings; switch between them in Aurora's **Settings → Screensaver**.

## 6. Pick a look

Open **Settings** on Aurora's home screen and choose **Theme**. There are nine looks, and the games and the phone remote follow whichever one you pick.

Other things worth a look in Settings:

- **Interface sound volume** (Whisper to Full).
- **Gentle volume**, so holding volume doesn't race.
- A **PIN** for Settings.
- **Kid lock**.
- **Bedtime** and the **morning screen**.

## 7. Use your phone as a remote

<img src="docs/screenshots/remote-classic.jpg" width="260" align="right" alt="The phone remote in the Classic look">

1. On the TV, open **Settings → Phone remote → Connect a phone**. A QR code appears.
2. Scan it with your phone's camera. The remote opens in the browser and pairs by itself. If you type the address instead, tap **Show code on TV** and enter the 6-digit code.
3. **Add it to your home screen** so it opens like an app:
   - **iPhone (Safari):** tap Share, then **Add to Home Screen**.
   - **Android (Chrome):** open the ⋮ menu, then **Add to Home screen**.

The phone and TV must be on the same Wi-Fi. If the TV has a Settings PIN, pairing asks for it too.

**Arrows in YouTube, Netflix and similar apps.** The phone's arrows work on Aurora, in Settings and in most apps. Apps that draw their own screens ignore them. To fix that:

1. Turn on **Settings → Phone remote → Arrows in every app**.
2. When the TV asks **Allow debugging?**, tick **Always allow** and choose **Allow**.

Aurora then presses the buttons through the TV's own debugging connection. It only ever sends remote buttons.

**Turning the TV on from the phone.** When the TV is asleep, the power button on the phone turns green and wakes it. This only works if the TV keeps its Wi-Fi on in standby. On most Google TVs that's **Settings → System → Power & energy**: turn off any low-power or "eco" standby, and turn on **Quick start**, **Network standby** or **Wake on Wi-Fi**, whichever your TV has. In a deep standby nothing on the network can reach the TV, so use its own remote.

To see or remove paired phones, open **Settings → Phone remote → Paired phones**.

<br clear="right">

## Troubleshooting

| Problem | Fix |
|---|---|
| `adb connect` fails or times out | The TV is asleep or on another network. Wake it, check the IP, and make sure network debugging is still on. |
| A warning says Aurora's background service is off | Do step 4 again. Some TVs turn it off after an Aurora update. |
| Home opens Google's screen again | Run the `disable-user` line from step 3 again. |
| The phone remote won't load | Use `http://` (not https), with the TV's IP and port 8765, e.g. `http://192.168.1.40:8765`. Phone and TV must share the same Wi-Fi. |
| The phone's arrows don't move anything in an app | Turn on **Arrows in every app** (step 7). |
| Interface sounds are too loud or too quiet | **Settings → Interface sound volume**. |
| An update won't install ("signatures don't match") | It was built with a different signing key. Either build with the original key, or uninstall Aurora first (this erases its settings; back them up from the phone remote's **Backup** section). |

## Undoing everything

```bash
adb -s 192.168.1.40:5555 shell pm enable com.google.android.apps.tv.launcherx
adb -s 192.168.1.40:5555 uninstall home.aurora
```

You can then turn off developer options in **Settings → System → Developer options**.
