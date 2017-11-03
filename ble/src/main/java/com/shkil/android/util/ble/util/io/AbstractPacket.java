package com.shkil.android.util.ble.util.io;

import java.io.IOException;

public abstract class AbstractPacket implements IPacket {

    private volatile byte[] packetBytes;

    public final byte[] getPacketBytes() {
        if (packetBytes == null) {
            synchronized (this) {
                if (packetBytes == null) {
                    packetBytes = assemblePacketBytes();
                }
            }
        }
        return packetBytes.clone();
    }

    private byte[] assemblePacketBytes() {
        PacketOutputStream output = new PacketOutputStream();
        try {
            writeHeaderTo(output);
            writeBodyTo(output);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return output.toByteArray();
    }

    protected void writeHeaderTo(PacketOutputStream out) throws IOException {
    }

    protected abstract void writeBodyTo(PacketOutputStream out) throws IOException;

}
