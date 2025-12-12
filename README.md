# Cordova Plugin: Age Verification

A cross-platform Cordova plugin for privacy-preserving age verification:
- **iOS**: Apple's DeclaredAgeRange API (iOS 26+)
- **Android**: Google Play Age Signals API

## Overview

This plugin provides a unified API for age verification across both major mobile platforms. It allows apps to deliver age-appropriate experiences without collecting exact birthdates, protecting user privacy while ensuring compliance with regulations like Texas SB2420, Utah SB287, and Louisiana Act 440.

## Features

- Privacy-preserving age verification (no exact birthdate collection)
- Cross-platform unified JavaScript API
- Support for multiple age gates (up to 3 thresholds)
- Detection of parental control/supervision status
- Source identification (self-declared, guardian-declared, verified, supervised)
- Compliance support for US state age verification laws
- TypeScript definitions included

## Requirements

### iOS
- **iOS 26.0 or later** (macOS Tahoe for development)
- **Xcode 26 Beta** or later
- **Apple Developer Program membership** (for the required entitlement)

### Android
- **Android 6.0 (API level 23)** or later
- **Google Play Services**
- **App distributed via Google Play Store**

### Cordova
- **Cordova iOS 7.0.0** or later
- **Cordova Android 10.0.0** or later

## Dependencies

