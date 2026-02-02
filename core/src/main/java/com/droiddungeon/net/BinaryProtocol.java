package com.droiddungeon.net;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class BinaryProtocol {
    public static final int MAGIC = 0x44444E31; // "DDN1"
    public static final byte VERSION_1 = 1;
    public static final byte TYPE_WELCOME = 1;
    public static final byte TYPE_SNAPSHOT = 2;
    public static final byte TYPE_INPUT = 3;
    public static final int HEADER_SIZE = 6;

    private BinaryProtocol() {}

    public static byte[] wrap(byte type, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE + payload.length).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(MAGIC);
        buffer.put(VERSION_1);
        buffer.put(type);
        buffer.put(payload);
        return buffer.array();
    }

    public static Header readHeader(ByteBuffer buffer) {
        buffer.order(ByteOrder.BIG_ENDIAN);
        if (buffer.remaining() < HEADER_SIZE) {
            throw new IllegalArgumentException("Binary payload too small for header");
        }
        int magic = buffer.getInt();
        if (magic != MAGIC) {
            throw new IllegalArgumentException("Invalid magic header");
        }
        byte version = buffer.get();
        byte type = buffer.get();
        return new Header(version, type);
    }

    public record Header(byte version, byte type) {}
}
