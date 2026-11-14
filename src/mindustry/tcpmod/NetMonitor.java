package mindustry.tcpmod;

import arc.util.Log;
import arc.util.Time;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public final class NetMonitor {
    public static final int TX_CLIENT = 0;
    public static final int TX_SERVER = 1;
    public static final int RX_CLIENT = 2;
    public static final int RX_SERVER = 3;

    public static final String[] DIR_LABELS = {"TX", "TX-srv", "RX", "RX-srv"};

    private static final int SAMPLE_MASK = 3;

    private static final ConcurrentHashMap<String, Stat> stats = new ConcurrentHashMap<>();
    private static final LongAdder[] dirPackets = new LongAdder[4];
    private static final LongAdder[] dirBytes = new LongAdder[4];

    private static final LongAdder tcpTxPackets = new LongAdder();
    private static final LongAdder tcpTxBytes = new LongAdder();
    private static final LongAdder udpTxPackets = new LongAdder();
    private static final LongAdder udpTxBytes = new LongAdder();

    private static Object serializer;
    private static Method writeMethod;
    private static final ByteBuffer buffer = ByteBuffer.allocateDirect(1 << 20);
    private static final Object bufferLock = new Object();
    private static volatile long startTime;

    private static final ClassValue<Field> cachedDataField = new ClassValue<>() {
        @Override
        protected Field computeValue(Class<?> type) {
            try {
                Field field = type.getDeclaredField("DATA");
                field.setAccessible(true);
                return field;
            } catch (Throwable ignored) {
                return null;
            }
        }
    };

    private static long lastRateTime;
    private static long lastTxBytes;
    private static long lastRxBytes;
    private static long lastTxPackets;
    private static long lastRxPackets;
    private static float txBytesRate;
    private static float rxBytesRate;
    private static float txPacketsRate;
    private static float rxPacketsRate;

    private static volatile long lastEntitySnapshotTime;
    private static volatile long maxEntitySnapshotGap;

    static {
        for (int i = 0; i < 4; i++) {
            dirPackets[i] = new LongAdder();
            dirBytes[i] = new LongAdder();
        }
    }

    private NetMonitor() {
    }

    public static final class Stat {
        public final LongAdder packets = new LongAdder();
        public final LongAdder bytes = new LongAdder();
        private final LongAdder samples = new LongAdder();
        private final LongAdder sampleBytes = new LongAdder();
        private final AtomicLong seq = new AtomicLong();
        private volatile double averageSize;

        long account(Object packet) {
            packets.increment();
            long size = -1;
            if ((seq.getAndIncrement() & SAMPLE_MASK) == 0) {
                size = measure(packet);
                if (size > 0) {
                    samples.increment();
                    sampleBytes.add(size);
                    averageSize = sampleBytes.sum() / (double) samples.sum();
                }
            }
            long add = size > 0 ? size : (long) averageSize;
            bytes.add(add);
            return add;
        }
    }

    public static final class Row {
        public final String dir;
        public final String packet;
        public final long packets;
        public final long bytes;

        Row(String dir, String packet, long packets, long bytes) {
            this.dir = dir;
            this.packet = packet;
            this.packets = packets;
            this.bytes = bytes;
        }
    }

    public static void attach(Object client, Object server) {
        if (client == null) return;

        try {
            serializer = client.getClass().getMethod("getSerialization").invoke(client);
            if (serializer != null) {
                writeMethod = serializer.getClass().getMethod("write", ByteBuffer.class, Object.class);
            }
        } catch (Throwable t) {
            Log.warn("TCP Mod: monitor could not access packet serializer, sizes will read 0", t);
        }

        try {
            Class<?> listenerClass = Class.forName("arc.net.NetListener");
            installListener(client, listenerClass, true);
            if (server != null) installListener(server, listenerClass, false);
            Log.info("TCP Mod: network monitor attached");
        } catch (Throwable t) {
            Log.warn("TCP Mod: monitor could not attach listeners", t);
        }
    }

    private static void installListener(Object target, Class<?> listenerClass, boolean clientSide) throws Exception {
        Object listener = Proxy.newProxyInstance(
            listenerClass.getClassLoader(),
            new Class<?>[]{listenerClass},
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    switch (method.getName()) {
                        case "equals":
                            return proxy == args[0];
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "toString":
                            return "TCPModNetMonitor";
                    }
                }
                if ("received".equals(method.getName()) && args != null && args.length >= 2 && args[1] != null) {
                    record(clientSide ? RX_CLIENT : RX_SERVER, args[1], false);
                }
                return null;
            }
        );
        Method add = target.getClass().getMethod("addListener", listenerClass);
        add.invoke(target, listener);
    }

    public static void recordTxClient(Object packet, boolean reliable, boolean tcp) {
        record(TX_CLIENT, packet, tcp);
    }

    public static void recordTxServer(Object packet, boolean reliable) {
        record(TX_SERVER, packet, reliable);
    }

    private static void record(int dir, Object packet, boolean tcp) {
        if (packet == null) return;
        if (startTime == 0) startTime = Time.millis();

        String name = packet.getClass().getSimpleName();
        if (name.isEmpty()) name = packet.getClass().getName();
        String key = dir + "|" + name;

        if (dir == RX_CLIENT && "EntitySnapshotCallPacket".equals(name)) {
            long now = Time.millis();
            if (lastEntitySnapshotTime != 0) {
                maxEntitySnapshotGap = Math.max(maxEntitySnapshotGap, now - lastEntitySnapshotTime);
            }
            lastEntitySnapshotTime = now;
        }

        Stat stat = stats.computeIfAbsent(key, k -> new Stat());
        long bytes = stat.account(packet);

        dirPackets[dir].increment();
        dirBytes[dir].add(bytes);

        if (dir == TX_CLIENT || dir == TX_SERVER) {
            if (tcp) {
                tcpTxPackets.increment();
                tcpTxBytes.add(bytes);
            } else {
                udpTxPackets.increment();
                udpTxBytes.add(bytes);
            }
        }
    }

    private static long measure(Object packet) {

        Field dataField = cachedDataField.get(packet.getClass());
        if (dataField != null) {
            try {
                Object value = dataField.get(packet);
                if (value instanceof byte[] bytes) {

                    return bytes.length + 4L;
                }
            } catch (Throwable ignored) {
            }
        }

        if (writeMethod == null || serializer == null) return 0;
        synchronized (bufferLock) {
            try {
                buffer.clear();
                writeMethod.invoke(serializer, buffer, packet);

                return buffer.position() + 2L;
            } catch (Throwable t) {
                return 0;
            }
        }
    }

    public static void updateRates() {
        long now = Time.millis();
        long txBytes = txBytes();
        long rxBytes = rxBytes();
        long txPackets = txPackets();
        long rxPackets = rxPackets();

        long dt = now - lastRateTime;
        if (dt >= 500) {
            txBytesRate = (txBytes - lastTxBytes) * 1000f / dt;
            rxBytesRate = (rxBytes - lastRxBytes) * 1000f / dt;
            txPacketsRate = (txPackets - lastTxPackets) * 1000f / dt;
            rxPacketsRate = (rxPackets - lastRxPackets) * 1000f / dt;
            lastTxBytes = txBytes;
            lastRxBytes = rxBytes;
            lastTxPackets = txPackets;
            lastRxPackets = rxPackets;
            lastRateTime = now;
        }
    }

    public static float txBytesPerSec() {
        return txBytesRate;
    }

    public static float rxBytesPerSec() {
        return rxBytesRate;
    }

    public static float txPacketsPerSec() {
        return txPacketsRate;
    }

    public static float rxPacketsPerSec() {
        return rxPacketsRate;
    }

    public static long txPackets() {
        return dirPackets[TX_CLIENT].sum() + dirPackets[TX_SERVER].sum();
    }

    public static long rxPackets() {
        return dirPackets[RX_CLIENT].sum() + dirPackets[RX_SERVER].sum();
    }

    public static long txBytes() {
        return dirBytes[TX_CLIENT].sum() + dirBytes[TX_SERVER].sum();
    }

    public static long rxBytes() {
        return dirBytes[RX_CLIENT].sum() + dirBytes[RX_SERVER].sum();
    }

    public static long tcpTxBytes() {
        return tcpTxBytes.sum();
    }

    public static long udpTxBytes() {
        return udpTxBytes.sum();
    }

    public static long tcpTxPackets() {
        return tcpTxPackets.sum();
    }

    public static long udpTxPackets() {
        return udpTxPackets.sum();
    }

    public static int statCount() {
        return stats.size();
    }

    public static List<Row> rows(int limit) {
        List<Row> list = new ArrayList<>(stats.size());
        for (Map.Entry<String, Stat> entry : stats.entrySet()) {
            String key = entry.getKey();
            int split = key.indexOf('|');
            Stat stat = entry.getValue();
            list.add(new Row(
                DIR_LABELS[Integer.parseInt(key.substring(0, split))],
                key.substring(split + 1),
                stat.packets.sum(),
                stat.bytes.sum()
            ));
        }
        list.sort((a, b) -> Long.compare(b.bytes, a.bytes));
        if (list.size() > limit) return new ArrayList<>(list.subList(0, limit));
        return list;
    }

    public static void reset() {
        stats.clear();
        for (int i = 0; i < 4; i++) {
            dirPackets[i].reset();
            dirBytes[i].reset();
        }
        tcpTxPackets.reset();
        tcpTxBytes.reset();
        udpTxPackets.reset();
        udpTxBytes.reset();
        startTime = 0;
        lastEntitySnapshotTime = 0;
        maxEntitySnapshotGap = 0;
        lastRateTime = 0;
        lastTxBytes = lastRxBytes = lastTxPackets = lastRxPackets = 0;
        txBytesRate = rxBytesRate = txPacketsRate = rxPacketsRate = 0f;
    }

    public static long sessionMillis() {
        return startTime == 0 ? 0 : Time.millis() - startTime;
    }

    public static long entitySnapshotAge() {
        return lastEntitySnapshotTime == 0 ? -1 : Time.millis() - lastEntitySnapshotTime;
    }

    public static long maxEntitySnapshotGap() {
        return maxEntitySnapshotGap;
    }

    public static String dump() {
        updateRates();
        long seconds = sessionMillis() / 1000L;

        StringBuilder sb = new StringBuilder();
        sb.append("TCP Mod network monitor\n");
        sb.append("mode: ").append(TCPForcer.isActive() ? "TCP forced (client)" : "passthrough").append('\n');
        sb.append("session: ").append(seconds).append("s since first packet\n");
        long age = entitySnapshotAge();
        sb.append("entity snapshot: ")
            .append(age < 0 ? "none received" : String.format("%.1fs ago", age / 1000.0))
            .append(", max gap ").append(String.format("%.1fs", maxEntitySnapshotGap() / 1000.0)).append('\n');
        sb.append("rate TX: ").append(fmt(txBytesPerSec())).append("/s, ").append((int) txPacketsPerSec()).append(" pkt/s");
        if (seconds > 0) sb.append("  (avg ").append(fmt(txBytes() / (double) seconds)).append("/s)");
        sb.append('\n');
        sb.append("rate RX: ").append(fmt(rxBytesPerSec())).append("/s, ").append((int) rxPacketsPerSec()).append(" pkt/s");
        if (seconds > 0) sb.append("  (avg ").append(fmt(rxBytes() / (double) seconds)).append("/s)");
        sb.append('\n');
        sb.append("total TX: ").append(fmt(txBytes())).append(", ").append(txPackets()).append(" pkt (TCP ")
            .append(fmt(tcpTxBytes())).append(" / UDP ").append(fmt(udpTxBytes())).append(")\n");
        sb.append("total RX: ").append(fmt(rxBytes())).append(", ").append(rxPackets()).append(" pkt\n");
        sb.append('\n');
        sb.append("dir\tpacket\tpackets\tbytes");
        if (seconds > 0) sb.append("\tavg/s");
        sb.append('\n');
        for (Row row : rows(40)) {
            sb.append(row.dir).append('\t').append(row.packet).append('\t')
                .append(row.packets).append('\t').append(fmt(row.bytes));
            if (seconds > 0) sb.append('\t').append(fmt(row.bytes / (double) seconds));
            sb.append('\n');
        }
        return sb.toString();
    }

    public static String fmt(double bytes) {
        if (bytes < 1024) return String.format("%.0f B", bytes);
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
