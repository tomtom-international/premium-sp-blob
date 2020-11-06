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


import com.google.common.primitives.Bytes;

import java.io.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Converts Premium-Speed-Profile (PSP) data to and from binary blobs.
 *
 * The PSP data is provided/returned in the form of {@link PremiumProfileBlobData} objects.
 *
 * <b>Binary Blob Format</b>
 * <p>
 * The format of a PSP binary blob is as follows (version 1.0):
 *
 * <ul>
 * <li> Byte No. 1 : Version of the blob</li>
 * <li> Byte No. 2 : Working-day speed in km/h as 8-bit integer</li>
 * <li> Byte No. 3 : Weekend-day speed in km/h as 8-bit integer</li>
 * </ul>
 * (Possible blob end - e.g. for road elements without detailed profile data)
 * <ul>
 * <li> Byte No. 4 : Width of the individual time bins in minutes as 8-bit integer</li>
 * <li> Byte No. 5 : Relevant days as bit field</li>
 * <li> Bytes 6+   : zlib-compressed speed values in a dedicated encoding (see below)</li>
 * </ul>
 *
 * <b>Daily Profile Information</b>
 * <p>
 * A blob containing only mean speeds and no daily profiles has a size of exactly 3 bytes. A blob that <em>does</em>
 * contain detailed daily speed profiles contains two additional bytes plus the zlib-deflated and encoded speed-profile
 * data, thus resulting in more than 5 + 11 bytes (the 11 bytes resulting from the minimal zlib-byte-overhead of the 6
 * byte header and 5 byte footer).
 *
 * If existing, the 4th byte in the blob encodes the time-span of the profiles' single time bins in minutes. All time
 * bins in all contained profiles inside one blob have the same width. Since the time bins of each profile cover all 24
 * hours of the day, the specified width directly determines the number of speed values per profile.
 *
 * The 5th byte specifies the days for which speed profile data is included in form of a bit-field: The first 7 bits
 * represent the single days of the week from Sunday (least significant bit) to Saturday (second-most-significant bit).
 * If the bit corresponding to a given day is set in the bit-field, a speed profile is included for that day.
 *
 * The number of contained daily profiles times the number of speed values per profile (see above) determines the number
 * of speed values encoded in the rest of the blob.
 *
 * The remaining bytes in the blob represent the encoded speed values of all contained daily profiles. For each day, the
 * speed values are provided in temporal order of the time bins. The order of the days is the same as in the preceding
 * bit field. Days for which no profile is available are simply skipped. The speed values for all contained days are
 * provided in a single continuous sequence starting with the first value of the first available day and ending with the
 * last value of the last contained day.
 *
 * <b>Speed-Value Encoding</b>
 * <p>
 * The sequence of speed values is provided in a custom encoding as follows:
 * <ul>
 * <li> The speeds are encoded as 10-bit floating-point values using the {@link FlexSpeedEncoding} functionality.</li>
 * <li> Only for the very first speed value in the entire sequence the full absolute value is retained. For all further
 * speeds, only the difference to the absolute value of their predecessor is encoded and stored.</li>
 * <li> Using methods of the {@link ZigZagEncoding} class, the resulting deltas are encoded as positive integer numbers.</li>
 * <li> The {@link VarIntEncoding} class is used to efficiently encode the resulting positive deltas.</li>
 * <li> Finally, the entire byte array is deflated using standard zlib-compression.</li>
 * </ul>
 *
 * Note that due to the used {@code FlexSpeedEncoding}, the precision of the stored values is not uniform but depends
 * on their respective absolute values. (Functionality is provided to infer the effectively encoded number for a given
 * speed value, see {@link #asEncoded(double)}.)
 */
public class PremiumProfileBlobConverter {

    // Current version - only backward compatibility is guaranteed, i.e. only this and previous versions are compatible
    static final int VERSION = 1;

    // Number of minutes per day
    private static final int MINUTES_PER_DAY = 60 * 24;

    // Maximum speed value that can be encoded/decoded to/from binary data
    private static final int MAX_SPEED_VALUE = 255;

    // All days of week starting with Sunday = 0:
    private static final int[] ALL_DAYS = {0, 1, 2, 3, 4, 5, 6};

    // Compression level used for zlib deflate compression
    private static final int COMPRESSION_LEVEL = Deflater.DEFAULT_COMPRESSION;

    // Internal flag to switch off zlib compression for testing
    private final boolean isZipData;

    // zlib deflation adds a 6 byte header for each 64kB block and a 5 byte footer at the end of each archive
    // Due to the maximum resolution of 1 minute, we write at most approx. 20kB into a single archive (only one block)
    private static final int ZLIB_BYTE_OVERHEAD = 11;

    public PremiumProfileBlobConverter() {
        this(true);
    }

    // To simplify testing
    PremiumProfileBlobConverter(final boolean isZipData) {
        this.isZipData = isZipData;
    }

    /**
     * Checks whether the format-version of the provided binary blob is supported by this converter.
     *
     * Note that this method does not check the integrity of the entire blob, but only checks the encoded version
     * information.
     *
     * @param blob  the binary blob of which the version shall be tested
     * @return whether the version of the provided blob can be correctly parsed by this converter
     */
    public static boolean hasSupportedVersion(final byte[] blob) {
        int version = Byte.toUnsignedInt(blob[0]);
        return version <= VERSION;
    }

    /**
     * Returns a String representation of the given speed value as it would be encoded in a binary blob.
     *
     * Due to the value-dependent precision of encoded speed-values, the represented speed might differ from the passed
     * speed value.
     *
     * @param speed Speed.
     * @return Speed as text.
     *
     * @see #asEncoded(double)
     */
    public static String toText(final double speed) {
        double encoded = asEncoded(speed);
        if (encoded % 1 == 0) {
            // no decimal point if speed has integer value
            return String.valueOf(Math.toIntExact((long) encoded));
        } else {
            return String.valueOf(encoded);
        }
    }

    /**
     * Returns the speed value that is effectively stored if the passed speed is encoded in blob format.
     *
     * Due to the value-dependent precision of encoded speed-values inside the blob, this effectively stored speed can
     * differ from the originally passed speed value.
     *
     * @param speed Speed.
     * @return Encoded speed.
     */
    public static double asEncoded(final double speed) {
        return FlexSpeedEncoding.decode(FlexSpeedEncoding.encode(speed));
    }

    /**
     * Returns the smallest non-zero value that can effectively be stored within a binary blob.
     *
     * @see #asEncoded(double)
     *
     * @return The smallest speed value that can effectively be stored within a binary blob.
     */
    public static double getMinimumNonZeroOutputValue() {
        return FlexSpeedEncoding.MINIMUM_NONZERO_OUTPUT_VALUE;
    }

    /**
     * Returns the smallest input value that will result in an non-zero speed value when encoded in a binary blob.
     *
     * If it is important to guarantee that no speeds of zero will be included in a binary blob, any candidate speed
     * should be verified to be at least as large as this value.
     *
     * The effectively stored speed when encoding this value is given by {@link #getMinimumNonZeroOutputValue()}.
     *
     * The encoding of any speed smaller than this value will result in a stored value of zero.
     *
     * @see #asEncoded(double)
     *
     * @return The smallest speed that will be encoded as a non-zero value within a binary blob
     */
    public static double getMinimumNonZeroInputValue() {
        return FlexSpeedEncoding.MINIMUM_NONZERO_INPUT_VALUE;
    }

    /**
     * Sets the mean speeds for the working-days and the weekend-days within a binary blob to the provided values.
     *
     * This allows to change these values without having to decode and re-encode a given binary blob.
     *
     * @param blob  the binary blob the contents of which shall be changed
     * @param weekDaySpeed  the working-day mean-speed in km/h that shall be stored in the binary blob
     * @param weekendSpeed  the weekend-day mean-speed in km/h that shall be stored in the binary blob
     * @throws IllegalArgumentException if the version of the binary blob is not supported
     */
    public static void setMeanSpeeds(final byte[] blob, final int weekDaySpeed, final int weekendSpeed) {
        int version = Byte.toUnsignedInt(blob[0]);
        if (version > VERSION) {
            throw new IllegalArgumentException(String.format("Premium profile blob version %s is not supported",
                    version));
        }
        blob[1] = (byte) weekDaySpeed;
        blob[2] = (byte) weekendSpeed;
    }

    /**
     * Converts (decodes) a binary blob into a {@link PremiumProfileBlobData} object.
     *
     * @param blob  the binary blob to be converted in form of a byte array
     * @return {@code PremiumProfileBlobData} object matching the binary blob
     * @throws IllegalArgumentException if the version of the binary blob is not supported or decoding of the sequence
     * of contained profile speeds fails for any other reason
     */
    public PremiumProfileBlobData fromBinaryBlob(final byte[] blob) {
        int index = 0;
        int version = Byte.toUnsignedInt(blob[index++]);
        if (version > VERSION) {
            throw new IllegalArgumentException(String.format("Premium profile blob version %d is not supported", blob[0]));
        }
        int weekDaySpeed = Byte.toUnsignedInt(blob[index++]);
        int weekendSpeed = Byte.toUnsignedInt(blob[index++]);

        if (blob.length > 3) {
            return fromBinaryProfileData(blob, index, weekDaySpeed, weekendSpeed);
        }

        return new PremiumProfileBlobData(weekDaySpeed, weekendSpeed);
    }

    private PremiumProfileBlobData fromBinaryProfileData(final byte[] blob, final int index, final int weekDaySpeed,
                                                         final int weekendSpeed) {
        HeaderAndIndex headerAndDataIndex = Header.decode(blob, index);
        Header header = headerAndDataIndex.header;

        double[][] speeds = new double[ALL_DAYS.length][];
        try {
            if (header.daysBitSet != 0) { //just in case, be ready for no valid days at all
                decodeSpeeds(speeds, blob, headerAndDataIndex.index, header);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    String.format("Unable to read daily speed profiles from byte array. Reason: %s", e.getMessage()));
        }
        return new PremiumProfileBlobData(weekDaySpeed, weekendSpeed,
                PremiumProfileBlobData.arrayToSpeedsAccessor(speeds), header.timeResolution);
    }

    private void decodeSpeeds(final double[][] result, final byte[] blob, final int index, final Header header)
            throws IOException {
        ByteArrayInputStream inputByteStream = new ByteArrayInputStream(blob, index, blob.length - index);
        // If required, first de-compress the entire blob at once (faster than de-compressing byte by byte).
        if (isZipData) {
            inputByteStream = createDecompressedInputStream(inputByteStream, header);
        }
        // Decode the (de-compressed) speed-values.
        try (DataInputStream in = new DataInputStream(inputByteStream)) {
            decodeSpeedsFromStream(result, header, in);
        }
    }

    private static ByteArrayInputStream createDecompressedInputStream(final ByteArrayInputStream inputByteStream,
                                                                      final Header header) throws IOException {
        int bufferSize = determineSafeBufferSize(header);
        byte[] inflaterBufferArray = new byte[bufferSize];
        int numBytesRead;
        Inflater inflater = new Inflater();
        try (InputStream sourceStream = new InflaterInputStream(inputByteStream, inflater, bufferSize)) {
            numBytesRead = sourceStream.read(inflaterBufferArray, 0, bufferSize);
            if (numBytesRead < 0) {
                throw new EOFException("Unexpected end of binary blob.");
            }
        } finally {
            // When passing an external inflater to InflaterInputStream, it is not automatically "ended" on close.
            // Will be done in finalize(), but do this manually in order to prevent native memory exhaustion.
            // See also InflaterInputStream.close().
            inflater.end();
        }
        return new ByteArrayInputStream(inflaterBufferArray, 0, numBytesRead);
    }

    private static int determineSafeBufferSize(final Header header) {
        // Due to used delta-ZigZag-VarInt encoding have at most 2 bytes per speed value (uncompressed).
        int numTotalBytes = (MINUTES_PER_DAY / header.timeResolution) * 7 * 2;
        int nextPowerOfTwo = Integer.highestOneBit(numTotalBytes);
        int bufferSize = (numTotalBytes == nextPowerOfTwo) ? numTotalBytes : (nextPowerOfTwo << 1);
        // with header.timeResolution being at most 255 and MINUTES_PER_DAY being 60 * 24 = 1440, the minimum
        // valid buffer size that can result for intact data is 128. Set here as hard lower limit to be explicit.
        return Math.max(128, bufferSize);
    }

    private static void decodeSpeedsFromStream(final double[][] result, final Header header,
                                               final DataInputStream inStream) throws IOException {
        int speedsPerDay = MINUTES_PER_DAY / header.timeResolution;
        short lastSpeed = 0;
        for (int day : ALL_DAYS) {
            if (header.isRelevantDay(day)) {
                result[day] = new double[speedsPerDay];
                lastSpeed = decodeSpeedsForDay(result[day], inStream, speedsPerDay, lastSpeed);
            }
        }
    }

    private static short decodeSpeedsForDay(final double[] resDaySpeeds, final DataInputStream inStream,
                                            final int speedsPerDay, final short lastSpeed)
            throws IOException {
        short previous = lastSpeed;
        short current = previous;
        for (int bin = 0; bin < speedsPerDay; ++bin) {
            current = (short) (previous + ZigZagEncoding.decode16(VarIntEncoding.decode(inStream)));
            resDaySpeeds[bin] = FlexSpeedEncoding.decode(current);
            previous = current;
        }
        return current;
    }

    /**
     * Converts (encodes) a {@link PremiumProfileBlobData} object into a binary blob.
     *
     * The data is always encoded into a binary blob of the latest supported format version.
     *
     * If the number of profile-speeds for any included day within the provided {@code PremiumProfileBlobData} does not
     * match the specified temporal resolution, if any of the contained speed values is negative or larger than 255,
     * or if encoding fails for any other reason, an {@code IllegalArgumentException} is thrown.
     *
     * @param blobData  the {@code PremiumProfileBlobData} object to be converted to a binary blob
     * @return the converted binary blob as an array of bytes
     * @throws IllegalArgumentException if an error occurs during encoding, see description
     */
    public byte[] toBinaryBlob(final PremiumProfileBlobData blobData) {
        try {
            byte[] blobBytes = encodeVersionAndMeanSpeeds(blobData.getWeekDaySpeed(), blobData.getWeekendSpeed());
            if (blobData.hasDailySpeeds()) {
                Header header = extractHeader(blobData);
                byte[] headerPart = header.encode();
                byte[] dataPart = encodeDailySpeeds(blobData, header);
                blobBytes = Bytes.concat(blobBytes, headerPart, dataPart);
            }
            return blobBytes;
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    String.format("Unable to convert premium profile data to binary blob. Reason: %s", e.getMessage()));
        }
    }

    private static Header extractHeader(final PremiumProfileBlobData blobData) {
        int timeResolution = getCheckedTimeResolution(blobData);
        byte daysBitSet = determineDaysBitSet(blobData);
        return new Header(timeResolution, daysBitSet);
    }

    private static int getCheckedTimeResolution(final PremiumProfileBlobData blobData) {
        int timeResolutionMinutes = blobData.getTimeResolutionMinutes();
        int expectedBins = MINUTES_PER_DAY / timeResolutionMinutes;
        for (int day : ALL_DAYS) {
            PremiumProfileBlobData.DaySpeedsAccessor daySpeeds = blobData.getDaySpeeds(day).orElse(null);
            if (daySpeeds != null) {
                int actBins = daySpeeds.getTotalBins();
                if (actBins != expectedBins) {
                    throw new IllegalArgumentException(String.format(
                            "Time bins count for day %d does not match the time resolution of %d minutes: " +
                                    "expecting %d bins, got %d",
                            day, timeResolutionMinutes, expectedBins, actBins));
                }
            }
        }
        return timeResolutionMinutes;
    }

    private static byte determineDaysBitSet(final PremiumProfileBlobData blobData) {
        byte result = 0;
        for (int day : ALL_DAYS) {
            if (blobData.hasDaySpeeds(day)) {
                byte dayMask = Header.getDayMask(day);
                result = (byte) (result | dayMask);
            }
        }
        return result;
    }

    private static byte[] encodeVersionAndMeanSpeeds(final int weekDaySpeed, final int weekendSpeed) {
        return new byte[]{(byte) VERSION, (byte) weekDaySpeed, (byte) weekendSpeed};
    }

    private byte[] encodeDailySpeeds(final PremiumProfileBlobData blobData, final Header header) throws IOException {
        // Conservatively set buffer size for output to maximum size of uncompressed output plus zlib-overhead.
        int bufferSize = determineSafeBufferSize(header) + ZLIB_BYTE_OVERHEAD;
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(bufferSize);
        byte[] result;

        // First create uncompressed byte array.
        try (DataOutputStream out = new DataOutputStream(byteStream)) {
            encodeDailySpeedsToStream(blobData, header, out);
        }
        result = byteStream.toByteArray();

        // If required, compress the entire byte array at once (faster than compressing byte by byte).
        if (isZipData) {
            result = createCompressedOutput(result, bufferSize);
        }

        return result;
    }

    private static void encodeDailySpeedsToStream(final PremiumProfileBlobData blobData, final Header header,
                                                  final DataOutputStream outStream) throws IOException {
        short lastSpeed = 0;
        for (int day : ALL_DAYS) {
            if (header.isRelevantDay(day)) {
                // At this point we know that the speeds accessor exists as the day is considered relevant:
                PremiumProfileBlobData.DaySpeedsAccessor daySpeeds = blobData.getDaySpeeds(day).
                        orElseThrow(() -> new IllegalStateException("Bug in "
                                + PremiumProfileBlobConverter.class.getSimpleName()));
                try {
                    lastSpeed = encodeSpeedsForDay(outStream, daySpeeds, lastSpeed);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(String.format("For day %d: %s", day, e.getMessage()));
                }
            }
        }
    }

    private static short encodeSpeedsForDay(final DataOutputStream outStream,
                                            final PremiumProfileBlobData.DaySpeedsAccessor daySpeeds,
                                            final short lastSpeed) throws IOException {
        short previous = lastSpeed;
        short current = previous;
        for (int bin = 0; bin < daySpeeds.getTotalBins(); ++bin) {
            double binSpeed = daySpeeds.getSpeedByBin(bin);
            assertCompatibleSpeed(binSpeed, bin);
            current = FlexSpeedEncoding.encode(binSpeed);
            VarIntEncoding.encode(ZigZagEncoding.encode16((short) (current - previous)), outStream);
            previous = current;
        }
        return current;
    }

    private static void assertCompatibleSpeed(final double binSpeed, final int bin) {
        if (binSpeed < 0) {
            throw new IllegalArgumentException(String.format("Negative speed at bin %d: %f", bin, binSpeed));
        }
        if (binSpeed > MAX_SPEED_VALUE) {
            throw new IllegalArgumentException(String.format("Can't write speed: %f", binSpeed));
        }
    }

    private static byte[] createCompressedOutput(final byte[] unzippedBytes, final int bufferSize) throws IOException {
        ByteArrayOutputStream compressedStream = new ByteArrayOutputStream(bufferSize);
        Deflater deflater = new Deflater(COMPRESSION_LEVEL);
        try (OutputStream targetStream = new DeflaterOutputStream(compressedStream, deflater, bufferSize)) {
            targetStream.write(unzippedBytes);
        } finally {
            // When passing an external deflater to DeflaterOutputStream, it is not automatically "ended" on close.
            // Will be done in finalize(), but do this manually in order to prevent native memory exhaustion.
            // See also DeflaterOutputStream.close().
            deflater.end();
        }
        return compressedStream.toByteArray();
    }

    private static class Header {
        private static final int OUT_TIME_RESOLUTION_FOR_24H = 0;
        private final int timeResolution;
        private final byte daysBitSet;

        Header(int timeResolution, byte daysBitSet) {
            this.timeResolution = timeResolution;
            this.daysBitSet = daysBitSet;
        }

        @SuppressWarnings("SameParameterValue")
        static HeaderAndIndex decode(final byte[] blob, final int index) {
            int currIndex = index;
            int outTimeResolution = Byte.toUnsignedInt(blob[currIndex++]);
            int actualTimeResolution =
                    (outTimeResolution == OUT_TIME_RESOLUTION_FOR_24H) ? MINUTES_PER_DAY : outTimeResolution;
            byte daysBitSet = blob[currIndex++];
            return new HeaderAndIndex(new Header(actualTimeResolution, daysBitSet), currIndex);
        }

        byte[] encode() {
            int outResolution = (timeResolution == MINUTES_PER_DAY) ? OUT_TIME_RESOLUTION_FOR_24H : timeResolution;
            if (outResolution > 255) {
                throw new IllegalArgumentException(String.format(
                        "Time resolution '%d' is too big to be written into one byte", timeResolution));
            }
            return new byte[]{(byte) outResolution, daysBitSet};
        }

        boolean isRelevantDay(final int day) {
            byte dayMask = getDayMask(day);
            return (dayMask & daysBitSet) != 0;
        }

        static byte getDayMask(final int day) {
            byte day0Mask = 0x01;
            return (byte) (day0Mask << day);
        }
    }

    private static class HeaderAndIndex {
        private final Header header;
        private final int index;

        HeaderAndIndex(final Header header, final int index) {
            this.header = header;
            this.index = index;
        }
    }
}
