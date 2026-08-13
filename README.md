<div align="center">
<img src="https://avatars.githubusercontent.com/u/262700051?s=200&v=4" width="160" height="160" style="display: block; margin: 0 auto"/>
<h1>Zemer</h1>

### Kosher YouTube Music client for Android

</div>

Zemer is YouTube Music for people who filter what they listen to. Every artist in the app was reviewed and approved by hand. If an artist isn't on the whitelist, their music doesn't exist here: not in search, not in radio, not in recommendations.

On top of the whitelist there are per-home choices: allow or block female vocalists, block music videos, block podcasts, and a KidZone with its own stricter list. Filters can be locked with a sync account so they stay put.

## Features

- Search, radio, artist and album pages all served from Zemer's own hand-built catalog
- Hebrew and English search that cross-matches (a Hebrew query finds romanized titles, and the reverse)
- Home rows and charts ranked by anonymous listening stats, tied to a random install identifier and never to a person
- Zemer Stations: live synchronized radio, everyone hears the same song at the same moment
- Podcasts, whitelisted per publisher: approve a channel and its whole catalog is in
- Music statuses on the home screen, savable to your gallery
- Genre browsing with genre radio
- Downloads for offline listening, including video downloads
- Video songs play as audio by default; watching is a per-play choice, with quality up to 2160p
- Search backup: download the catalog once and search keeps working when the server is unreachable
- Synced lyrics, casting, Android Auto, a home screen widget, and music recognition
- Full theming with a palette picker and pure black mode
- A playback option for devices whose network filter blocks streaming
- Backup and restore, and in-app updates

## Download

Get the app at [zemer.io](https://zemer.io). Updates are checked in-app.

## Screenshots

<p align="center">
  <img src="https://github.com/user-attachments/assets/2acb160c-4859-431c-bc48-a7e10451ef4a" width="22%" alt="Artists" />
  <img src="https://github.com/user-attachments/assets/9bc46d9c-d051-4be1-8b3c-1ac601b02b3f" width="22%" alt="Home" />
  <img src="https://github.com/user-attachments/assets/852979f3-b1ed-4935-bac2-ca559294bdf5" width="22%" alt="Library" />
  <img src="https://github.com/user-attachments/assets/abcc9b92-2f6a-4464-a9b6-0620e82ffebe" width="22%" alt="Content Filters" />
</p>

## Building

JDK 21, then:

```
git submodule update --init --recursive
./gradlew :app:assembleDebug
```

See `AGENTS.md` for the full picture.

## Credits

Zemer is a fork of [Metrolist](https://github.com/MetrolistGroup/Metrolist), and keeps its GPL-3.0 license. For commit history before this repo was created, see [Zemer-Old](https://github.com/ZemerTeam/Zemer-Old).
