该mod由ai在[Mindustry-tcp-mod](https://github.com/Regator48/mindustry-tcp-mod)的基础上修改而来，部分实现性功能可能有bug

[English readme](README.md)

# Mindustry TCP Mod Extended

Fork: <https://github.com/Psimoo/mindustry-tcp-mod-extended>

Mindustry TCP Mod Extended 是 [Mindustry-tcp-mod](https://github.com/Regator48/mindustry-tcp-mod) 的扩展分支。它强制客户端数据包走 TCP，内置网络监控器，并提供一个实验性的主机快照 TCP 模式。

## 功能

- 强制客户端发给服务器的数据包走 TCP，而不是 UDP。
- 游戏内网络监控器：收发速率、按包类型统计、TCP/UDP 占比、实体快照间隔。
- 主机快照转 TCP（实验性）：把实体、状态、方块和隐藏快照通过 TCP 发给客户端，避免 20 秒快照超时。
- 设置跨重启保存。
- 对服务器隐藏，可在原版服务器使用。
- TCP 模式关闭时监控仍然工作，方便对比开关前后的流量。

## 安装

1. 从 [Releases](https://github.com/Psimoo/mindustry-tcp-mod-extended/releases) 下载 `mindustry-tcp-mod-extended.jar`。
2. 复制到 Mindustry 的 mods 目录：
   - Windows：`%appdata%\Mindustry\mods\`
   - Linux：`~/.local/share/Mindustry/mods/`
   - macOS：`~/Library/Application Support/Mindustry/mods/`
3. 重启 Mindustry。

## 使用

- 主菜单打开 **TCP Mode**。
- **ON/OFF** 开关客户端到服务器的 TCP 强制。
- **Host snapshots to TCP** 开关实验性的主机快照 TCP 模式。
- **Network Monitor** 打开实时流量和按包类型的明细。**Reset** 重新开始采样，**Copy** 复制文本报告。

## 构建

需要 Java 17。

```bash
./gradlew jar
```

产物位于 `build/libs/mindustry-tcp-mod-extended.jar`。

## 说明

- TCP 强制作用于客户端。主机快照选项只影响连接到“运行了本 mod 的主机”的客户端。
- 主机侧只把快照改走 TCP，而不是所有服务器广播。如果某个客户端的 TCP 写缓冲接近上限，该快照会被丢弃，以避免断连。
- TCP 存在队头阻塞，拥塞时可能增加延迟。
- 监控器中的字节数是负载估算值，不是网卡原始计数。
- 世界数据和模组资源等流式传输不计入按包类型统计。

## 许可证

MIT，详见 [LICENSE](LICENSE)。

## 致谢

原 mod 作者 Regator48；扩展分支由 Psimoo 维护。
