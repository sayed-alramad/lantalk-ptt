# Roadmap

LAN Talkie is an offline Wi-Fi/hotspot walkie-talkie for Android. This roadmap extends it with
**TCP** for text messages, files and recorded voice messages. Live push-to-talk voice stays on **UDP**
(see [ADR 0001](decisions/0001-udp-for-voice-tcp-for-messages.md)).

Each milestone is developed on its own branch (`feature/mX-...`), merged by pull request after CI is
green and the two-phone test in [TESTING.md](TESTING.md) passes, then tagged (`v0.X.0`) and published
as a GitHub Release with the APK attached.

| Milestone | Tag | Goal | Status |
|-----------|-----|------|--------|
| M0 | v0.1.0 | Repository, docs, CI, releases, stable signing | In progress |
| M1 | v0.2.0 | TCP layer + text messages with ACK | Planned |
| M2 | v0.3.0 | Chat UI + message history (Room) | Planned |
| M3 | v0.4.0 | File transfer with progress | Planned |
| M4 | v0.5.0 | Recorded voice messages | Planned |
| M5 | v0.6.0 | Reliability, pairing code, optional encryption | Planned |
| M6 | v1.0.0 | Polish and stable release | Planned |

## M0 — Repository foundation
**Goal:** a well-organised repo that anyone can build and download from.
- README, ARCHITECTURE, TESTING, CHANGELOG, CONTRIBUTING, ADR 0001.
- CI builds the APK on every push/PR; a release workflow publishes the APK when a `v*` tag is pushed.
- Unused Firebase dependencies removed (smaller APK).
- Fixed debug keystore so every build is signed with the same key (updates install over each other).

**Acceptance (per phone):** install the APK from Releases/Actions; live PTT voice still works between two phones.

## M1 — TCP layer + text messages
**Goal:** reliable connection between two phones carrying framed messages.
- One phone listens (TCP server), the other connects; the same code path serves both directions.
- Framed protocol: length-prefixed frames with a type byte (`HELLO`, `TEXT`, `ACK`, `PING`), documented in `docs/PROTOCOL.md`.
- JVM unit tests for frame encode/decode run in CI.

**Acceptance:** phone A sends a text, phone B receives it and A sees it marked as delivered; reconnect after Wi-Fi toggle.

## M2 — Chat UI + history
**Goal:** a real conversation screen.
- Message list, input box, delivery state, connection indicator.
- Room database for history; survives app restart.
- Live PTT stays on the main screen / a button in the chat.

**Acceptance:** exchange 20 messages, restart the app on both phones, history is intact.

## M3 — Files
**Goal:** send any file over TCP.
- Chunked streaming (no whole-file in memory), progress bar, cancel, size limit configurable.
- Storage Access Framework picker; received files saved to Downloads/LanTalk.
- Optional SHA-256 check at the end.

**Acceptance:** send a 50 MB video; progress is shown on both phones; checksums match; cancel mid-way works.

## M4 — Recorded voice messages
**Goal:** hold-to-record voice notes delivered as files.
- Record (AAC/Opus in M4A/OGG), send through the file path, play inline in chat with a seek bar.

**Acceptance:** record 30 s on A, B plays it back clearly.

## M5 — Reliability and security
**Goal:** survive real-world networks.
- Auto-reconnect with backoff, keep-alive, resume interrupted transfers.
- Pairing code (6 digits) so only the intended peer connects.
- Optional AES-GCM encryption derived from the pairing code.

**Acceptance:** toggle Wi-Fi mid-transfer — it resumes; a device without the code cannot connect.

## M6 — Polish and v1.0.0
- Notifications for incoming messages, Arabic/English UI, app icon, onboarding screen, battery-optimisation guidance.
- Final regression pass of the full checklist in TESTING.md.

## Out of scope (deferred)
- Internet / WhatsApp-like version (Firebase / WebRTC) — postponed by the project owner.
- Wi-Fi Direct — explained, not approved.
