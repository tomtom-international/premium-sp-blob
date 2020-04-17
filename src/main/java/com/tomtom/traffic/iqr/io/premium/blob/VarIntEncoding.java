/*
 * Copyright (C) 2020 TomTom N.V. (www.tomtom.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tomtom.traffic.iqr.io.premium.blob;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Utility class providing functionality to read/write {@code short} values in Base-128 encoding from/to streams.
 *
 * For details on the Base-128 encoding see <a href="https://en.wikipedia.org/wiki/Variable-length_quantity">here</a>.
 */
class VarIntEncoding {

    private VarIntEncoding() {
        // Hiding the implicit public constructor
    }

    /**
     * Writes the passed {@code short} value to the provided {@link DataOutputStream} in Base-128 encoding.
     *
     * @param value  the value to be encoded and written
     * @param out  the {@code DataOutputStream} to write the value to
     * @throws IOException  if writing to the passed {@code DataOutputStream} fails.
     */
    public static void encode(final short value, final DataOutputStream out) throws IOException {
        int intValue = Short.toUnsignedInt(value);
        int bits = intValue & 0x7f;
        intValue = intValue >>> 7;
        while (intValue > 0) {
            out.writeByte((byte) (bits | 0x80));
            bits = intValue & 0x7f;
            intValue = intValue >>> 7;
        }
        out.writeByte((byte) bits);
    }

    /**
     * Reads a Base-128-encoded value from the passed {@link DataInputStream} and returns the decoded number as {@code short}.
     *
     * @param in  the {@code DataInputStream} to read from
     * @return the decoded value
     * @throws IOException  if reading from the {@code DataInputStream} fails.
     * @throws IllegalArgumentException  if the read-in data cannot be interpreted a Base-128 value
     */
    public static short decode(final DataInputStream in) throws IOException {
        int result = 0;
        int shift = 0;

        while (shift <= 16) {
            byte bits = in.readByte();
            result = result | ((bits & 0x7f) << shift);
            if ((bits & 0x80) == 0) {
                return (short) result;
            }
            shift += 7;
        }

        throw new IllegalArgumentException("Unexpected number of bytes in VarInt-encoded short.");
    }
}
