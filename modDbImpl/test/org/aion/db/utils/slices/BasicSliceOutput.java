/**
 * ***************************************************************************** Copyright (c)
 * 2017-2018 Aion foundation.
 *
 * <p>This file is part of the aion network project.
 *
 * <p>The aion network project is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version.
 *
 * <p>The aion network project is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with the aion network
 * project source files. If not, see <https://www.gnu.org/licenses/>.
 *
 * <p>The aion network project leverages useful source code from other open source projects. We
 * greatly appreciate the effort that was invested in these projects and we thank the individual
 * contributors for their work. For provenance information and contributors please see
 * <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * <p>Contributors to the aion source files in decreasing order of code volume: Aion foundation.
 * <ether.camp> team through the ethereumJ library. Ether.Camp Inc. (US) team through Ethereum
 * Harmony. John Tromp through the Equihash solver. Samuel Neves through the BLAKE2 implementation.
 * Zcash project team. Bitcoinj team. H2 Group.
 * ****************************************************************************
 */
package org.aion.db.utils.slices;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;

public class BasicSliceOutput extends SliceOutput {
    private final Slice slice;
    private int size;

    protected BasicSliceOutput(Slice slice) {
        this.slice = slice;
    }

    @Override
    public void reset() {
        size = 0;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isWritable() {
        return writableBytes() > 0;
    }

    @Override
    public int writableBytes() {
        return slice.length() - size;
    }

    @Override
    public void writeByte(int value) {
        slice.setByte(size++, value);
    }

    @Override
    public void writeShort(int value) {
        slice.setShort(size, value);
        size += 2;
    }

    @Override
    public void writeInt(int value) {
        slice.setInt(size, value);
        size += 4;
    }

    @Override
    public void writeLong(long value) {
        slice.setLong(size, value);
        size += 8;
    }

    @Override
    public void writeBytes(byte[] source, int sourceIndex, int length) {
        slice.setBytes(size, source, sourceIndex, length);
        size += length;
    }

    @Override
    public void writeBytes(byte[] source) {
        writeBytes(source, 0, source.length);
    }

    @Override
    public void writeBytes(Slice source) {
        writeBytes(source, 0, source.length());
    }

    @Override
    public void writeBytes(SliceInput source, int length) {
        if (length > source.available()) {
            throw new IndexOutOfBoundsException();
        }
        writeBytes(source.readBytes(length));
    }

    @Override
    public void writeBytes(Slice source, int sourceIndex, int length) {
        slice.setBytes(size, source, sourceIndex, length);
        size += length;
    }

    @Override
    public void writeBytes(ByteBuffer source) {
        int length = source.remaining();
        slice.setBytes(size, source);
        size += length;
    }

    @Override
    public int writeBytes(InputStream in, int length) throws IOException {
        int writtenBytes = slice.setBytes(size, in, length);
        if (writtenBytes > 0) {
            size += writtenBytes;
        }
        return writtenBytes;
    }

    @Override
    public int writeBytes(ScatteringByteChannel in, int length) throws IOException {
        int writtenBytes = slice.setBytes(size, in, length);
        if (writtenBytes > 0) {
            size += writtenBytes;
        }
        return writtenBytes;
    }

    @Override
    public int writeBytes(FileChannel in, int position, int length) throws IOException {
        int writtenBytes = slice.setBytes(size, in, position, length);
        if (writtenBytes > 0) {
            size += writtenBytes;
        }
        return writtenBytes;
    }

    @Override
    public void writeZero(int length) {
        if (length == 0) {
            return;
        }
        if (length < 0) {
            throw new IllegalArgumentException("length must be 0 or greater than 0.");
        }
        int nLong = length >>> 3;
        int nBytes = length & 7;
        for (int i = nLong; i > 0; i--) {
            writeLong(0);
        }
        if (nBytes == 4) {
            writeInt(0);
        } else if (nBytes < 4) {
            for (int i = nBytes; i > 0; i--) {
                writeByte((byte) 0);
            }
        } else {
            writeInt(0);
            for (int i = nBytes - 4; i > 0; i--) {
                writeByte((byte) 0);
            }
        }
    }

    @Override
    public Slice slice() {
        return slice.slice(0, size);
    }

    @Override
    public ByteBuffer toByteBuffer() {
        return slice.toByteBuffer(0, size);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + '('
                + "size="
                + size
                + ", "
                + "capacity="
                + slice.length()
                + ')';
    }

    public String toString(Charset charset) {
        return slice.toString(0, size, charset);
    }
}
