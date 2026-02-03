package com.droiddungeon.net.codec;

import com.droiddungeon.net.BinaryProtocol;
import com.droiddungeon.net.dto.ClientInputDto;
import com.droiddungeon.net.dto.WelcomeDto;
import com.droiddungeon.net.dto.WorldSnapshotDto;
import java.io.IOException;
import java.nio.ByteBuffer;

public interface ProtocolCodec {
  DecodedMessage decode(ByteBuffer buffer) throws IOException;

  byte[] encodeWelcome(WelcomeDto dto) throws IOException;

  byte[] encodeSnapshot(WorldSnapshotDto dto) throws IOException;

  byte[] encodeInput(ClientInputDto dto) throws IOException;

  sealed interface DecodedMessage permits WelcomeMessage, SnapshotMessage, InputMessage {
    byte type();
  }

  record WelcomeMessage(WelcomeDto value) implements DecodedMessage {
    @Override
    public byte type() {
      return BinaryProtocol.TYPE_WELCOME;
    }
  }

  record SnapshotMessage(WorldSnapshotDto value) implements DecodedMessage {
    @Override
    public byte type() {
      return BinaryProtocol.TYPE_SNAPSHOT;
    }
  }

  record InputMessage(ClientInputDto value) implements DecodedMessage {
    @Override
    public byte type() {
      return BinaryProtocol.TYPE_INPUT;
    }
  }
}
