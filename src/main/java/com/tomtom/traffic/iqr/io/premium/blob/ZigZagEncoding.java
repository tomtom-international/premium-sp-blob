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

/**
 * Utility class providing functionality to convert values according to the ZigZag encoding.
 *
 * ZigZag encoding maps signed integers to unsigned integers such that signed numbers with a small absolute value have a
 * small encoded unsigned value too. Effectively, it uses the least significant bit as the sign bit instead of the most
 * significant bit as usual. In other words, negative integer values are mapped to odd unsigned values, while positive
 * values are mapped to even unsigned values.
 *
 * For further details on the ZigZag encoding see
 * <a href="https://en.wikipedia.org/wiki/Variable-length_quantity#Zigzag_encoding">here</a>.
 */
class ZigZagEncoding {

    private ZigZagEncoding() {
        // Hiding the implicit public constructor
    }

    /**
     * Performs ZigZag encoding on the passed value.
     *
     * The returned value is to be interpreted as the unsigned ZigZag-representation of the original value. Note that
     * Java does not natively support unsigned types. Both the in- and output value of the method are thus of a
     * signed type, only the interpretation of the bits is changed. Take this into account accordingly and be careful
     * when comparing or casting the returned values.
     *
     * @param value  the signed value to be encoded
     * @return the unsigned ZigZag-encoded representation of the original value
     */
    public static short encode16(final short value) {
        return (short) ((value << 1) ^ (value >> 15));
    }

    /**
     * Interprets the passed number as an unsigned ZigZag-encoded value and returns the respective original value.
     *
     * @param num  the unsigned ZigZag representation of a signed value
     * @return the represented signed value
     */
    public static short decode16(final short num) {
        int intValue = Short.toUnsignedInt(num);
        return (short) ((intValue >>> 1) ^ (-(intValue & 1)));
    }
}
