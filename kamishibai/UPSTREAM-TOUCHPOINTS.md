# Where Kamishibai touches SmartTube's files

Everything else is new files, so upstream updates merge without conflicts.

| File | What changed | If it conflicts |
|---|---|---|
| `smarttubetv/build.gradle` | last 3 lines: `apply from: 'kamishibai.gradle'` | keep upstream's file, re-add the 3 lines at the end |
| `common/.../misc/MotherActivity.java` | `isTvDensityEnabled()` hook (11 lines) used by `initDpi()` | keep upstream's version, re-add the hook: `initDpi()` returns early when the hook is false |

## New files (never in upstream, never conflict)

- `smarttubetv/kamishibai.gradle`: the `stmobile` flavor (app id `com.orikami.kamishibai`).
- `smarttubetv/src/stmobile/`: the whole touch UI, its resources and its manifest.
- `kamishibai/`: this folder (sync script, docs).
- `.github/workflows/kamishibai-*.yml`: automatic sync and build.

## What follows upstream automatically

Presenters (`BrowsePresenter`, `SearchPresenter`...), the player, the settings dialogs, accounts, SponsorBlock,
DeArrow, the YouTube API layer (`MediaServiceCore` submodule), update checker and every preference.
A bug fixed or an API changed upstream is fixed in Kamishibai at the next sync.

## When a sync can break the build

Only if upstream changes one of these contracts that the touch UI implements or calls:

- `BrowseView`, `SearchView`, `ChannelView`, `ChannelUploadsView` (`common/.../app/views/`)
- `VideoGroupPresenter`, `BrowsePresenter`, `SearchPresenter`, `ChannelPresenter`, `ChannelUploadsPresenter`
- `VideoGroup`, `Video`, `BrowseSection`, `SettingsGroup`, `SettingsItem`
- `VideoGroupObjectAdapter` (data side of the cards)

The compile step of the sync fails right away and points to the line. The fix is almost always one method to add
in `MobileBrowseFragment`, `MobileSearchFragment`, `MobileChannelFragment` or `MobileChannelUploadsFragment`.
