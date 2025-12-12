/**
 * Age Verification Plugin
 *
 * Cross-platform Cordova plugin for age verification:
 * - iOS: Apple's DeclaredAgeRange API (iOS 26+)
 * - Android: Google Play Age Signals API
 *
 * Provides age verification functionality while preserving user privacy.
 */

var exec = require('cordova/exec');

// Constants for age validation
var MIN_AGE = 1;
var MAX_AGE = 150;

var AgeVerification = {

    /**
     * Check if the age verification API is available on this device
     *
     * @param {Function} successCallback - Called with boolean (true if available)
     * @param {Function} errorCallback - Called on error
     *
     * @example
     * AgeVerification.isAvailable(
     *     function(available) {
     *         console.log('Age verification available:', available);
     *     },
     *     function(error) {
     *         console.error('Error:', error);
     *     }
     * );
     */
    isAvailable: function(successCallback, errorCallback) {
        exec(
            function(result) {
                // Normalize result to boolean (Android returns 1/0)
                successCallback(result === true || result === 1);
            },
            errorCallback,
            'AgeVerification',
            'isAvailable',
            []
        );
    },

    /**
     * Request the user's age range with specified age gates
     *
     * @param {number[]} ageGates - Array of 1-3 age thresholds (e.g., [13, 16, 18])
     * @param {Function} successCallback - Called with age range result object
     * @param {Function} errorCallback - Called on error
     *
     * Result object structure:
     * {
     *     status: 'shared' | 'declined' | 'pending' | 'unknown',
     *     shared: boolean,
     *     lowerBound: number | null,  // Lower bound of age range
     *     upperBound: number | null,  // Upper bound of age range
     *     source: string | null,       // iOS: 'selfDeclared'|'guardianDeclared', Android: 'verified'|'supervised'
     *     parentalControls: string[]   // Active parental controls (iOS only)
     * }
     *
     * @example
     * AgeVerification.requestAgeRange(
     *     [13, 16, 18],
     *     function(result) {
     *         if (result.shared) {
     *             console.log('Age range:', result.lowerBound, '-', result.upperBound);
     *             console.log('Source:', result.source);
     *         } else {
     *             console.log('User declined to share age');
     *         }
     *     },
     *     function(error) {
     *         console.error('Error:', error.message);
     *     }
     * );
     */
    requestAgeRange: function(ageGates, successCallback, errorCallback) {
        if (!Array.isArray(ageGates) || ageGates.length === 0 || ageGates.length > 3) {
            if (errorCallback) {
                errorCallback({
                    error: 'invalid_arguments',
                    message: 'ageGates must be an array of 1-3 integers'
                });
            }
            return;
        }

        // Validate all gates are numbers within valid range
        for (var i = 0; i < ageGates.length; i++) {
            if (typeof ageGates[i] !== 'number' || !Number.isInteger(ageGates[i])) {
                if (errorCallback) {
                    errorCallback({
                        error: 'invalid_arguments',
                        message: 'All age gates must be integers'
                    });
                }
                return;
            }
            if (ageGates[i] < MIN_AGE || ageGates[i] > MAX_AGE) {
                if (errorCallback) {
                    errorCallback({
                        error: 'invalid_arguments',
                        message: 'Age gates must be between ' + MIN_AGE + ' and ' + MAX_AGE
                    });
                }
                return;
            }
        }

        exec(successCallback, errorCallback, 'AgeVerification', 'requestAgeRange', [ageGates]);
    },

    /**
     * Check if the user is at or above a specific age
     * Convenience method for simple age gating
     *
     * @param {number} minimumAge - The minimum age to check against
     * @param {Function} successCallback - Called with result object
     * @param {Function} errorCallback - Called on error
     *
     * Result object structure:
     * {
     *     isAboveAge: boolean,
     *     declined: boolean,
     *     minimumAge: number,
     *     lowerBound: number | null,
     *     upperBound: number | null
     * }
     *
     * @example
     * AgeVerification.isUserAboveAge(
     *     18,
     *     function(result) {
     *         if (result.declined) {
     *             console.log('User declined age verification');
     *         } else if (result.isAboveAge) {
     *             console.log('User is 18 or older');
     *         } else {
     *             console.log('User is under 18');
     *         }
     *     },
     *     function(error) {
     *         console.error('Error:', error.message);
     *     }
     * );
     */
    isUserAboveAge: function(minimumAge, successCallback, errorCallback) {
        if (typeof minimumAge !== 'number' || !Number.isInteger(minimumAge)) {
            if (errorCallback) {
                errorCallback({
                    error: 'invalid_arguments',
                    message: 'minimumAge must be an integer'
                });
            }
            return;
        }

        if (minimumAge < MIN_AGE || minimumAge > MAX_AGE) {
            if (errorCallback) {
                errorCallback({
                    error: 'invalid_arguments',
                    message: 'minimumAge must be between ' + MIN_AGE + ' and ' + MAX_AGE
                });
            }
            return;
        }

        exec(successCallback, errorCallback, 'AgeVerification', 'isUserAboveAge', [minimumAge]);
    },

    /**
     * Get platform and API availability information
     *
     * @param {Function} successCallback - Called with platform info object
     * @param {Function} errorCallback - Called on error
     *
     * Result object structure (iOS):
     * {
     *     platform: 'ios',
     *     systemVersion: string,
     *     minimumVersionMet: boolean,
     *     requiredVersion: '26.0',
     *     frameworkAvailable: boolean,
     *     apiAvailable: boolean
     * }
     *
     * Result object structure (Android):
     * {
     *     platform: 'android',
     *     systemVersion: string,
     *     sdkVersion: number,
     *     requiredSdkVersion: 23,
     *     minimumVersionMet: boolean,
     *     apiAvailable: boolean
     * }
     *
     * @example
     * AgeVerification.getPlatformInfo(
     *     function(info) {
     *         console.log('Platform:', info.platform);
     *         console.log('API available:', info.apiAvailable);
     *     },
     *     function(error) {
     *         console.error('Error:', error);
     *     }
     * );
     */
    getPlatformInfo: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'AgeVerification', 'getPlatformInfo', []);
    },

    /**
     * Android-specific: Get full age signals data including install ID and approval date
     * On iOS, this is equivalent to requestAgeRange with default age gates
     *
     * @param {Function} successCallback - Called with full age signals result
     * @param {Function} errorCallback - Called on error
     *
     * Android-specific result fields:
     * {
     *     ...standard fields...,
     *     userStatus: 'verified' | 'supervised' | 'supervised_approval_pending' | 'supervised_approval_denied' | 'unknown',
     *     installId: string | null,
     *     mostRecentApprovalDate: string | null
     * }
     *
     * @example
     * AgeVerification.checkAgeSignals(
     *     function(result) {
     *         console.log('User status:', result.userStatus);
     *         if (result.installId) {
     *             console.log('Install ID:', result.installId);
     *         }
     *     },
     *     function(error) {
     *         console.error('Error:', error.message);
     *     }
     * );
     */
    checkAgeSignals: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, 'AgeVerification', 'checkAgeSignals', []);
    },

    // Convenience constants for common age gates
    AGE_GATES: {
        KIDS: 13,
        TEENS: 16,
        ADULTS: 18,
        ALCOHOL_US: 21
    },

    // Standard age gate combinations
    STANDARD_GATES: {
        // For apps with kids, teens, and adult content
        FULL: [13, 16, 18],
        // For apps that just need adult verification
        ADULT_ONLY: [18],
        // For apps with teen and adult content
        TEEN_ADULT: [13, 18],
        // Texas SB2420 / Utah / Louisiana compliance categories
        US_STATE_COMPLIANCE: [13, 16, 18]
    },

    // User status constants (primarily for Android)
    USER_STATUS: {
        VERIFIED: 'verified',
        SUPERVISED: 'supervised',
        SUPERVISED_APPROVAL_PENDING: 'supervised_approval_pending',
        SUPERVISED_APPROVAL_DENIED: 'supervised_approval_denied',
        UNKNOWN: 'unknown'
    }
};

module.exports = AgeVerification;
