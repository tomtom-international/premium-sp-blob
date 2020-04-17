# Premium Speed Profiles Blob
Copyright (C) 2020, TomTom NV. All rights reserved.

A library that converts Premium Speed-Profile binary blob data into and from an object.

Premium Speed-Profiles for a single directed segment are stored in a `PremiumProfileBlobData`
object. This object provides access to (integral) working-day and weekend average speeds and 
optionally also contains speeds for multiple time-periods per day for up to 7 days of the week.

The data can be converted from and into an efficiently compressed binary format using the 
`PremiumProfileBlobConverter`. The provided conversion methods take a `PremiumProfileBlobData`
object and create a `byte[]` array or vice versa.

## Installation

Download the code to build a java-library using maven via `mvn clean install` or directly 
integrate the code into your projects.

## Usage

The following example initializes a `PremiumProfileBlobData` object with binned speeds for each 
day of the week. It uses a temporal resolution of 4 hours, i.e. a day is divided into 6 time 
periods. (For simplicity, each day is assigned the exact same average speeds per period. This is 
for demonstration purposes only and not typically the case in practice.)

```
    // Average speed for week days
    int weekdaySpeed = 48;
    // Average speed for weekends
    int weekendSpeed = 52;

    // 4 hour resolution, 240 minutes
    int binWidthMinutes = 4 * 60;

    // Period from [0 to 4[
    double nightSpeed = 60.0;
    // Period from [4 to 8[
    double morningSpeed = 40.0;
    // Period from [8 to 12[
    double noonSpeed = 45.0;
    // Period from [12 to 16[
    double afternoonSpeed = 50.0;
    // Period from [16 to 20[
    double eveningSpeed = 45.0;
    // Period from [20 to 24[
    double lateEveningSpeed = 50.0;

    // The speeds for a single day
    double[] daySpeeds = { nightSpeed, morningSpeed, noonSpeed, afternoonSpeed, eveningSpeed, lateEveningSpeed };

    // Initialize the speeds for a whole week of 7 days
    double[][] inputSpeeds = new double[7][];
    for (int day = 0; day < 7; ++day) {
        inputSpeeds[day] = daySpeeds;
    }

    // Initialize blob data
    PremiumProfileBlobData blobData = new PremiumProfileBlobData(weekdaySpeed, weekendSpeed,
            PremiumProfileBlobData.arrayToSpeedsAccessor(inputSpeeds), binWidthMinutes);
```

The data inside the blob data object can easily be converted into the highly compressed binary byte array:

```
    PremiumProfileBlobConverter pspBlobConverter = new PremiumProfileBlobConverter();
    byte[] binaryBlob = pspBlobConverter.toBinaryBlob(blobData);

```

Similarly, the binary blob data can be decoded back into a `PremiumProfileBlobData` object.

```
    PremiumProfileBlobData convertedBlobData = pspBlobConverter.fromBinaryBlob(binaryBlob);
```

Several accessors allow to infer the contained speed information from the blob data object.

```
    System.out.println("Resolution: " + blobData.getTimeResolutionMinutes());
    System.out.println("Week Day Speed: " + blobData.getWeekDaySpeed());
    System.out.println("Week End Speed: " + blobData.getWeekendSpeed());
    for (int day = 0; day < 7; ++day) {
        System.out.println("Day " + day + " Speeds: " + Arrays.toString(blobData.getDaySpeedsAsArray(day).orElse(new double[] {})));
    }

```

## License

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
