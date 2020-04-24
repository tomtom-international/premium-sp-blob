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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.Test;

public class VarIntEncodingTest {

    @Test
    public void encodingOfNegativeValuesYieldsThreeBytes() throws IOException {
        DataOutputStream myOutputStream;
        ByteArrayOutputStream myByteArrayStream;
        for (short value = Short.MIN_VALUE; value < 0; ++value) {
            myByteArrayStream = new ByteArrayOutputStream();
            myOutputStream = new DataOutputStream(myByteArrayStream);
            VarIntEncoding.encode(value, myOutputStream);
            myOutputStream.flush();
            byte[] written = myByteArrayStream.toByteArray();
            assertEquals(
                    String.format("Unexpected number of bytes in VarInt encoding of %d - expected 3, got %d",
                            value, written.length),
                    3, written.length);
        }
    }

    @Test
    public void encodingOfValidValuesYieldsCorrectNumberOfBytes() throws IOException {
        DataOutputStream myOutputStream;
        ByteArrayOutputStream myByteArrayStream;
        for (int intValue = 0; intValue <= Short.MAX_VALUE; ++intValue) {
            myByteArrayStream = new ByteArrayOutputStream();
            myOutputStream = new DataOutputStream(myByteArrayStream);
            short value = (short) intValue;
            VarIntEncoding.encode(value, myOutputStream);
            myOutputStream.flush();
            byte[] written = myByteArrayStream.toByteArray();
            int expectedBytes = expectedNumberOfBytes(value);
            assertEquals(
                    String.format("Unexpected number of bytes in VarInt encoding of %d - expected %d, got %d",
                            value, expectedBytes, written.length),
                    expectedBytes, written.length);
        }
    }

    private static int expectedNumberOfBytes(final short value) {
        if (value <= 0x7f) {
            return 1;
        }
        if (value <= 0x3fff) {
            return 2;
        }
        return 3;
    }

    @Test
    public void decodingEncodedValuesYieldsOriginalValue() throws IOException {
        DataOutputStream myOutputStream;
        ByteArrayOutputStream myByteArrayStream;
        DataInputStream myInputStream;
        ByteArrayInputStream myByteArrayInputStream;

        for (int intValue = Short.MIN_VALUE; intValue <= Short.MAX_VALUE; ++intValue) {
            myByteArrayStream = new ByteArrayOutputStream();
            myOutputStream = new DataOutputStream(myByteArrayStream);
            short value = (short) intValue;
            VarIntEncoding.encode(value, myOutputStream);
            myOutputStream.flush();
            byte[] written = myByteArrayStream.toByteArray();

            myByteArrayInputStream = new ByteArrayInputStream(written);
            myInputStream = new DataInputStream(myByteArrayInputStream);
            short result = VarIntEncoding.decode(myInputStream);
            assertEquals(
                    String.format("Encoding/decoding of %d yields %d", value, result), value, result);
        }
    }
}