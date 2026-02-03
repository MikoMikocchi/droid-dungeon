package com.droiddungeon.desktop;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.droiddungeon.input.MovementIntent;
import com.droiddungeon.input.WeaponInput;
import com.droiddungeon.net.NetworkClientAdapter;
import com.droiddungeon.net.codec.CborProtocolCodec;
import com.droiddungeon.net.codec.ProtocolCodec;
import com.droiddungeon.net.dto.ClientInputDto;
import com.droiddungeon.net.dto.WelcomeDto;
import com.droiddungeon.net.dto.WorldSnapshotDto;
import com.droiddungeon.net.mapper.InputDtoMapper;
import com.droiddungeon.runtime.NetworkSnapshot;
import com.droiddungeon.runtime.NetworkSnapshotBuffer;

public final class NetworkClient extends WebSocketClient implements NetworkClientAdapter {
  private final ProtocolCodec codec = CborProtocolCodec.createDefault();
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
      ProtocolCodec.DecodedMessage decoded = codec.decode(bytes);
      if (decoded instanceof ProtocolCodec.WelcomeMessage(WelcomeDto welcome)) {
        if (welcome != null && welcome.playerId() != null && !welcome.playerId().isEmpty()) {
          playerId = welcome.playerId();
          PlayerIdStore.save(playerId);
        }
        return;
      }
      if (decoded instanceof ProtocolCodec.SnapshotMessage(WorldSnapshotDto snap)) {
        if (snap != null) {
          latestSnapshot.set(snap);
          if (playerId != null && snap.players() != null) {
            for (var p : snap.players()) {
              if (playerId.equals(p.playerId())) {
                buffer.push(
                    new NetworkSnapshot(
                        snap.tick(),
                        p.x(),
                        p.y(),
                        p.gridX(),
                        p.gridY(),
                        p.hp(),
                        p.lastProcessedTick()));
                return;
              }
            }
          }
          if (snap.player() != null) {
            buffer.push(
                new NetworkSnapshot(
                    snap.tick(),
                    snap.player().x(),
                    snap.player().y(),
                    snap.player().gridX(),
                    snap.player().gridY(),
                    snap.player().hp(),
                    snap.player().lastProcessedTick()));
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

  @Override
  public void onClose(int code, String reason, boolean remote) {
    connected = false;
  }

  @Override
  public void onError(Exception ex) {
    connected = false;
  }

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
  public boolean isConnected() {
    return connected;
  }

  @Override
  public void sendInput(
      long tick,
      MovementIntent movement,
      WeaponInput weapon,
      boolean drop,
      boolean pickUp,
      boolean mine,
      String playerId) {
    if (!connected) return;
    String pid = playerId != null ? playerId : this.playerId;
    if (pid == null) return;
    ClientInputDto dto =
        InputDtoMapper.toDto(tick, pid, movement, weapon, drop, pickUp, mine);
    tickCounter = Math.max(tickCounter, tick + 1);
    try {
      send(codec.encodeInput(dto));
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
    try {
      super.closeBlocking();
    } catch (InterruptedException ignored) {
    }
  }
}
