package com.shkil.android.util.ble.util.io;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;

public class PacketInputStream extends ByteArrayInputStream {

    public PacketInputStream(byte[] buf) {
        super(buf);
    }

    public PacketInputStream(byte[] buf, int offset, int length) {
        super(buf, offset, length);
    }

    public final int readUnsignedByte() throws IOException {
        int ch = read();
        if (ch < 0) {
            throw new EOFException();
        }
        return ch;
    }

    public final byte readByte() throws IOException {
        int ch = read();
        if (ch < 0) {
            throw new EOFException();
        }
        return (byte) ch;
    }

    /**
     * @return Big-endian unsigned 4 bytes int
     */
    public final long readUnsignedIntBe() throws IOException {
        long byte1 = read();
        long byte2 = read();
        long byte3 = read();
        long byte4 = read();
        if ((byte1 | byte2 | byte3 | byte4) < 0) {
            throw new EOFException();
        }
        return (byte1 << 24) + (byte2 << 16) + (byte3 << 8) + (byte4 << 0);
    }

    /**
     * @return Little-endian unsigned 4 bytes int
     */
    public final long readUnsignedIntLe() throws IOException {
        long byte1 = read();
        long byte2 = read();
        long byte3 = read();
        long byte4 = read();
        if ((byte1 | byte2 | byte3 | byte4) < 0) {
            throw new EOFException();
        }
        return (byte4 << 24) + (byte3 << 16) + (byte2 << 8) + (byte1 << 0);
    }

    /**
     * @return Little-endian unsigned 3 bytes int
     */
    public final long readUnsignedInt24Le() throws IOException {
        long byte1 = read();
        long byte2 = read();
        long byte3 = read();
        if ((byte1 | byte2 | byte3) < 0) {
            throw new EOFException();
        }
        return (byte3 << 16) + (byte2 << 8) + (byte1 << 0);
    }

    /**
     * @return Little-endian signed 4 bytes int
     */
    public final int readSignedIntLe() throws IOException {
        int byte1 = read();
        int byte2 = read();
        int byte3 = read();
        int byte4 = read();
        if ((byte1 | byte2 | byte3 | byte4) < 0) {
            throw new EOFException();
        }
        return (byte4 << 24) + (byte3 << 16) + (byte2 << 8) + (byte1 << 0);
    }

    /**
     * @return Little-endian unsigned 2 bytes char
     */
    public final char readCharLe() throws IOException {
        int byte1 = read();
        int byte2 = read();
        if ((byte1 | byte2) < 0) {
            throw new EOFException();
        }
        return (char) ((byte2 << 8) + (byte1 << 0));
    }

    /**
     * @return Little-endian signed 2 bytes short
     */
    public final short readShortLe() throws IOException {
        int byte1 = read();
        int byte2 = read();
        if ((byte1 | byte2) < 0) {
            throw new EOFException();
        }
        return (short)((byte2 << 8) + (byte1 << 0));
    }

    public final void readFully(byte b[]) throws IOException {
        readFully(b, 0, b.length);
    }

    public final byte[] readBytes(int count) throws IOException {
        byte[] bytes = new byte[count];
        readFully(bytes, 0, count);
        return bytes;
    }

    /**
     * See the general contract of the <code>readFully</code>
     * method of <code>DataInput</code>.
     */
    public final void readFully(byte b[], int off, int len) throws IOException {
        if (len < 0) {
            throw new IndexOutOfBoundsException();
        }
        int n = 0;
        while (n < len) {
            int count = read(b, off + n, len - n);
            if (count < 0) {
                throw new EOFException();
            }
            n += count;
        }
    }

    public final byte[] readAvailable(int maxLength, boolean throwIfEmpty) throws IOException {
        byte[] buff = new byte[maxLength];
        int read = read(buff);
        if (read == maxLength) {
            return buff;
        }
        if (read > 0) {
            return Arrays.copyOf(buff, read);
        }
        if (throwIfEmpty) {
            throw new IOException("Nothing read");
        }
        return new byte[0];
    }

}
