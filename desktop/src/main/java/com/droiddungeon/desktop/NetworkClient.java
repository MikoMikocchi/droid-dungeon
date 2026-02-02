package com.droiddungeon.desktop;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.droiddungeon.input.MovementIntent;
import com.droiddungeon.input.WeaponInput;
import com.droiddungeon.net.BinaryProtocol;
import com.droiddungeon.net.NetworkClientAdapter;
import com.droiddungeon.net.dto.ClientInputDto;
import com.droiddungeon.net.dto.MovementIntentDto;
import com.droiddungeon.net.dto.WeaponInputDto;
import com.droiddungeon.net.dto.WelcomeDto;
import com.droiddungeon.net.dto.WorldSnapshotDto;
import com.droiddungeon.runtime.NetworkSnapshot;
import com.droiddungeon.runtime.NetworkSnapshotBuffer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

public final class NetworkClient extends WebSocketClient implements NetworkClientAdapter {
    private final ObjectMapper cbor = new ObjectMapper(new CBORFactory()).findAndRegisterModules();
    private final NetworkSnapshotBuffer buffer;
    private final AtomicReference<WorldSnapshotDto> latestSnapshot = new AtomicReference<>();
    private volatile boolean connected = false;
    private long tickCounter = 0L;
    private boolean connectAttempted = false;
    private String playerId = null;

    public NetworkClient(URI serverUri, NetworkSnapshotBuffer buffer) {
        super(serverUri);
        this.buffer = buffer;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        connected = true;
    }

    @Override
    public void onMessage(ByteBuffer bytes) {
        try {
            bytes.order(ByteOrder.BIG_ENDIAN);
            BinaryProtocol.Header header = BinaryProtocol.readHeader(bytes);
            if (header.version() != BinaryProtocol.VERSION_1) return;

            byte[] payload = new byte[bytes.remaining()];
            bytes.get(payload);

            if (header.type() == BinaryProtocol.TYPE_WELCOME) {
                WelcomeDto welcome = cbor.readValue(payload, WelcomeDto.class);
                if (welcome != null && welcome.playerId() != null && !welcome.playerId().isEmpty()) {
                    playerId = welcome.playerId();
                }
                return;
            }
            if (header.type() == BinaryProtocol.TYPE_SNAPSHOT) {
                WorldSnapshotDto snap = cbor.readValue(payload, WorldSnapshotDto.class);
                if (snap != null) {
                    latestSnapshot.set(snap);
                    if (playerId != null && snap.players() != null) {
                        for (var p : snap.players()) {
                            if (playerId.equals(p.playerId())) {
                                buffer.push(new NetworkSnapshot(
                                        snap.tick(),
                                        p.x(),
                                        p.y(),
                                        p.gridX(),
                                        p.gridY(),
                                        p.hp(),
                                        p.lastProcessedTick()
                                ));
                                return;
                            }
                        }
                    }
                    if (snap.player() != null) {
                        buffer.push(new NetworkSnapshot(
                                snap.tick(),
                                snap.player().x(),
                                snap.player().y(),
                                snap.player().gridX(),
                                snap.player().gridY(),
                                snap.player().hp(),
                                snap.player().lastProcessedTick()
                        ));
                    }
                }
            }
        } catch (IllegalArgumentException | IOException ignored) {
        }
    }

    @Override
    public void onMessage(String message) {
        // JSON payloads are no longer supported
    }

    @Override public void onClose(int code, String reason, boolean remote) { connected = false; }
    @Override public void onError(Exception ex) { connected = false; }

    @Override
    public void connectIfNeeded() {
        if (connectAttempted) return;
        connectAttempted = true;
        try {
            super.connectBlocking(1, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public boolean isConnected() { return connected; }

    @Override
    public void sendInput(long tick, MovementIntent movement, WeaponInput weapon, boolean drop, boolean pickUp, boolean mine, String playerId) {
        if (!connected) return;
        String pid = playerId != null ? playerId : this.playerId;
        if (pid == null) return;
        MovementIntentDto m = new MovementIntentDto(
                movement.leftHeld(), movement.rightHeld(), movement.upHeld(), movement.downHeld(),
                movement.leftJustPressed(), movement.rightJustPressed(), movement.upJustPressed(), movement.downJustPressed()
        );
        WeaponInputDto w = new WeaponInputDto(weapon.attackJustPressed(), weapon.attackHeld(), weapon.aimWorldX(), weapon.aimWorldY());
        ClientInputDto dto = new ClientInputDto(tick, pid, m, w, drop, pickUp, mine);
        tickCounter = Math.max(tickCounter, tick + 1);
        try {
            byte[] payload = cbor.writeValueAsBytes(dto);
            byte[] framed = BinaryProtocol.wrap(BinaryProtocol.TYPE_INPUT, payload);
            send(framed);
        } catch (IOException ignored) {
        }
    }

    @Override
    public NetworkSnapshotBuffer buffer() {
        return buffer;
    }

    @Override
    public WorldSnapshotDto pollSnapshot() {
        return latestSnapshot.getAndSet(null);
    }

    @Override
    public String playerId() {
        return playerId;
    }

    @Override
    public void close() {
        try { super.closeBlocking(); } catch (InterruptedException ignored) {}
    }
}