This plugin automatically installs:
- [`cordova-plugin-add-swift-support`](https://www.npmjs.com/package/cordova-plugin-add-swift-support) - Adds Swift 5.0 support (iOS only)

## Installation

### From Local Path

```bash
cordova plugin add ./cordova-plugin-age-verification
```

### From GitHub

```bash
cordova plugin add https://github.com/intractallc/cordova-plugin-age-verification.git
```

## Platform Setup

### iOS Setup

#### 1. Enable the Entitlement in Xcode

After adding the plugin, enable the DeclaredAgeRange capability in Xcode:

1. Open your project in Xcode (`platforms/ios/YourApp.xcworkspace`)
2. Select your app target
3. Go to **Signing & Capabilities**
4. Click **+ Capability**
5. Add **Declared Age Range**

The plugin attempts to add this automatically via entitlements files, but manual verification is recommended.

#### 2. Set Minimum Deployment Target

Ensure your app targets iOS 26.0 or later in `config.xml`:

```xml
<platform name="ios">
    <preference name="deployment-target" value="26.0" />
</platform>
```

### Android Setup

#### 1. Configure Age Ranges in Play Console (Optional)

You can customize the age ranges returned by the API:

1. Go to **Google Play Console** > Your App
2. Navigate to **Policy and programs** > **Age signals**
3. Select **Custom age ranges** tab
4. Enter up to three minimum ages (must be ≥2 years apart)

Default age ranges: 0-12, 13-15, 16-17, 18+

#### 2. Testing

Use the `FakeAgeSignalsManager` for testing. See the [Google Play documentation](https://developer.android.com/google/play/age-signals/test-age-signals-api) for details.

**Important**: The Play Age Signals API only returns data for users in regions where Play is legally required to provide age category data (currently Texas, Utah, and Louisiana starting in 2026).

## Usage

### Check Availability

```javascript
AgeVerification.isAvailable(
    function(available) {
        if (available) {
            console.log('Age verification is available');
        } else {
            console.log('Age verification not available on this device');
        }
    },
    function(error) {
        console.error('Error checking availability:', error);
    }
);
```

### Request Age Range

Request the user's age range with custom age gates:

```javascript
// Request with multiple age gates (creates ranges: <13, 13-15, 16-17, 18+)
AgeVerification.requestAgeRange(
    [13, 16, 18],
    function(result) {
        if (result.shared) {
            console.log('Age range:', result.lowerBound, '-', result.upperBound);
            console.log('Source:', result.source);
        } else {
            console.log('User declined to share age');
        }
    },
    function(error) {
        console.error('Error:', error.message);
    }
);
```

### Simple Age Check

For straightforward "is user above X age" checks:

```javascript
// Check if user is 18 or older
AgeVerification.isUserAboveAge(
    18,
    function(result) {
        if (result.declined) {
            console.log('User declined age verification');
        } else if (result.isAboveAge) {
            console.log('User is 18 or older');
        } else {
            console.log('User is under 18');
        }
    },
    function(error) {
        console.error('Error:', error.message);
    }
);
```

### Using Predefined Age Gates

```javascript
// Using predefined gates
AgeVerification.requestAgeRange(
    AgeVerification.STANDARD_GATES.FULL, // [13, 16, 18]
    successCallback,
    errorCallback
);

// Available constants:
AgeVerification.AGE_GATES.KIDS          // 13
AgeVerification.AGE_GATES.TEENS         // 16
AgeVerification.AGE_GATES.ADULTS        // 18
AgeVerification.AGE_GATES.ALCOHOL_US    // 21

// Predefined combinations:
AgeVerification.STANDARD_GATES.FULL              // [13, 16, 18]
AgeVerification.STANDARD_GATES.ADULT_ONLY        // [18]
AgeVerification.STANDARD_GATES.TEEN_ADULT        // [13, 18]
AgeVerification.STANDARD_GATES.US_STATE_COMPLIANCE // [13, 16, 18]
```

### Get Platform Info

```javascript
AgeVerification.getPlatformInfo(
    function(info) {
        console.log('Platform:', info.platform);
        console.log('API available:', info.apiAvailable);

        if (info.platform === 'ios') {
            console.log('iOS version:', info.systemVersion);
            console.log('Framework available:', info.frameworkAvailable);
        } else {
            console.log('Android SDK:', info.sdkVersion);
        }
    },
    function(error) {
        console.error('Error:', error);
    }
);
```

### Android-Specific: Full Age Signals

```javascript
// Get full age signals data (Android-specific fields)
AgeVerification.checkAgeSignals(
    function(result) {
        console.log('User status:', result.userStatus);
        console.log('Age range:', result.lowerBound, '-', result.upperBound);

        if (result.installId) {
            console.log('Install ID:', result.installId);
        }
        if (result.mostRecentApprovalDate) {
            console.log('Last approval:', result.mostRecentApprovalDate);
        }
    },
    function(error) {
        console.error('Error:', error.message);
        if (error.retryable) {
            console.log('This error is retryable');
        }
    }
);
```

## API Reference

### `isAvailable(successCallback, errorCallback)`

Checks if the age verification API is available on the current device.

**Success Response:** `boolean`

---

### `requestAgeRange(ageGates, successCallback, errorCallback)`

Requests the user's age range based on specified age thresholds.

**Parameters:**
- `ageGates`: `number[]` - Array of 1-3 age thresholds

**Success Response:**
```typescript
{
    status: 'shared' | 'declined' | 'pending' | 'unknown',
    shared: boolean,
    lowerBound: number | null,
    upperBound: number | null,
    source: string | null,
    parentalControls: string[]  // iOS only
}
```

---

### `isUserAboveAge(minimumAge, successCallback, errorCallback)`

Convenience method to check if user meets a minimum age requirement.

**Parameters:**
- `minimumAge`: `number` - The minimum age to check

**Success Response:**
```typescript
{
    isAboveAge: boolean,
    declined: boolean,
    minimumAge: number,
    lowerBound: number | null,
    upperBound: number | null
}
```

---

### `getPlatformInfo(successCallback, errorCallback)`

Returns platform and API availability information.

**Success Response (iOS):**
```typescript
{
    platform: 'ios',
    systemVersion: string,
    minimumVersionMet: boolean,
    requiredVersion: '26.0',
    frameworkAvailable: boolean,
    apiAvailable: boolean
}
```

**Success Response (Android):**
```typescript
{
    platform: 'android',
    systemVersion: string,
    sdkVersion: number,
    requiredSdkVersion: 23,
    minimumVersionMet: boolean,
    apiAvailable: boolean
}
```

---

### `checkAgeSignals(successCallback, errorCallback)`

Android-specific method returning full age signals data.

**Success Response:**
```typescript
{
    // Standard fields...
    userStatus: 'verified' | 'supervised' | 'supervised_approval_pending' | 'supervised_approval_denied' | 'unknown',
    installId: string | null,
    mostRecentApprovalDate: string | null
}
```

## Error Handling

Error callbacks receive an object with:

```typescript
{
    error: string,      // Error code
    message: string,    // Human-readable message
    retryable?: boolean // Whether to retry (Android only)
}
```

**Common Error Codes:**
- `unsupported` - Platform/version not supported
- `invalid_arguments` - Invalid parameters provided
- `invalid_request` - Age ranges don't meet requirements
- `not_available` - Service unavailable
- `unknown` - Unexpected error

**Android-Specific Error Codes:**
- `api_not_available` - Play Store app too old
- `play_store_not_found` - Play Store not installed
- `network_error` - No network connection
- `play_services_not_found` - Play Services unavailable
- `play_store_outdated` - Play Store needs update
- `play_services_outdated` - Play Services needs update
- `transient_error` - Temporary error, retry
- `app_not_owned` - App not installed from Google Play
- `internal_error` - Unknown internal error

## Complete Example

```javascript
document.addEventListener('deviceready', onDeviceReady, false);

function onDeviceReady() {
    initAgeVerification();
}

function initAgeVerification() {
    AgeVerification.isAvailable(
        function(available) {
            if (available) {
                performAgeVerification();
            } else {
                showFallbackAgeGate();
            }
        },
        function(error) {
            console.error('Availability check failed:', error);
            showFallbackAgeGate();
        }
    );
}

function performAgeVerification() {
    AgeVerification.requestAgeRange(
        [13, 16, 18],
        function(result) {
            if (result.status === 'declined') {
                restrictToKidsContent();
                return;
            }

            if (result.lowerBound >= 18) {
                enableFullContent();
            } else if (result.lowerBound >= 16) {
                enableTeenContent();
            } else if (result.lowerBound >= 13) {
                enableKidsContent();
            } else {
                restrictToKidsContent();
            }

            // iOS: Check parental controls
            if (result.parentalControls &&
                result.parentalControls.includes('communicationLimits')) {
                disableSocialFeatures();
            }
        },
        function(error) {
            console.error('Age verification failed:', error.message);

            // Android: Check if retryable
            if (error.retryable) {
                setTimeout(performAgeVerification, 5000);
            } else {
                restrictToKidsContent();
            }
        }
    );
}

function showFallbackAgeGate() {
    // Implement traditional age gate UI
}
```

## Compliance Notes

### US State Laws

Starting January 1, 2026, apps must comply with:
- **Texas SB2420** - App Store Accountability Act
- **Utah SB287** - Takes effect May 7, 2026
- **Louisiana Act 440** - Takes effect July 1, 2026

This plugin helps meet these requirements by:
- Providing age range verification
- Supporting mandated age categories: under 13, 13-15, 16-17, over 18
- Integrating with platform parental consent systems

### Privacy Considerations

- Both APIs only share age **ranges**, never exact birthdates
- Users can decline to share their age
- iOS: Age from Apple ID (self-declared or guardian-declared)
- Android: Age verified through Google Play
- Neither API can detect falsified age information

### Limitations

- **iOS**: Requires iOS 26.0+ (released 2025)
- **Android**: Only returns data in regions with legal requirements
- Cannot replace high-assurance verification (face-based checks)
- Relies on accuracy of platform age information

## Troubleshooting

### iOS: "Framework not available" error

- Ensure you're running iOS 26.0 or later
- Verify the DeclaredAgeRange capability is added in Xcode
- Check entitlement configuration

### iOS: "Invalid request" error

- Ensure age gates create ranges of at least 2 years
- Maximum 3 age gates allowed

### iOS: Swift compilation errors

- Ensure `cordova-plugin-add-swift-support` is installed
- Verify Swift version is 5.0 or later
- Check bridging header configuration

### Android: "app_not_owned" error

- App must be installed from Google Play
- Test using Play's internal testing tracks

### Android: "play_store_outdated" error

- Update Google Play Store on device
- Use latest Play Age Signals library (0.0.2+)

### Android: No data returned

- Play Age Signals only returns data in Texas, Utah, and Louisiana
- Use `FakeAgeSignalsManager` for testing outside these regions

### Plugin not found

- Run `cordova prepare`
- Clean and rebuild in Xcode/Android Studio
- Verify plugin in `config.xml`

## License

MIT License - see LICENSE file for details.

## Resources

### iOS
- [Apple Developer Documentation - DeclaredAgeRange](https://developer.apple.com/documentation/declaredagerange/)
- [WWDC 2025: Deliver Age-Appropriate Experiences](https://developer.apple.com/videos/play/wwdc2025/299/)

### Android
- [Play Age Signals API Overview](https://developer.android.com/google/play/age-signals/overview)
- [Play Age Signals API Usage Guide](https://developer.android.com/google/play/age-signals/use-age-signals-api)
- [Testing Play Age Signals](https://developer.android.com/google/play/age-signals/test-age-signals-api)

### General
- [Apple Newsroom: Tools to Protect Kids Online](https://www.apple.com/newsroom/2025/06/apple-expands-tools-to-help-parents-protect-kids-and-teens-online/)
- [Google Play Age Verification Blog](https://android-developers.googleblog.com/2024/12/build-high-quality-enagaing-age-appropriate-apps.html)
- [Intracta LLC Development Team](https://intracta.com)