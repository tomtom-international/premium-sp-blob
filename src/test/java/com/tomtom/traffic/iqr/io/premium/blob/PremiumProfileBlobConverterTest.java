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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.function.IntFunction;

import com.google.common.primitives.Doubles;
import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Test;

public class PremiumProfileBlobConverterTest {

    private static final Offset<Double> EPS = Offset.offset(1.0e-7);
    private static final int WHOLE_DAY_RESOLUTION = 24 * 60;

    private double[][] inputSpeeds;
    private IntFunction<PremiumProfileBlobData.DaySpeedsAccessor> inputSpeedsAccessor;
    private int inputResolution;
    private int inputWeekdaySpeed;
    private int inputWeekendSpeed;

    @Before
    public void setUp() {
        inputSpeeds = new double[7][];
        inputSpeedsAccessor = PremiumProfileBlobData.arrayToSpeedsAccessor(inputSpeeds);
        inputWeekdaySpeed = 0;
        inputWeekendSpeed = 0;
        inputResolution = WHOLE_DAY_RESOLUTION;
    }

    @Test
    public void noDailyProfiles() {
        inputWeekdaySpeed = 81;
        inputWeekendSpeed = 87;
        runOnSimpleInputAndVerify(false);
        runOnSimpleInputAndVerify(true);
    }

    /**
     * Tests that resolutions larger than {@value Byte#MAX_VALUE} are encoded and decoded correctly.
     *
     * Encodes premium profile blob data with large resolution, encodes and decodes it and verifies the result is still the same.
     */
    @Test
    public void largeResultion_decodedCorrectly() {
        int largerThanByteSizeResolution = 240;

        IntFunction<PremiumProfileBlobData.DaySpeedsAccessor> emptySpeeds = PremiumProfileBlobData.arrayToSpeedsAccessor(new double[7][6]);
        PremiumProfileBlobData inputData = new PremiumProfileBlobData(0, 0, emptySpeeds, largerThanByteSizeResolution);

        PremiumProfileBlobConverter fixture = new PremiumProfileBlobConverter();

        byte[] dataAsBytes = fixture.toBinaryBlob(inputData);

        PremiumProfileBlobData convertedBlob = fixture.fromBinaryBlob(dataAsBytes);

        assertThat(convertedBlob.getTimeResolutionMinutes()).isEqualTo(largerThanByteSizeResolution);
    }

    @Test
    public void allDaysValidSpeeds() {
        double[] daySpeeds = {129, 128.5, 128, 121.8, 121.3, 120.9, 64.8, 64.7, 64.3, 64.2, 30, 30.1, 30.2, 30.3, 30.4,
                30.5, 30.6, 30.7, 30.8, 30.9, 5.01, 5.02, 5.03, 5.04, 5.05, 5.07};
        fillAllDaysWithSpeedsRotated(daySpeeds);

        TimeResAndBitSet expTimeResAndBitSet =
                new TimeResAndBitSet(inputResolution, (byte) inputResolution, (byte) 0b01111111);
        runOnFullInputAndVerify(false, expTimeResAndBitSet);
        runOnFullInputAndVerify(true, expTimeResAndBitSet);
    }

    @Test
    public void missingDaysValidSpeeds() {
        double[] daySpeeds = {129, 128.5, 128, 121.8, 121.3, 120.9, 64.8, 64.7, 64.3, 64.2, 30, 30.1, 30.2, 30.3, 30.4,
                30.5, 30.6, 30.7, 30.8, 30.9, 5.01, 5.02, 5.03, 5.04, 5.05, 5.07};
        fillAllDaysWithSpeedsRotated(daySpeeds);
        inputSpeeds[2] = inputSpeeds[5] = null;

        TimeResAndBitSet expTimeResAndBitSet =
                new TimeResAndBitSet(inputResolution, (byte) inputResolution, (byte) 0b01011011);
        runOnFullInputAndVerify(false, expTimeResAndBitSet);
        runOnFullInputAndVerify(true, expTimeResAndBitSet);
    }

    @Test
    public void maxResolutionWithSomeDaysMissing() {
        double[] daySpeeds = {121};
        fillAllDaysWithSpeedsRotated(daySpeeds);
        inputSpeeds[1] = inputSpeeds[2] = null;

        TimeResAndBitSet expTimeResAndBitSet = new TimeResAndBitSet(inputResolution, (byte) 0, (byte) 0b01111001);
        runOnFullInputAndVerify(false, expTimeResAndBitSet);
        runOnFullInputAndVerify(true, expTimeResAndBitSet);
    }

