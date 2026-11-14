# Mindustry TCP Mod Extended

[English](#english) | [中文](#中文)

Fork: <https://github.com/Psimoo/mindustry-tcp-mod-extended>

## English

Mindustry TCP Mod Extended is a fork of [Regator48/mindustry-tcp-mod](https://github.com/Regator48/mindustry-tcp-mod). It forces client packets over TCP, adds an in-game network monitor, and includes an experimental host-side snapshot TCP mode.

### Features

- Force client to server packets over TCP instead of UDP.
- In-game network monitor: TX/RX packet and byte rates, per-packet-type table, TCP/UDP share, and entity snapshot age.
- Host snapshots to TCP (experimental): send entity, state, block and hidden snapshots to clients over TCP to avoid the 20 second snapshot timeout.
- Settings persist across restarts.
- Hidden from the server compatibility check, so it works on vanilla servers.
- The monitor stays active while TCP mode is off, making on/off comparisons easy.

### Installation

1. Download `mindustry-tcp-mod-extended.jar` from [Releases](https://github.com/Psimoo/mindustry-tcp-mod-extended/releases).
2. Copy it into your Mindustry mods folder:
   - Windows: `%appdata%\Mindustry\mods\`
   - Linux: `~/.local/share/Mindustry/mods/`
   - macOS: `~/Library/Application Support/Mindustry/mods/`
3. Restart Mindustry.

### Usage

- Open **TCP Mode** from the main menu.
- **ON/OFF** toggles client to server TCP forcing.
- **Host snapshots to TCP** toggles the experimental host-side snapshot mode.
- **Network Monitor** shows live traffic and the per-packet breakdown. Use **Reset** to start a new sample and **Copy** to copy a text report.

### Building

Requires Java 17.

```bash
./gradlew jar
```

The output is `build/libs/mindustry-tcp-mod-extended.jar`.

### Notes

- TCP forcing is client-side. The host snapshot option only affects clients connected to a host that runs this mod.
- Only snapshots are routed over TCP on the host side, not every server broadcast. If a client's TCP buffer is nearly full, the snapshot is dropped for that client instead of risking a disconnect.
- TCP has head-of-line blocking, so forcing traffic over TCP can increase latency under congestion.
- Packet sizes in the monitor are payload estimates, not raw socket counters.
- Streaming transfers such as world and mod assets are not included in the per-packet table.

### License

MIT. See [LICENSE](LICENSE).

### Credits

Original mod by Regator48. Extended fork maintained by Psimoo.

## 中文

Mindustry TCP Mod Extended 是 [Regator48/mindustry-tcp-mod](https://github.com/Regator48/mindustry-tcp-mod) 的扩展分支。它强制客户端数据包走 TCP，内置网络监控器，并提供一个实验性的主机快照 TCP 模式。

### 功能

- 强制客户端发给服务器的数据包走 TCP，而不是 UDP。
- 游戏内网络监控器：收发速率、按包类型统计、TCP/UDP 占比、实体快照间隔。
- 主机快照转 TCP（实验性）：把实体、状态、方块和隐藏快照通过 TCP 发给客户端，避免 20 秒快照超时。
- 设置跨重启保存。
- 对服务器隐藏，可在原版服务器使用。
- TCP 模式关闭时监控仍然工作，方便对比开关前后的流量。

### 安装

1. 从 [Releases](https://github.com/Psimoo/mindustry-tcp-mod-extended/releases) 下载 `mindustry-tcp-mod-extended.jar`。
2. 复制到 Mindustry 的 mods 目录：
   - Windows：`%appdata%\Mindustry\mods\`
   - Linux：`~/.local/share/Mindustry/mods/`
   - macOS：`~/Library/Application Support/Mindustry/mods/`
3. 重启 Mindustry。

### 使用

- 主菜单打开 **TCP Mode**。
- **ON/OFF** 开关客户端到服务器的 TCP 强制。
- **Host snapshots to TCP** 开关实验性的主机快照 TCP 模式。
- **Network Monitor** 打开实时流量和按包类型的明细。**Reset** 重新开始采样，**Copy** 复制文本报告。

### 构建

需要 Java 17。

```bash
./gradlew jar
```

产物位于 `build/libs/mindustry-tcp-mod-extended.jar`。

### 说明

- TCP 强制作用于客户端。主机快照选项只影响连接到“运行了本 mod 的主机”的客户端。
- 主机侧只把快照改走 TCP，而不是所有服务器广播。如果某个客户端的 TCP 写缓冲接近上限，该快照会被丢弃，以避免断连。
- TCP 存在队头阻塞，拥塞时可能增加延迟。
- 监控器中的字节数是负载估算值，不是网卡原始计数。
- 世界数据和模组资源等流式传输不计入按包类型统计。

### 许可证

MIT，详见 [LICENSE](LICENSE)。

### 致谢

原 mod 作者 Regator48；扩展分支由 Psimoo 维护。
