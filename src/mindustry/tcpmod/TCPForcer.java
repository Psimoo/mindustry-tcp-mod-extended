package mindustry.tcpmod;

import arc.util.Log;
import mindustry.Vars;
import mindustry.net.ArcNetProvider;
import mindustry.net.Net;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicLong;

public class TCPForcer {
    private static boolean installed = false;
    private static boolean forced = false;
    private static boolean forceServer = false;
    private static Object originalProvider = null;
    private static Object client = null;
    private static Method sendTCP = null;
    private static Method getConnections = null;
    private static Field arcConnectionField = null;

    private static final AtomicLong routedPackets = new AtomicLong();
    private static final AtomicLong fallbackPackets = new AtomicLong();
    private static final AtomicLong serverRoutedPackets = new AtomicLong();

    public static void install() {
        if (installed) return;

        try {
            Field providerField = Net.class.getDeclaredField("provider");
            providerField.setAccessible(true);
            Object currentProvider = providerField.get(Vars.net);

            if (!(currentProvider instanceof ArcNetProvider arcProvider)) {
                Log.warn("TCP Mod: Not an ArcNetProvider");
                return;
            }

            originalProvider = currentProvider;

            Field clientField = ArcNetProvider.class.getDeclaredField("client");
            clientField.setAccessible(true);
            client = clientField.get(arcProvider);

            if (client == null) {
                Log.warn("TCP Mod: Client is null");
                return;
            }

            Field serverField = ArcNetProvider.class.getDeclaredField("server");
            serverField.setAccessible(true);
            Object server = serverField.get(arcProvider);

            sendTCP = client.getClass().getMethod("sendTCP", Object.class);
            getConnections = currentProvider.getClass().getMethod("getConnections");
            arcConnectionField = Class.forName("mindustry.net.ArcNetProvider$ArcConnection")
                .getField("connection");

            NetMonitor.attach(client, server);

            Class<?> providerInterface = Class.forName("mindustry.net.Net$NetProvider");
            Object proxy = Proxy.newProxyInstance(
                providerInterface.getClassLoader(),
                new Class<?>[]{ providerInterface },
                TCPForcer::handle
            );

            providerField.set(Vars.net, proxy);
            installed = true;
            Log.info("TCP Mod: network hooks installed");
        } catch (Throwable e) {
            Log.err("TCP Mod: Failed to install network hooks", e);
        }
    }

    private static Object handle(Object proxyObj, Method method, Object[] args) throws Throwable {
        String name = method.getName();

        if ("sendClient".equals(name) && args != null && args.length == 2) {
            boolean reliable = (boolean) args[1];

            if (forced) {
                try {
                    sendTCP.invoke(client, args[0]);
                    long n = routedPackets.incrementAndGet();
                    if (n == 1) {
                        Log.info("TCP Mod: first client packet rerouted over TCP ("
                            + args[0].getClass().getSimpleName() + ")");
                    }
                    NetMonitor.recordTxClient(args[0], reliable, true);
                    return null;
                } catch (Throwable e) {
                    fallbackPackets.incrementAndGet();
                    NetMonitor.recordTxClient(args[0], reliable, reliable);
                    Log.err("TCP Mod: TCP send failed, falling back", e);
                    return method.invoke(originalProvider, args);
                }
            }

            NetMonitor.recordTxClient(args[0], reliable, reliable);
            return method.invoke(originalProvider, args);
        }

        if ("sendAllServer".equals(name) && args != null) {
            if (args.length == 2) {
                return forwardServer(method, args, args[0], (boolean) args[1], null, null);
            } else if (args.length == 3) {
                return forwardServer(method, args, args[0], (boolean) args[2], (Iterable<?>) args[1], null);
            }
        }

        if ("sendExceptServer".equals(name) && args != null && args.length == 3) {
            return forwardServer(method, args, args[1], (boolean) args[2], null, args[0]);
        }

        return method.invoke(originalProvider, args);
    }

    private static Object forwardServer(Method method, Object[] args, Object packet, boolean reliable,
                                        Iterable<?> connections, Object except) throws Throwable {
        if (forced && forceServer && isSnapshot(packet)) {
            int sent = 0;
            try {
                Iterable<?> targets = connections != null
                    ? connections
                    : (Iterable<?>) getConnections.invoke(originalProvider);
                sent = sendTo(targets, packet, except);
            } catch (Throwable e) {
                Log.err("TCP Mod: server TCP send failed", e);
            }

            if (sent > 0) {
                long n = serverRoutedPackets.incrementAndGet();
                if (n == 1) {
                    Log.info("TCP Mod: first snapshot rerouted over TCP ("
                        + packet.getClass().getSimpleName() + ", " + sent + " client(s))");
                }
                NetMonitor.recordTxServer(packet, true);
                return null;
            }
        }

        NetMonitor.recordTxServer(packet, reliable);
        return method.invoke(originalProvider, args);
    }

    private static boolean isSnapshot(Object packet) {
        if (packet == null) return false;
        String name = packet.getClass().getSimpleName();
        return "EntitySnapshotCallPacket".equals(name)
            || "StateSnapshotCallPacket".equals(name)
            || "BlockSnapshotCallPacket".equals(name)
            || "HiddenSnapshotCallPacket".equals(name);
    }

    private static int sendTo(Iterable<?> connections, Object packet, Object except) {
        if (connections == null) return 0;

        int sent = 0;
        for (Object connection : connections) {
            if (connection == null || connection == except) continue;

            try {
                Object arcConnection = arcConnectionField.get(connection);
                if (arcConnection instanceof arc.net.Connection arc && arc.isConnected()
                    && arc.getTcpWriteBufferSize() < 30000) {

                    sendTCP.invoke(arc, packet);
                    sent++;
                }
            } catch (Throwable ignored) {
            }
        }
        return sent;
    }

    public static void enable() {
        install();
        if (!installed) return;

        forced = true;
        routedPackets.set(0);
        fallbackPackets.set(0);
        serverRoutedPackets.set(0);
        Log.info("TCP Mod: TCP forcing enabled (client packets only)");
    }

    public static void disable() {
        if (!installed) return;

        forced = false;
        Log.info("TCP Mod: TCP forcing disabled (monitor stays active)");
    }

    public static void setForceServer(boolean value) {
        forceServer = value;
        Log.info("TCP Mod: server-to-client TCP forcing " + (value ? "enabled" : "disabled"));
    }

    public static boolean isForceServer() {
        return forceServer;
    }

    public static boolean isActive() {
        return installed && forced;
    }

    public static boolean isInstalled() {
        return installed;
    }

    public static long routedPackets() {
        return routedPackets.get();
    }

    public static long fallbackPackets() {
        return fallbackPackets.get();
    }

    public static long serverRoutedPackets() {
        return serverRoutedPackets.get();
    }

    public static boolean isClientRole() {
        return Vars.net != null && Vars.net.client();
    }

    public static boolean isHost() {
        return Vars.net != null && Vars.net.server();
    }
}
