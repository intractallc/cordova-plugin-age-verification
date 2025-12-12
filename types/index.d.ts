/**
 * TypeScript definitions for cordova-plugin-age-verification
 * Cross-platform age verification using Apple DeclaredAgeRange API (iOS) and Google Play Age Signals API (Android)
 */

declare namespace AgeVerification {
    /**
     * Age range response when user shares their age
     */
    interface AgeRangeResult {
        /** Status of the age range request */
        status: 'shared' | 'declined' | 'pending' | 'unknown';
        /** Whether the user shared their age */
        shared: boolean;
        /** Lower bound of the age range (null if no lower bound) */
        lowerBound: number | null;
        /** Upper bound of the age range (null if no upper bound) */
        upperBound: number | null;
        /** Source of the age information (iOS: selfDeclared/guardianDeclared, Android: verified/supervised) */
        source: 'selfDeclared' | 'guardianDeclared' | 'verified' | 'supervised' | 'unknown' | null;
        /** Active parental controls (iOS only, empty array on Android) */
        parentalControls: ('communicationLimits' | 'screenTime' | 'contentRestrictions')[];
    }

    /**
     * Result from isUserAboveAge check
     */
    interface AgeCheckResult {
        /** Whether the user is at or above the minimum age */
        isAboveAge: boolean;
        /** Whether the user declined to share their age */
        declined: boolean;
        /** The minimum age that was checked */
        minimumAge: number;
        /** Lower bound of the user's age range */
        lowerBound: number | null;
        /** Upper bound of the user's age range */
        upperBound: number | null;
        /** Whether the status is unknown (Android only) */
        unknown?: boolean;
    }

    /**
     * iOS Platform information
     */
    interface IOSPlatformInfo {
        /** Platform name */
        platform: 'ios';
        /** iOS system version */
        systemVersion: string;
        /** Whether iOS 26.0+ requirement is met */
        minimumVersionMet: boolean;
        /** Required iOS version */
        requiredVersion: string;
        /** Whether the DeclaredAgeRange framework is available */
        frameworkAvailable: boolean;
        /** Whether the API is available */
        apiAvailable: boolean;
    }

    /**
     * Android Platform information
     */
    interface AndroidPlatformInfo {
        /** Platform name */
        platform: 'android';
        /** Android version string */
        systemVersion: string;
        /** Android SDK version number */
        sdkVersion: number;
        /** Required SDK version (23) */
        requiredSdkVersion: number;
        /** Whether minimum SDK version is met */
        minimumVersionMet: boolean;
        /** Whether the API is available */
        apiAvailable: boolean;
    }

    type PlatformInfo = IOSPlatformInfo | AndroidPlatformInfo;

    /**
     * Android-specific age signals result with additional fields
     */
    interface AgeSignalsResult extends AgeRangeResult {
        /** Android-specific user verification status */
        userStatus: 'verified' | 'supervised' | 'supervised_approval_pending' | 'supervised_approval_denied' | 'unknown';
        /** Play-generated install ID for supervised installs */
        installId: string | null;
        /** Date of most recent parental approval */
        mostRecentApprovalDate: string | null;
    }

    /**
     * Error response
     */
    interface ErrorResult {
        /** Error code */
        error:
            // Common errors
            | 'unsupported'
            | 'invalid_arguments'
            | 'invalid_request'
            | 'not_available'
            | 'unknown'
            | 'request_failed'
            | 'parse_error'
            // Android-specific errors
            | 'api_not_available'
            | 'play_store_not_found'
            | 'network_error'
            | 'play_services_not_found'
            | 'service_binding_failed'
            | 'play_store_outdated'
            | 'play_services_outdated'
            | 'transient_error'
            | 'app_not_owned'
            | 'internal_error';
        /** Human-readable error message */
        message: string;
        /** Whether the error is retryable (Android only) */
        retryable?: boolean;
    }

    /**
     * Common age gate values
     */
    interface AgeGates {
        KIDS: 13;
        TEENS: 16;
        ADULTS: 18;
        ALCOHOL_US: 21;
    }

    /**
     * Predefined age gate combinations
     */
    interface StandardGates {
        FULL: [13, 16, 18];
        ADULT_ONLY: [18];
        TEEN_ADULT: [13, 18];
        US_STATE_COMPLIANCE: [13, 16, 18];
    }

    /**
     * User status constants (primarily for Android)
     */
    interface UserStatus {
        VERIFIED: 'verified';
        SUPERVISED: 'supervised';
        SUPERVISED_APPROVAL_PENDING: 'supervised_approval_pending';
        SUPERVISED_APPROVAL_DENIED: 'supervised_approval_denied';
        UNKNOWN: 'unknown';
    }
}

interface AgeVerificationPlugin {
    /**
     * Check if the age verification API is available on this device
     */
    isAvailable(
        successCallback: (available: boolean) => void,
        errorCallback: (error: AgeVerification.ErrorResult) => void
    ): void;

    /**
     * Request the user's age range with specified age gates
     * @param ageGates Array of 1-3 age thresholds
     */
    requestAgeRange(
        ageGates: number[],
        successCallback: (result: AgeVerification.AgeRangeResult) => void,
        errorCallback: (error: AgeVerification.ErrorResult) => void
    ): void;

    /**
     * Check if the user is at or above a specific age
     * @param minimumAge The minimum age to check against
     */
    isUserAboveAge(
        minimumAge: number,
        successCallback: (result: AgeVerification.AgeCheckResult) => void,
        errorCallback: (error: AgeVerification.ErrorResult) => void
    ): void;

    /**
     * Get platform and API availability information
     */
    getPlatformInfo(
        successCallback: (info: AgeVerification.PlatformInfo) => void,
        errorCallback: (error: AgeVerification.ErrorResult) => void
    ): void;

    /**
     * Android-specific: Get full age signals data including install ID and approval date
     * On iOS, this is equivalent to requestAgeRange with default age gates
     */
    checkAgeSignals(
        successCallback: (result: AgeVerification.AgeSignalsResult) => void,
        errorCallback: (error: AgeVerification.ErrorResult) => void
    ): void;

    /** Common age gate values */
    AGE_GATES: AgeVerification.AgeGates;

    /** Predefined age gate combinations */
    STANDARD_GATES: AgeVerification.StandardGates;

    /** User status constants (primarily for Android) */
    USER_STATUS: AgeVerification.UserStatus;
}

declare var AgeVerification: AgeVerificationPlugin;

export = AgeVerification;
export as namespace AgeVerification;
