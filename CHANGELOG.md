# Changelog

All notable changes to Riplox are documented here.
Versioning: **MAJOR.MINOR.PATCH** — new features bump MINOR, fixes bump PATCH.

## [1.1.0] — 2026-09-07

Photo posts now download, sharing shows real progress, and the engine keeps itself current — even if you never open the app.

### Added
- **Photo posts** — Instagram carousels, TikTok slideshows and image posts now download and land in **Pictures**, opening in your gallery instead of failing with "File not found"
- **Live progress when you share** — the ⚡ Instant tile now shows a sheet with the title, thumbnail, a progress bar, **Cancel**, and **Open / Share** when it's done, instead of a toast that vanished
- **Update & retry** — one tap on a failed download refreshes the engine and tries again
- **Batch paste** — paste several links at once and every one of them is queued
- **Photo** filter in History, with the right icon and the right app when you tap
- **Update engine** in Settings → Downloads, showing the engine version, when it was last checked, and what actually happened
- A notice on the home screen when a **newer Riplox** has been released

### Fixed
- Anyone who only downloaded from another app's share sheet **never got engine updates** — every download now keeps the engine current, so sites don't quietly stop working
- A failed download caused by an out-of-date engine now updates and retries itself once, instead of failing for good
- "Engine updated" used to be shown even when the update had failed — it now reports what really happened
- Photo files were saved as videos: wrong gallery album, wrong file type, and tapping one opened a video player that could never play it

## [1.0.2] — 2026-07-17

Background downloads made bulletproof — start instantly, never jam, real progress all the way to the gallery.

### Fixed
- Downloads sometimes never started until the app was opened — every new download is now escorted by a lightweight helper service to its own protected foreground slot, so Android can't defer or freeze the start (works with the app closed)
- "Stuck at 100%" — the silent merge step after 100% is no longer mistaken for a stall; it gets a proper 20-minute window
- A truly hung download could permanently jam a queue slot ("starting…" forever) — hangs are now fully cancelled and retried, and the slot is always freed
- A download stuck at 0% in a retry loop is now detected and restarted automatically after 15 minutes
- Startup cleanup could delete the temp folder of a download that was just starting — it now only removes leftovers older than 24 hours
- Large files looked frozen on "Finishing…" while being copied to the gallery

### Added
- Live **"Saving to gallery… X%"** progress during the final copy of big files
- 🛡 **Fix background downloads** — guided 3-step setup (battery, auto-start, lock in recents) on the home screen and in Settings → Downloads, with live status — for phones with aggressive battery managers (XOS, MIUI, …)
- Downloads hold wake + Wi-Fi locks so the phone can't doze mid-download

## [1.0.0] — 2026-07-06

First public release. 🎉

### Added
- Download video (up to 4K) & audio (MP3/M4A/OPUS/WAV/FLAC) from 1000+ sites
- Exact format/size picker per download
- ⚡ Instant share-download + 🎛 Download Options share targets
- Smart defaults — the download popup remembers your last choices
- Downloads manager: live progress, queue, failed-with-retry, completed (tap to play)
- Background queue with auto-retry (configurable 1–5 attempts) — survives app close & reboot
- Account connect (in-app real-site login) for private / age-restricted / guest-blocked videos, with true login detection
- Saved-cookies page — disconnected accounts' cookies stay under your control until you delete them
- 🔒 Secret Vault — fingerprint/PIN-locked hidden storage (excluded from gallery, history and backups), entered by tapping the home logo
- History: tap-to-play, search, filters, multi-select (bulk delete / copy links), share with file
- Link packs: export selected links to a file (optional password/AES-256) and import + select + batch-download on any device
- In-app player: fullscreen, speed, "Open with…" to any external player
- Backup / Restore (settings + history + logins in one file)
- First-run guide, Tips page with full feature guide
- AMOLED-black Material 3 UI with the Steel Blue brand look
