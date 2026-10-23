# Kamishibai

The phone, tablet and foldable edition of [SmartTube](https://github.com/yuliskov/SmartTube) (MIT, by Yuriy Liskov).

Same features as SmartTube, because it **is** SmartTube underneath: the presenters, the player, the settings,
SponsorBlock, DeArrow, accounts and the YouTube API layer are the original code. Only the screens you touch were redrawn for a finger.

## What changed for the phone

- Same layout on every screen of a foldable: the name and logo on top with notifications and search,
  topic filters on Home, and a bottom bar with Home, Shorts, Subscriptions, History and Settings.
- The compass at the start of the Home filters opens "Explore": the topic feeds, then "My library"
  (playlists, queue, notifications, pinned channels, blocked channels...).
- Cards fill the width: 1 column on the folded screen, a grid elsewhere, recomputed live on fold, unfold, rotation and split screen.
- Settings and their sub-menus are full screen pages with a top bar and a back arrow, like any Android app.
  Short menus of actions (the "⋮" of a video) and anything opened over the player are bottom sheets.
- Google sign in made for a phone: the code, a button that opens the activation page in the browser
  (not in the YouTube app) with the code copied, and a QR code for another device.
- Watch page with a 16:9 player, comments panel, picture-in-picture and downloads.
- Real search box with the phone keyboard, live suggestions, voice, filters.
- Dynamic interface scale, transparent system bars, day and night palettes (washi and vermilion, ink and vermilion).

## Build

JDK 17 and the Android SDK (API 34).

    ./gradlew :smarttubetv:assembleStmobileDebug

APK: `smarttubetv/build/outputs/apk/stmobile/debug/`. Application id: `com.orikami.kamishibai`
(installs next to SmartTube, doesn't replace it).

## Staying in sync with SmartTube

See [UPSTREAM-TOUCHPOINTS.md](UPSTREAM-TOUCHPOINTS.md). `kamishibai/sync-upstream.sh` merges the latest SmartTube and builds.
Once the repository is on GitHub, `.github/workflows/kamishibai-sync.yml` does it every Monday and opens a PR (or an issue if something needs a hand).

## Known limits

- Android Auto: paused.
- Still SmartTube's TV screens: adding a remote device (AddDevice) and the web browser screen.

## License

MIT, as SmartTube. See `LICENSE`: the original copyright notice stays.
