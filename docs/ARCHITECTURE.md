# Architecture (current: v0.1.0)

```
MainActivity ── MainViewModel ── LanTalkieScreen (Compose)
      │ binds
AudioService (foreground service, microphone type)
   ├─ AudioCaptureEngine   mic → 20 ms PCM16 frames (AEC/NS)
   ├─ AudioPlaybackEngine  jitter buffer → speaker
   └─ UdpVoiceSocket       DatagramSocket send/receive loops
util/NetworkUtils          local IPv4, IP input normalisation, Wi-Fi Network lookup
```

## Voice packet
`[0..3]` sequence number (big-endian int) · `[4..643]` 640 bytes PCM16, 16 kHz mono = 644 bytes per 20 ms.

## Notes
- The foreground service owns audio and sockets so voice keeps working with the screen off.
- Received audio is routed to the speaker using MODE_IN_COMMUNICATION.
- `UdpVoiceSocket` binds the configured port explicitly (see the port-shadowing bug fixed in v0.1.0).
- Optional "Bind to Wi-Fi network" sends packets through Wi-Fi even if mobile data is the default network.
- Works only on a LAN/hotspot where devices can reach each other (no client isolation, no captive portal).

Planned additions are in [ROADMAP.md](ROADMAP.md).
