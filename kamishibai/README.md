# Kamishibai

The phone, tablet and foldable edition of [SmartTube](https://github.com/yuliskov/SmartTube) (MIT, by Yuriy Liskov).

Same features as SmartTube, because it **is** SmartTube underneath: the presenters, the player, the settings,
SponsorBlock, DeArrow, accounts and the YouTube API layer are the original code. Only the screens you touch were redrawn for a finger.

## What changed for the phone

- Sections in a bottom bar on phones, a side rail on foldables and landscape, a drawer on tablets. A "More" sheet lists the rest.
- Cards fill the width: 1 column on a phone, 2 to 6 on foldables and tablets, recomputed live on fold, unfold, rotation and split screen.
- Home rows swipe sideways with a peek of the next card. Pull down to refresh. Second tap on a tab scrolls to the top.
- Real search box with the phone keyboard, live suggestions, voice, filters.
- Real system density: text and buttons keep a normal size on any screen (SmartTube scales everything for a 1080p TV).
- Long press or the "⋮" button on a card opens the original SmartTube video menu.

## Build

JDK 17 and the Android SDK (API 34).

    ./gradlew :smarttubetv:assembleStmobileDebug

APK: `smarttubetv/build/outputs/apk/stmobile/debug/`. Application id: `com.orikami.kamishibai`
(installs next to SmartTube, doesn't replace it).

## Staying in sync with SmartTube

See [UPSTREAM-TOUCHPOINTS.md](UPSTREAM-TOUCHPOINTS.md). `kamishibai/sync-upstream.sh` merges the latest SmartTube and builds.
Once the repository is on GitHub, `.github/workflows/kamishibai-sync.yml` does it every Monday and opens a PR (or an issue if something needs a hand).

## Known limits of this first version

- The video player screen is still SmartTube's (touch gestures included: double tap to seek). A phone-first player is the next step.
- Android Auto: paused.
- The Settings screen opens SmartTube's own dialogs.

## License

MIT, as SmartTube. See `LICENSE`: the original copyright notice stays.
