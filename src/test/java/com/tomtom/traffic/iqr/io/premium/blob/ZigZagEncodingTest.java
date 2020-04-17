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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ZigZagEncodingTest {

    @Test
    public void encodingYieldsExpectedValue() {
        for (int orig = Short.MIN_VALUE; orig <= Short.MAX_VALUE; ++orig) {
            short result = ZigZagEncoding.encode16((short) orig);
            if (orig == 0) {
                assertEquals(String.format("ZigZag.encode(%d) == %d != 0.", orig, result), orig, result);
                continue;
            }
            int bitResult = 0xffff & result;
            int expected;
            if (orig < 0) {
                expected = -orig * 2 - 1;
            } else {
                expected = 2 * orig;
            }
            assertEquals(String.format("ZigZag.encode(%d) == %d != %d.", orig, bitResult, expected), bitResult, expected);
        }
    }

    @Test
    public void decodingYieldsExpectedValue() {
        for (short orig = 0; orig <= (Short.MAX_VALUE - 1) / 2; ++orig) {
            int negativeEncoded = 2 * orig + 1;
            int positiveEncoded = 2 * orig;
            short negativeResult = ZigZagEncoding.decode16((short) negativeEncoded);
            short positiveResult = ZigZagEncoding.decode16((short) positiveEncoded);
            assertEquals(
                    String.format("Decoding of %d yields %d instead of %d.", negativeEncoded, negativeResult, -orig - 1),
                    -orig - 1, negativeResult);
            assertEquals(
                    String.format("Decoding of %d yields %d instead of %d.", positiveEncoded, positiveResult, orig),
                    orig, positiveResult);
        }
    }

    @Test
    public void decodingEncodedYieldsOriginalValue() {
        for (int orig = Short.MIN_VALUE; orig <= Short.MAX_VALUE; ++orig) {
            short result = ZigZagEncoding.decode16(ZigZagEncoding.encode16((short) orig));
            assertEquals(String.format("Encoding/decoding of %d yields %d.", orig, result), orig, result);
        }
    }
}