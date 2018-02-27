/*******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 *     H2 Group.
 ******************************************************************************/
package org.aion.db.utils.slices;

import com.google.common.base.Preconditions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.util.IdentityHashMap;
import java.util.Map;

public final class Slices
{
    public static Slice readLengthPrefixedBytes(SliceInput sliceInput)
    {
        int length = VariableLengthQuantity.readVariableLengthInt(sliceInput);
        return sliceInput.readBytes(length);
    }

    public static void writeLengthPrefixedBytes(SliceOutput sliceOutput, Slice value)
    {
        VariableLengthQuantity.writeVariableLengthInt(value.length(), sliceOutput);
        sliceOutput.writeBytes(value);
    }

    /**
     * A buffer whose capacity is {@code 0}.
     */
    public static final Slice EMPTY_SLICE = new Slice(0);

    private Slices()
    {
    }

    public static Slice ensureSize(Slice existingSlice, int minWritableBytes)
    {
        if (existingSlice == null) {
            existingSlice = EMPTY_SLICE;
        }

        if (minWritableBytes <= existingSlice.length()) {
            return existingSlice;
        }

        int newCapacity;
        if (existingSlice.length() == 0) {
            newCapacity = 1;
        }
        else {
            newCapacity = existingSlice.length();
        }
        int minNewCapacity = existingSlice.length() + minWritableBytes;
        while (newCapacity < minNewCapacity) {
            newCapacity <<= 1;
        }

        Slice newSlice = allocate(newCapacity);
        newSlice.setBytes(0, existingSlice, 0, existingSlice.length());
        return newSlice;
    }

    public static Slice allocate(int capacity)
    {
        if (capacity == 0) {
            return EMPTY_SLICE;
        }
        return new Slice(capacity);
    }

    public static Slice wrappedBuffer(byte[] array)
    {
        if (array.length == 0) {
            return EMPTY_SLICE;
        }
        return new Slice(array);
    }

    public static Slice copiedBuffer(ByteBuffer source, int sourceOffset, int length)
    {
        Preconditions.checkNotNull(source, "source is null");
        int newPosition = source.position() + sourceOffset;
        return copiedBuffer(
                source.duplicate().order(ByteOrder.LITTLE_ENDIAN).clear().limit(newPosition + length).position(newPosition));
    }

    public static Slice copiedBuffer(ByteBuffer source)
    {
        Preconditions.checkNotNull(source, "source is null");
        Slice copy = allocate(source.limit() - source.position());
        copy.setBytes(0, source.duplicate().order(ByteOrder.LITTLE_ENDIAN));
        return copy;
    }

    public static Slice copiedBuffer(String string, Charset charset)
    {
        Preconditions.checkNotNull(string, "string is null");
        Preconditions.checkNotNull(charset, "charset is null");

        return wrappedBuffer(string.getBytes(charset));
    }

    public static ByteBuffer encodeString(CharBuffer src, Charset charset)
    {
        CharsetEncoder encoder = getEncoder(charset);
        ByteBuffer dst = ByteBuffer.allocate(
                (int) ((double) src.remaining() * encoder.maxBytesPerChar()));
        try {
            CoderResult cr = encoder.encode(src, dst, true);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
            cr = encoder.flush(dst);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
        }
        catch (CharacterCodingException x) {
            throw new IllegalStateException(x);
        }
        dst.flip();
        return dst;
    }

    public static String decodeString(ByteBuffer src, Charset charset)
    {
        CharsetDecoder decoder = getDecoder(charset);
        CharBuffer dst = CharBuffer.allocate(
                (int) ((double) src.remaining() * decoder.maxCharsPerByte()));
        try {
            CoderResult cr = decoder.decode(src, dst, true);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
            cr = decoder.flush(dst);
            if (!cr.isUnderflow()) {
                cr.throwException();
            }
        }
        catch (CharacterCodingException x) {
            throw new IllegalStateException(x);
        }
        return dst.flip().toString();
    }

    private static final ThreadLocal<Map<Charset, CharsetEncoder>> encoders =
            new ThreadLocal<Map<Charset, CharsetEncoder>>()
            {
                @Override
                protected Map<Charset, CharsetEncoder> initialValue()
                {
                    return new IdentityHashMap<>();
                }
            };

    private static final ThreadLocal<Map<Charset, CharsetDecoder>> decoders =
            new ThreadLocal<Map<Charset, CharsetDecoder>>()
            {
                @Override
                protected Map<Charset, CharsetDecoder> initialValue()
                {
                    return new IdentityHashMap<>();
                }
            };

    /**
     * Returns a cached thread-local {@link CharsetEncoder} for the specified
     * <tt>charset</tt>.
     */
    private static CharsetEncoder getEncoder(Charset charset)
    {
        if (charset == null) {
            throw new NullPointerException("charset");
        }

        Map<Charset, CharsetEncoder> map = encoders.get();
        CharsetEncoder e = map.get(charset);
        if (e != null) {
            e.reset();
            e.onMalformedInput(CodingErrorAction.REPLACE);
            e.onUnmappableCharacter(CodingErrorAction.REPLACE);
            return e;
        }

        e = charset.newEncoder();
        e.onMalformedInput(CodingErrorAction.REPLACE);
        e.onUnmappableCharacter(CodingErrorAction.REPLACE);
        map.put(charset, e);
        return e;
    }

    /**
     * Returns a cached thread-local {@link CharsetDecoder} for the specified
     * <tt>charset</tt>.
     */
    private static CharsetDecoder getDecoder(Charset charset)
    {
        if (charset == null) {
            throw new NullPointerException("charset");
        }

        Map<Charset, CharsetDecoder> map = decoders.get();
        CharsetDecoder d = map.get(charset);
        if (d != null) {
            d.reset();
            d.onMalformedInput(CodingErrorAction.REPLACE);
            d.onUnmappableCharacter(CodingErrorAction.REPLACE);
            return d;
        }

        d = charset.newDecoder();
        d.onMalformedInput(CodingErrorAction.REPLACE);
        d.onUnmappableCharacter(CodingErrorAction.REPLACE);
        map.put(charset, d);
        return d;
    }
}
