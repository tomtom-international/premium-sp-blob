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

import java.util.Optional;
import java.util.function.IntFunction;

/**
 * Data structure for representing the speed information that is comprised inside a Premium Speed-Profile binary blob.
 *
 * A Premium-Speed-Profile (PSP) binary blob contains the following information about a single directed segment:
 *
 * <ul>
 * <li/> The two mean speeds for working-days and weekend-days.
 * <li/> Daily 24h-speed-profiles for up to 7 days of the week in a given temporal resolution
 * </ul>
 *
 * A binary blob - and hence the corresponding {@code PremiumProfileBlobData} object - might not contain a daily speed
 * profile for each day of the week or even no daily speed profiles at all. Information about the availability of daily
 * profiles as well as the temporal resolution of all potentially contained profile data can be inferred from the object
 * via a set of corresponding methods (see e.g. {@link #hasDailySpeeds()} and {@link #getTimeResolutionMinutes()}).
 *
 * Mean speed data is always provided and can be accessed via {@link #getWeekDaySpeed()} and {@link #getWeekendSpeed()}.
 *
 * {@code PremiumProfileBlobData} objects can be converted to/from binary blobs as using a
 * {@link PremiumProfileBlobConverter}.
 */
public class PremiumProfileBlobData {
    private final int weekDaySpeed;
    private final int weekendSpeed;
    private final int timeResolutionMinutes;
    private final IntFunction<DaySpeedsAccessor> dataAccessor;

    /**
     * @return the mean speed over all working-days of the week in km/h as an integer value.
     */
    public int getWeekDaySpeed() {
        return weekDaySpeed;
    }

    /**
     * @return the mean speed over all weekend-days in km/h as an integer value.
     */
    public int getWeekendSpeed() {
        return weekendSpeed;
    }

    /**
     * @return the temporal resolution in minutes of the Premium Speed-Profiles in this object.
     * E.g. a value of 30 means that the speeds within each daily profile correspond to the following day-time-intervals:
     * 0:00h-0:30h, 0:30h-1:00h, 1:00h-1:30h, 1:30h-2:00h, etc.
     * The time-periods always cover the entire 24h of the day, starting at 0:00h.
     * All contained daily profiles exhibit the same temporal resolution.
     */
    public int getTimeResolutionMinutes() {
        return timeResolutionMinutes;
    }

    /**
     * @return whether daily Premium Speed-Profiles are available for at least one day (true) or not (false).
     *
     * See also {@link #hasDaySpeeds(int)}.
     */
    public boolean hasDailySpeeds() {
        return dataAccessor != null;
    }

    /**
     * Specifies whether a daily Premium Speed-Profile is available for a given day.
     *
     * @param day  the index of the day for which information about availability of Premium Speed-Profiles is desired.
     *          The allowed index values range from 0 to 6: 0 for Sunday, ..., 6 for Saturday. Note the difference to
     *          {@link java.time.DayOfWeek}.
     * @return whether a daily Premium Speed-Profile is available for the specified day (true) or not (false).
     */
    public boolean hasDaySpeeds(final int day) {
        return dataAccessor != null && isNonEmptyAccessor(dataAccessor.apply(day));
    }

    /**
     * Returns the Premium Speed-Profile data for the specified day (if available).
     *
     * @param day  index value from 0 to 6: 0 for Sunday, ..., 6 for Saturday. Note the difference to
     *          {@link java.time.DayOfWeek}.
     * @return if a Premium Speed-Profile for the day exists, it is returned as a double array, where each value
     * represents the speed for the corresponding time period in km/h. The duration of each time period is given by
     * {@link #getTimeResolutionMinutes()} and the periods always cover the entire 24h of the day.
     * The first value is for 0:00, the second is for (0:00 + {@code getTimeResolutionMinutes()}), etc.
     */
    public Optional<double[]> getDaySpeedsAsArray(final int day) {
        if (dataAccessor != null) {
            DaySpeedsAccessor daySpeedsAccessor = dataAccessor.apply(day);
            double[] result = null;
            if (isNonEmptyAccessor(daySpeedsAccessor)) {
                result = new double[daySpeedsAccessor.getTotalBins()];
                for (int bin = 0; bin < result.length; ++bin) {
                    result[bin] = daySpeedsAccessor.getSpeedByBin(bin);
                }
            }
            return Optional.ofNullable(result);
        }
        return Optional.empty();
    }

    //----------------------------------------------------------------------------------
    //----------------- for writing and other TomTom usage -----------------------------

    /**
     * The {@code DaySpeedsAccessor} interface is used in order to allow the speed-profile data to originate from different sources.
     *
     * A single {@code DaySpeedsAccessor} provides the time-bin-wise speeds of a profile for one specific day of the
     * week.
     *
     * An implementation providing access to data stored in double arrays is available
     * (see {@link #arrayToSpeedsAccessor(double[][])}). Provide additional implementations if data stored in other
     * forms shall be processed.
     */
    public interface DaySpeedsAccessor {

        /**
         * @return the number of speed-profile time bins per day
         */
        int getTotalBins();

        /**
         * Returns the profile-speed in km/h at the time-bin of the provided index.
         *
         * @param bin  index of the time-bin for which the speed shall be returned
         * @return the profile-speed in km/h at the time-bin with the provided index
         */
        double getSpeedByBin(final int bin);
    }

