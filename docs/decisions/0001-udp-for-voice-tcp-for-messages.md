# ADR 0001 — UDP for live voice, TCP for messages, files and voice notes

**Status:** accepted

## Context
Live push-to-talk needs low latency; a late audio packet is useless. Messages and files must arrive
complete and in order.

## Decision
- Live PTT voice stays on **UDP** (20 ms PCM16 frames, jitter buffer).
- Text messages, files and recorded voice messages use **TCP**.

## Consequences
- TCP retransmission causes head-of-line blocking, which makes live voice stutter after packet loss — so TCP is not used for live voice.
- Two sockets/ports are needed (UDP voice port, TCP data port).
- Delivery guarantees for messages/files come from TCP; application-level ACKs confirm receipt by the app, not just the OS.
