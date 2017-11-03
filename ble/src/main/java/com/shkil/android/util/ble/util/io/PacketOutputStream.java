package com.shkil.android.util.ble.util.io;

import java.io.ByteArrayOutputStream;

public class PacketOutputStream extends ByteArrayOutputStream {

    public PacketOutputStream() {
        super();
    }

    public PacketOutputStream(int size) {
        super(size);
    }

    public void writeUnsignedByte(int value) {
        write(value);
    }

    public void writeUnsignedIntLe(long value) {
        write((int) ((value >>> 0) & 0xFF));
        write((int) ((value >>> 8) & 0xFF));
        write((int) ((value >>> 16) & 0xFF));
        write((int) ((value >>> 24) & 0xFF));
    }

    public void writeSignedIntLe(int value) {
        write((value >>> 0) & 0xFF);
        write((value >>> 8) & 0xFF);
        write((value >>> 16) & 0xFF);
        write((value >>> 24) & 0xFF);
    }

    public void writeCharLe(char value) {
        write((value >>> 0) & 0xFF);
        write((value >>> 8) & 0xFF);
    }

    /**
     * Writes little-endian signed 2 bytes short
     */
    public void writeShortLe(short value) {
        write((value >>> 0) & 0xFF);
        write((value >>> 8) & 0xFF);
    }

}