    /**
     * Representation of a 2D-array as day-to-{@link DaySpeedsAccessor}.
     *
     * Implement similarly for other underlying data types.
     *
     * @param dayToSpeeds  array: day to time-bin to speed. The assumed indexing of the days is: 0 for Sunday, ...,
     *          6 for Saturday. (Note the difference to {@link java.time.DayOfWeek}.)
     * @return a thin wrapper around the array
     */
    public static IntFunction<DaySpeedsAccessor> arrayToSpeedsAccessor(final double[][] dayToSpeeds) {
        return day -> {
            double[] daySpeeds = dayToSpeeds[day];
            if (daySpeeds != null && daySpeeds.length > 0) {
                return new PremiumProfileBlobData.DaySpeedsAccessor() {
                    @Override
                    public int getTotalBins() {
                        return daySpeeds.length;
                    }

                    @Override
                    public double getSpeedByBin(final int bin) {
                        return daySpeeds[bin];
                    }
                };
            } else {
                return null;
            }
        };
    }

    /**
     * Constructor for {@code PremiumProfileBlobData} objects providing both mean speeds as well as daily speed profiles.
     *
     * The daily time-bin-wise speed values are provided in form of an {@code IntFunction<DaySpeedsAccessor>}
     * (see {@link DaySpeedsAccessor}) that allows to access speed profiles for single days based on their index. For
     * creating a {@code PremiumProfileBlobData} from arrays for the daily speeds, see
     * {@link #arrayToSpeedsAccessor(double[][])} or use the alternative constructor.
     *
     * @param weekDaySpeed  the mean speed for working-days in km/h as an integer value
     * @param weekendSpeed  the mean speed for weekend-days in km/h as an integer value
     * @param dataAccessor  function that returns daily speeds for specific days of the week given by the corresponding
     *          index in form of a {@code DaySpeedsAccessor}
     * @param timeResolutionMinutes  the width of the time-bins in minutes for which speed values are provided.
     *          Note that a valid {@code PremiumProfileBlobData} is expected to contain as many speed values per day
     *          such that assuming this time-span, the entire 24h of this day are covered.
     */
    public PremiumProfileBlobData(
            int weekDaySpeed,
            int weekendSpeed,
            IntFunction<DaySpeedsAccessor> dataAccessor,
            int timeResolutionMinutes) {
        this.weekDaySpeed = weekDaySpeed;
        this.weekendSpeed = weekendSpeed;
        this.dataAccessor = dataAccessor;
        this.timeResolutionMinutes = timeResolutionMinutes;
    }

    /**
     * Constructor for {@code PremiumProfileBlobData} objects that include only mean speeds but no daily profiles.
     *
     * The resulting object will always return false when calling {@link #hasDailySpeeds()} or {@link #hasDaySpeeds(int)}
     * for any index. {@link #getTimeResolutionMinutes()} will always return 0.
     *
     * @param weekDaySpeed  the mean speed for working-days in km/h as an integer value
     * @param weekendSpeed  the mean speed for weekend-days in km/h as an integer value
     */
    public PremiumProfileBlobData(final int weekDaySpeed, final int weekendSpeed) {
        this(weekDaySpeed, weekendSpeed, (IntFunction<DaySpeedsAccessor>) null, 0);
    }

    /**
     * Convenience Constructor for {@code PremiumProfileBlobData} objects providing both mean speeds as well as daily speed profiles.
     *
     * The daily time-bin-wise speed values are provided in form of a two-dimensional {@code double} array, where the
     * first index specifies the day of the week (compare e.g. {@link #getDaySpeedsAsArray(int)} and the second index
     * specifies the time-bin. Internally uses {@link #arrayToSpeedsAccessor(double[][])} to construct a
     * {@code PremiumProfileBlobData} object.
     *
     * @param weekDaySpeed  the mean speed for working-days in km/h as an integer value
     * @param weekendSpeed  the mean speed for weekend-days in km/h as an integer value
     * @param speedProfileData  two-dimensional {@code double} array providing the speeds per day and time bin in km/h
     * @param timeResolutionMinutes the width of the time-bins in minutes for which speed values are provided. Note
     *          that a valid {@code PremiumProfileBlobData} is expected to contain as many speed values per day such
     *          that assuming this time-span, the entire 24h of this day are covered.
     */
    public PremiumProfileBlobData(
            final int weekDaySpeed,
            final int weekendSpeed,
            final double[][] speedProfileData,
            final int timeResolutionMinutes) {
        this(weekDaySpeed, weekendSpeed, arrayToSpeedsAccessor(speedProfileData), timeResolutionMinutes);
    }

    public Optional<DaySpeedsAccessor> getDaySpeeds(final int day) {
        if (dataAccessor != null) {
            DaySpeedsAccessor daySpeeds = dataAccessor.apply(day);
            return isNonEmptyAccessor(daySpeeds) ? Optional.of(daySpeeds) : Optional.empty();
        }
        return Optional.empty();
    }

    private static boolean isNonEmptyAccessor(final DaySpeedsAccessor daySpeeds) {
        return daySpeeds != null && daySpeeds.getTotalBins() > 0;
    }

}
