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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class FlexSpeedEncodingTest {

    @Test
    public void testMinimumNonZeroSpeedValues() {
        // ensure that the provided MINIMUM_NONZERO_INPUT_VALUE value is not rounded to 0 when encoded
        assertThat(FlexSpeedEncoding.encode(FlexSpeedEncoding.MINIMUM_NONZERO_INPUT_VALUE))
                .isGreaterThan((short)0);
        // ensure that encoding the provided MINIMUM_NONZERO_INPUT_VALUE value indeed decodes back to the provided
        // MINIMUM_NONZERO_OUTPUT_VALUE
        assertThat(FlexSpeedEncoding.decode(FlexSpeedEncoding.encode(FlexSpeedEncoding.MINIMUM_NONZERO_INPUT_VALUE)))
                .isEqualTo(FlexSpeedEncoding.MINIMUM_NONZERO_OUTPUT_VALUE);
        // ensure that the provided MINIMUM_NONZERO_INPUT_VALUE value is not unnecessarily large
        // (anything smaller is indeed rounded to zero)
        double nextSmallerValue = Math.nextAfter(FlexSpeedEncoding.MINIMUM_NONZERO_INPUT_VALUE, Double.NEGATIVE_INFINITY);
        assertThat(FlexSpeedEncoding.encode(nextSmallerValue)).isEqualTo((short)0);
    }

    @Test
    public void encodingOfNegativeValuesYieldsZero() {
        for (double value = -255; value < 0; value += 0.01) {
            // negative values are always encoded (and thus also decoded) as 0
            assertThat(FlexSpeedEncoding.encode(value)).isEqualTo((short)0);
            assertThat(FlexSpeedEncoding.decode(FlexSpeedEncoding.encode(value))).isEqualTo(0.0);
        }
    }

    @Test
    public void encodingOfTooLargeValuesYieldsMax() {
        double value = 255;
        // just testing some exemplary values above 255
        for (int exponent = 0; exponent < 8; ++exponent) {
            // largest value should be 255 ...
            assertThat(FlexSpeedEncoding.decode(FlexSpeedEncoding.encode(value))).isEqualTo(255);
            // ... which corresponds to 10 bits set
            assertThat(FlexSpeedEncoding.encode(value)).isEqualTo((short)0x3ff);
            value *= 2;
        }
    }

    @Test
    public void encodingValidSpeedsRetainsCorrectResolution() {
        // different value-ranges have different resolution
        // the encoded/decoded values in each range should retain this expected resolution

        // values below 4 have a resolution of 1/64
        assertResolutionInValueRange(0.0, 4.0, 1 / 64.0);

        // above that, values between 2^n-1 and 2^n have resolution 2^-(8 - n)
        double lower = 4.0;
        double upper;
        for (int exponent = 3; exponent <= 7; ++exponent) {
            upper = Math.pow(2, exponent);
            double resolution = Math.pow(2, -(8 - exponent));
            assertResolutionInValueRange(lower, upper, resolution);
            lower = upper;
        }
    }

    private static void assertResolutionInValueRange(final double lower, final double upper, final double resolution) {
        for (double value = lower; value <= upper; value += 0.001) {
            int encoded = FlexSpeedEncoding.encode(value);

            // encoded value must at most have 10 bit
            assertThat(encoded >> 10).isEqualTo(0);

            // encoded value must retain the expected resolution
            double expected = Math.round(value / resolution) * resolution;
            double obtained = FlexSpeedEncoding.decode(FlexSpeedEncoding.encode(value));
            assertThat(obtained)
                    .withFailMessage(
                            String.format("Encoding of %g yields %g instead of %g (resolution = %g)",
                                    value, obtained, expected, resolution))
                    .isEqualTo(expected);
        }
    }

    @Test
    public void encodingOfValidSpeedsYieldsTenBit() {
        for (double value = 0.0; value <= 255; value += 0.001) {
            // encoded value must at most have 10 bit
            assertThat(FlexSpeedEncoding.encode(value) >> 10).isEqualTo(0);
        }
    }
}