    @Test
    public void writingShouldFailOnNegativeSpeeds() {
        double[] daySpeeds = {-2};
        fillAllDaysWithSpeedsRotated(daySpeeds);

        PremiumProfileBlobConverter converter = new PremiumProfileBlobConverter();
        PremiumProfileBlobData blobData =
                new PremiumProfileBlobData(inputWeekdaySpeed, inputWeekendSpeed, inputSpeedsAccessor, inputResolution);
        assertThatThrownBy(() -> converter.toBinaryBlob(blobData)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void writingShouldFailOnTooLargeSpeeds() {
        double[] daySpeeds = {270.0};
        fillAllDaysWithSpeedsRotated(daySpeeds);

        PremiumProfileBlobConverter converter = new PremiumProfileBlobConverter();
        PremiumProfileBlobData blobData =
                new PremiumProfileBlobData(inputWeekdaySpeed, inputWeekendSpeed, inputSpeedsAccessor, inputResolution);
        assertThatThrownBy(() -> converter.toBinaryBlob(blobData)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void readingShouldFailOnWrongVersion() {
        double[] daySpeeds = {40};
        fillAllDaysWithSpeedsRotated(daySpeeds);
        PremiumProfileBlobConverter converter = new PremiumProfileBlobConverter();
        PremiumProfileBlobData blobData =
                new PremiumProfileBlobData(inputWeekdaySpeed, inputWeekendSpeed, inputSpeedsAccessor, inputResolution);
        byte[] blob = converter.toBinaryBlob(blobData);

        //does not fail, i.e. blob should be OK
        converter.fromBinaryBlob(blob);

        //let's change the blob version
        blob[0] = PremiumProfileBlobConverter.VERSION - 1;
        //does not fail as it should support the previous versions
        converter.fromBinaryBlob(blob);

        //now it should fail as the blob's version is larger that it can support:
        blob[0] = PremiumProfileBlobConverter.VERSION + 1;
        assertThatThrownBy(() -> converter.fromBinaryBlob(blob)).
                isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");
    }

    @Test
    public void versionCheckRespondsCorrectly() {
        // creating a simple blob for version checking
        double[] daySpeeds = {40};
        fillAllDaysWithSpeedsRotated(daySpeeds);
        PremiumProfileBlobConverter converter = new PremiumProfileBlobConverter();
        PremiumProfileBlobData blobData =
                new PremiumProfileBlobData(inputWeekdaySpeed, inputWeekendSpeed, inputSpeedsAccessor, inputResolution);
        byte[] blob = converter.toBinaryBlob(blobData);

        // has current version, check should yield true
        assertThat(PremiumProfileBlobConverter.hasSupportedVersion(blob)).isTrue();

        // previous versions should work as well
        blob[0] = PremiumProfileBlobConverter.VERSION - 1;
        assertThat(PremiumProfileBlobConverter.hasSupportedVersion(blob)).isTrue();

        // later versions should not be supported
        blob[0] = PremiumProfileBlobConverter.VERSION + 1;
        assertThat(PremiumProfileBlobConverter.hasSupportedVersion(blob)).isFalse();

        // negative integer values should be interpreted as large byte values to also allow versions between 127-255
        // the version -1 ~= 255 should thus currently not be supported (yet)
        blob[0] = (byte) -1;
        assertThat(PremiumProfileBlobConverter.hasSupportedVersion(blob)).isFalse();

        // version values between 127-255 should be allowed and correctly parsed from the byte as positive values
        // the version 240 ~= -16 should thus currently not be supported (yet)
        blob[0] = (byte) 240;
        assertThat(PremiumProfileBlobConverter.hasSupportedVersion(blob)).isFalse();
    }

    @Test
    public void settingMeanSpeedsWithoutChangingProfiles() {
        // dummy daily speeds - only to verify that setting the mean speeds does not alter the daily speed profiles
        double[] daySpeeds = {129, 128.5, 128, 121.8, 121.3, 120.9, 64.8, 64.7, 64.3, 64.2, 30, 30.1, 30.2, 30.3};
        fillAllDaysWithSpeedsRotated(daySpeeds);
        TimeResAndBitSet expTimeResAndBitSet =
                new TimeResAndBitSet(inputResolution, (byte) inputResolution, (byte) 0b01111111);

        // create full input data
        PremiumProfileBlobData inputData =
                new PremiumProfileBlobData(inputWeekdaySpeed, inputWeekendSpeed, inputSpeedsAccessor, inputResolution);

        //convert to blob
        PremiumProfileBlobConverter converter = new PremiumProfileBlobConverter(true);
        byte[] blob = converter.toBinaryBlob(inputData);

        // Change mean speeds after conversion (and adapt expected speeds)
        inputWeekdaySpeed = inputWeekdaySpeed + 7;
        inputWeekendSpeed = inputWeekendSpeed + 3;
        PremiumProfileBlobConverter.setMeanSpeeds(blob, inputWeekdaySpeed, inputWeekendSpeed);

        // convert back
        PremiumProfileBlobData actResult = converter.fromBinaryBlob(blob);

        // verify correctness of re-converted data - only want the mean speeds to have changed, not the daily profiles
        verifyCorrectness(actResult, blob, expTimeResAndBitSet, true);
    }

    private void runOnFullInputAndVerify(final boolean isZipData, final TimeResAndBitSet expTimeResAndBitSet) {
        // create full input data
        PremiumProfileBlobData inputData =
                new PremiumProfileBlobData(inputWeekdaySpeed, inputWeekendSpeed, inputSpeedsAccessor, inputResolution);

        //convert to blob and from blob
        PremiumProfileBlobConverter converter = new PremiumProfileBlobConverter(isZipData);
        byte[] blob = converter.toBinaryBlob(inputData);
        PremiumProfileBlobData actResult = converter.fromBinaryBlob(blob);

        // verify correctness of re-converted data
        verifyCorrectness(actResult, blob, expTimeResAndBitSet, isZipData);
    }

    private void verifyCorrectness(final PremiumProfileBlobData actResult, final byte[] blob,
                                   final TimeResAndBitSet expTimeResAndBitSet, final boolean isZipData) {
        // verify correct mean speeds are recovered
        assertThat(actResult.getWeekDaySpeed()).isEqualTo(inputWeekdaySpeed);
        assertThat(actResult.getWeekendSpeed()).isEqualTo(inputWeekendSpeed);
        if (expTimeResAndBitSet != null) {
            //verify we have correctly restored the input speeds (within the limits of the speed-dependant resolution)
            for (int day = 0; day < 7; ++day) {
                String context = String.format("day=%s, zip=%s, timeRes=%s min", day, isZipData, inputResolution);
                Optional<double[]> actDaySpeeds = actResult.getDaySpeedsAsArray(day);
                if (inputSpeeds[day] != null) {
                    double[] expected = expectedSpeeds(inputSpeeds[day]);
                    assertThat(actDaySpeeds.isPresent()).isTrue();
                    assertThat(actDaySpeeds.get()).as(context).containsExactly(expected, EPS);
                } else {
                    assertThat(actDaySpeeds.isPresent()).isFalse();
                }
            }
            //verify some header fields in the blob
            assertThat(blob[3]).isEqualTo(expTimeResAndBitSet.outputTimeResolution);
            assertThat(blob[4]).isEqualTo(expTimeResAndBitSet.daysBitSet);
            //verify the header fields extracted from the blob:
            assertThat(actResult.getTimeResolutionMinutes()).isEqualTo(expTimeResAndBitSet.actualTimeResolution);
        }
    }

    private void runOnSimpleInputAndVerify(final boolean isZipData) {
        // create simple input data without daily profiles and only mean speeds
        PremiumProfileBlobData inputData = new PremiumProfileBlobData(inputWeekdaySpeed, inputWeekendSpeed);

        // convert to blob and from blob
        PremiumProfileBlobConverter converter = new PremiumProfileBlobConverter(isZipData);
        byte[] blob = converter.toBinaryBlob(inputData);
        PremiumProfileBlobData actResult = converter.fromBinaryBlob(blob);

        // verify correct mean speeds are recovered
        assertThat(actResult.getWeekDaySpeed()).isEqualTo(inputWeekdaySpeed);
        assertThat(actResult.getWeekendSpeed()).isEqualTo(inputWeekendSpeed);

        // no daily profiles were written
        // -> converted data has exactly 3 bytes
        assertThat(blob.length).isEqualTo(3);
        // -> created object does not provide daily-speed-profiles
        assertThat(actResult.hasDailySpeeds()).isFalse();
        // -> no profile is available for any day
        for (int day = 0; day < 7; ++day) {
            assertThat(actResult.hasDaySpeeds(day)).isFalse();
        }
        // provided time resolution is 0
        assertThat(actResult.getTimeResolutionMinutes()).isEqualTo(0);

    }

    // creating slightly different speed values for every day by rotating the original array.
    private void fillAllDaysWithSpeedsRotated(final double[] daySpeeds) {
        double weekendTotal = 0.0;
        double weekdayTotal = 0.0;
        inputResolution = WHOLE_DAY_RESOLUTION / daySpeeds.length;
        for (int day = 0; day < inputSpeeds.length; ++day) {
            ArrayList<Double> speedsList = new ArrayList<>(Doubles.asList(daySpeeds));
            Collections.rotate(speedsList, day);
            if (day == 0 || day == 6) {
                inputWeekendSpeed += (int) speedsList.stream().mapToDouble(f -> f).sum();
            } else {
                inputWeekdaySpeed += (int) speedsList.stream().mapToDouble(f -> f).sum();
            }
            inputSpeeds[day] = Doubles.toArray(speedsList);
        }
        inputWeekendSpeed = (int) Math.round(weekendTotal / (2.0 * daySpeeds.length));
        inputWeekdaySpeed = (int) Math.round(weekdayTotal / (5.0 * daySpeeds.length));
    }

    // converting input speed values to the effectively stored values after encoding
    static double[] expectedSpeeds(final double[] inputSpeeds) {
        double[] result = new double[inputSpeeds.length];
        for (int bin = 0; bin < inputSpeeds.length; ++bin) {
            result[bin] = PremiumProfileBlobConverter.asEncoded(inputSpeeds[bin]);
        }
        return result;
    }

    private static class TimeResAndBitSet {
        private final int actualTimeResolution;
        private final byte outputTimeResolution;
        private final byte daysBitSet;

        TimeResAndBitSet(final int actualTimeResolution, final byte outputTimeResolution, final byte daysBitSet) {
            this.actualTimeResolution = actualTimeResolution;
            this.outputTimeResolution = outputTimeResolution;
            this.daysBitSet = daysBitSet;
        }
    }
}
