Modified from [Mindustry-tcp-mod](https://github.com/Regator48/mindustry-tcp-mod) by AI. Some experimental features may have bugs. | [中文readme](README.zh-CN.md)

# Mindustry TCP Mod Extended

Fork: <https://github.com/Psimoo/mindustry-tcp-mod-extended>

Mindustry TCP Mod Extended is a fork of [Mindustry-tcp-mod](https://github.com/Regator48/mindustry-tcp-mod). It forces client packets over TCP, adds an in-game network monitor, and includes an experimental host-side snapshot TCP mode.

## Features

- Force client to server packets over TCP instead of UDP.
- In-game network monitor: TX/RX packet and byte rates, per-packet-type table, TCP/UDP share, and entity snapshot age.
- Host snapshots to TCP (experimental): send entity, state, block and hidden snapshots to clients over TCP to avoid the 20 second snapshot timeout.
- Settings persist across restarts.
- Hidden from the server compatibility check, so it works on vanilla servers.
- The monitor stays active while TCP mode is off, making on/off comparisons easy.

## Installation

1. Download `mindustry-tcp-mod-extended.jar` from [Releases](https://github.com/Psimoo/mindustry-tcp-mod-extended/releases).
2. Copy it into your Mindustry mods folder:
   - Windows: `%appdata%\Mindustry\mods\`
   - Linux: `~/.local/share/Mindustry/mods/`
   - macOS: `~/Library/Application Support/Mindustry/mods/`
3. Restart Mindustry.

## Usage

- Open **TCP Mode** from the main menu.
- **ON/OFF** toggles client to server TCP forcing.
- **Host snapshots to TCP** toggles the experimental host-side snapshot mode.
- **Network Monitor** shows live traffic and the per-packet breakdown. Use **Reset** to start a new sample and **Copy** to copy a text report.

## Building

Requires Java 17.

```bash
./gradlew jar
```

The output is `build/libs/mindustry-tcp-mod-extended.jar`.

## Notes

- TCP forcing is client-side. The host snapshot option only affects clients connected to a host that runs this mod.
- Only snapshots are routed over TCP on the host side, not every server broadcast. If a client's TCP buffer is nearly full, the snapshot is dropped for that client instead of risking a disconnect.
- TCP has head-of-line blocking, so forcing traffic over TCP can increase latency under congestion.
- Packet sizes in the monitor are payload estimates, not raw socket counters.
- Streaming transfers such as world and mod assets are not included in the per-packet table.

## License

MIT. See [LICENSE](LICENSE).

## Credits

Original mod by Regator48. Extended fork maintained by Psimoo.
