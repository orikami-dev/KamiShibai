<p align="center">
  <img src="docs/images/kamishibai-icone.png" width="128" alt="KamiShibai">
</p>

<h1 align="center">KamiShibai</h1>
<p align="center"><b>Scroll. Watch.</b><br>by ORIKAMI</p>

<p align="center">
  <a href="https://github.com/orikami-dev/kamishibai/releases/latest">⬇️ <b>Download the latest version</b></a>
  ·
  <a href="https://github.com/orikami-dev/kamishibai/releases">📦 All versions</a>
  ·
  <a href="CHANGELOG.md">📝 Changelog</a>
</p>

---

## 🎴 The project

KamiShibai is ad-free YouTube, made for phones and foldables. It keeps the whole engine of [SmartTube](https://github.com/yuliskov/SmartTube) (no ads, SponsorBlock, quality choice, background playback), originally designed for TVs, and gives it a real touch interface.

| | |
|---|---|
| 📺 **A single screen** | The video rises in a panel over Home and slides back down with a gesture. |
| 🪟 **Mini player** | A bar above the tabs: the video keeps playing while you browse. |
| 📱 **Shorts** | Vertical, with the actions on the side, like on YouTube. |
| 🔍 **Built-in search** | The search box opens in the top bar, and the results replace the feed. |
| 🔑 **Google sign-in** | With an activation code, like on a TV: your password never goes through the app. |
| 📥 **Downloads** | Videos and audio, saved in your Movies and Music folders. |
| 🔄 **Automatic updates** | New versions download by themselves and are verified before installation. |

## 🎨 Six palettes

Each palette colors the interface, the logo and the app icon.

| Palette | In one sentence |
|---|---|
| 📜 **Washi** | Japanese paper and the vermilion of torii gates. |
| 🖋️ **Sumi** | Indian ink, deep black and violet. |
| 🌊 **Ai** | The indigo blue of Japanese dyes. |
| 🍵 **Matcha** | The soft green of tea. |
| 🌸 **Sakura** | The pink of cherry blossoms. |
| 🪙 **Kin** | The gold of lacquer leaf. |

<p align="center">
  <img src="docs/images/palettes-1.0.png" width="92%" alt="The six KamiShibai icons">
</p>

## ✨ Highlights

- 📐 Same layout on every screen of a foldable, sized for each screen.
- 📖 Half-open foldable in full screen: the video above the hinge, the controls below.
- 🧭 Bottom bar: Home, Shorts, Subscriptions, History, Settings; Explore for the library.
- 🎛️ Player menus that open from their button, settings as tiles.
- 🚀 Direct start, no logo screen.
- 🤝 Installs next to SmartTube, without replacing it.

## ⭐ Major updates

| Version | What it brought |
|---|---|
| [1.2.0 ⭐](https://github.com/orikami-dev/kamishibai/releases/tag/v1.2.0) | Mini player bar, automatic updates, log export, end of the TV interface. |
| [1.0 ⭐](https://github.com/orikami-dev/kamishibai/releases/tag/v1.0) | First public version: a single screen with a mini player, Shorts, six palettes, half-open foldables, built-in updates. |

## 🛠️ The repository

| Folder | Content |
|---|---|
| `smarttubetv/src/stmobile/` | All of KamiShibai's phone interface |
| `kamishibai/` | Notes, points where KamiShibai touches SmartTube, sync, logo generators |
| rest of the repository | SmartTube's code, kept as is to follow its updates |
| `docs/` | Notes of each version (`docs/releases`), images |

Build (JDK 17, Android SDK 34): `./gradlew :smarttubetv:assembleStmobileDebug`. The SmartTube files that were touched are listed in [kamishibai/UPSTREAM-TOUCHPOINTS.md](kamishibai/UPSTREAM-TOUCHPOINTS.md).

## ⚖️ Licenses

KamiShibai is a modified version of [SmartTube](https://github.com/yuliskov/SmartTube) by yuliskov and its contributors, distributed like it under the MIT license; the original copyright notice stays in [LICENSE](LICENSE) (see also [NOTICE.md](NOTICE.md)). To support SmartTube: [ko-fi.com/smarttube](https://ko-fi.com/smarttube). KamiShibai is not affiliated with YouTube or Google.
