package com.droiddungeon.net.codec;

import com.droiddungeon.net.BinaryProtocol;
import com.droiddungeon.net.dto.ClientInputDto;
import com.droiddungeon.net.dto.WelcomeDto;
import com.droiddungeon.net.dto.WorldSnapshotDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.io.IOException;
import java.nio.ByteBuffer;

public final class CborProtocolCodec implements ProtocolCodec {
  private final ObjectMapper mapper;

  public CborProtocolCodec(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public static CborProtocolCodec createDefault() {
    return new CborProtocolCodec(new ObjectMapper(new CBORFactory()).findAndRegisterModules());
  }

  @Override
  public DecodedMessage decode(ByteBuffer buffer) throws IOException {
    BinaryProtocol.Header header = BinaryProtocol.readHeader(buffer);
    if (header.version() != BinaryProtocol.VERSION_1) {
      throw new IllegalArgumentException("Unsupported protocol version: " + header.version());
    }
    byte[] payload = new byte[buffer.remaining()];
    buffer.get(payload);
    return switch (header.type()) {
      case BinaryProtocol.TYPE_WELCOME ->
          new WelcomeMessage(mapper.readValue(payload, WelcomeDto.class));
      case BinaryProtocol.TYPE_SNAPSHOT ->
          new SnapshotMessage(mapper.readValue(payload, WorldSnapshotDto.class));
      case BinaryProtocol.TYPE_INPUT ->
          new InputMessage(mapper.readValue(payload, ClientInputDto.class));
      default -> throw new IllegalArgumentException("Unexpected message type: " + header.type());
    };
  }

  @Override
  public byte[] encodeWelcome(WelcomeDto dto) throws IOException {
    byte[] payload = mapper.writeValueAsBytes(dto);
    return BinaryProtocol.wrap(BinaryProtocol.TYPE_WELCOME, payload);
  }

  @Override
  public byte[] encodeSnapshot(WorldSnapshotDto dto) throws IOException {
    byte[] payload = mapper.writeValueAsBytes(dto);
    return BinaryProtocol.wrap(BinaryProtocol.TYPE_SNAPSHOT, payload);
  }

  @Override
  public byte[] encodeInput(ClientInputDto dto) throws IOException {
    byte[] payload = mapper.writeValueAsBytes(dto);
    return BinaryProtocol.wrap(BinaryProtocol.TYPE_INPUT, payload);
  }
}
