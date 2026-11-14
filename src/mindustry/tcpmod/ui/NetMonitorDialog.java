package mindustry.tcpmod.ui;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import mindustry.tcpmod.NetMonitor;
import mindustry.tcpmod.TCPForcer;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import java.util.HashMap;
import java.util.Map;

public class NetMonitorDialog extends BaseDialog {
    private long lastRefresh;
    private long snapshotTime;
    private final Map<String, long[]> snapshot = new HashMap<>();

    private Label modeLabel;
    private Label rateLabel;
    private Label totalLabel;
    private Label snapshotLabel;
    private Table body;

    public NetMonitorDialog() {
        super("Network Monitor");
        addCloseButton();
        shown(this::build);
        resized(this::build);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (isShown() && body != null && Time.timeSinceMillis(lastRefresh) > 400) {
            lastRefresh = Time.millis();
            refresh();
        }
    }

    private void build() {
        cont.clear();
        cont.top().left();
        cont.defaults().left().pad(3);

        modeLabel = cont.add("").get();
        cont.row();
        rateLabel = cont.add("").get();
        cont.row();
        totalLabel = cont.add("").color(Color.lightGray).get();
        cont.row();
        snapshotLabel = cont.add("").color(Color.lightGray).get();
        cont.row();

        body = new Table();
        body.background(Styles.black6);
        body.defaults().left().pad(2);
        cont.add(body).growX().padTop(10).row();

        cont.table(buttons -> {
            buttons.button("Reset", () -> {
                NetMonitor.reset();
                snapshot.clear();
                snapshotTime = 0;
                refresh();
            }).width(120).height(40);
            buttons.button("Copy", () -> Core.app.setClipboardText(NetMonitor.dump()))
                .width(120).height(40);
        }).padTop(8);

        snapshotTime = 0;
        refresh();
    }

    private void refresh() {
        if (body == null) return;

        NetMonitor.updateRates();

        String mode = TCPForcer.isActive() ? "[green]TCP forced (client packets)" : "[gray]passthrough";
        String role = TCPForcer.isClientRole() ? "client" : TCPForcer.isHost() ? "host" : "offline";
        modeLabel.setText("Mode: " + mode + "    Role: " + role
            + "    serverTCP: " + (TCPForcer.isForceServer() ? "on" : "off"));

        rateLabel.setText("TX  " + (int) NetMonitor.txPacketsPerSec() + " pkt/s  "
            + NetMonitor.fmt(NetMonitor.txBytesPerSec()) + "/s"
            + "    RX  " + (int) NetMonitor.rxPacketsPerSec() + " pkt/s  "
            + NetMonitor.fmt(NetMonitor.rxBytesPerSec()) + "/s");

        long txTotal = NetMonitor.tcpTxBytes() + NetMonitor.udpTxBytes();
        float tcpShare = txTotal == 0 ? 0f : NetMonitor.tcpTxBytes() * 100f / txTotal;
        totalLabel.setText("TX total " + NetMonitor.fmt(NetMonitor.txBytes())
            + "    TCP " + String.format("%.0f%%", tcpShare)
            + " / UDP " + String.format("%.0f%%", 100f - tcpShare));

        long age = NetMonitor.entitySnapshotAge();
        long maxGap = NetMonitor.maxEntitySnapshotGap();
        String snapshotText;
        if (age < 0) {
            snapshotText = "[gray]Entity snapshot: none received yet";
        } else if (age > 5000) {
            snapshotText = "[orange]Entity snapshot: " + String.format("%.1fs", age / 1000.0)
                + " ago (UDP loss?)";
        } else {
            snapshotText = "Entity snapshot: " + String.format("%.1fs", age / 1000.0) + " ago";
        }
        snapshotLabel.setText(snapshotText + "    max gap " + String.format("%.1fs", maxGap / 1000.0));

        body.clear();
        body.add("[gray]Dir").width(55);
        body.add("[gray]Packet").width(200);
        body.add("[gray]Packets").width(80);
        body.add("[gray]Size").width(80);
        body.add("[gray]Rate").width(85);
        body.row();

        long now = Time.millis();
        float dt = snapshotTime == 0 ? 0f : (now - snapshotTime) / 1000f;

        for (NetMonitor.Row row : NetMonitor.rows(15)) {
            String key = row.dir + "|" + row.packet;
            long[] prev = snapshot.get(key);
            float rate = (dt > 0f && prev != null) ? (row.bytes - prev[1]) / dt : 0f;

            body.add(row.dir).color(Color.lightGray);
            body.add(row.packet).color(Color.white);
            body.add(Long.toString(row.packets)).color(Color.lightGray);
            body.add(NetMonitor.fmt(row.bytes)).color(Color.lightGray);
            body.add(rate > 0f ? NetMonitor.fmt(rate) + "/s" : "-").color(Color.lightGray);
            body.row();
        }

        if (NetMonitor.statCount() == 0) {
            body.add("No traffic recorded yet").colspan(5).color(Color.gray).row();
        }

        snapshot.clear();
        for (NetMonitor.Row row : NetMonitor.rows(Integer.MAX_VALUE)) {
            snapshot.put(row.dir + "|" + row.packet, new long[]{row.packets, row.bytes});
        }
        snapshotTime = now;
    }
}
