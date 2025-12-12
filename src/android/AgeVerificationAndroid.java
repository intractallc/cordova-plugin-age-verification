package com.anthropic.ageverification;

import android.os.Build;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.play.core.agesignals.AgeSignalsManager;
import com.google.android.play.core.agesignals.AgeSignalsManagerFactory;
import com.google.android.play.core.agesignals.AgeSignalsRequest;
import com.google.android.play.core.agesignals.AgeSignalsResult;
import com.google.android.play.core.agesignals.AgeSignalsVerificationStatus;
import com.google.android.play.core.agesignals.AgeSignalsException;

/**
 * Android implementation of Age Verification using Google Play Age Signals API
 */
public class AgeVerificationAndroid extends CordovaPlugin {

    private static final String TAG = "AgeVerificationAndroid";
    private static final int MIN_AGE = 1;
    private static final int MAX_AGE = 150;

    private AgeSignalsManager ageSignalsManager;
    private String initializationError;

    @Override
    protected void pluginInitialize() {
        super.pluginInitialize();
        try {
            ageSignalsManager = AgeSignalsManagerFactory.create(cordova.getActivity().getApplicationContext());
            initializationError = null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize AgeSignalsManager: " + e.getMessage());
            initializationError = e.getMessage();
            ageSignalsManager = null;
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        switch (action) {
            case "isAvailable":
                isAvailable(callbackContext);
                return true;
            case "requestAgeRange":
                requestAgeRange(args, callbackContext);
                return true;
            case "isUserAboveAge":
                isUserAboveAge(args, callbackContext);
                return true;
            case "getPlatformInfo":
                getPlatformInfo(callbackContext);
                return true;
            case "checkAgeSignals":
                checkAgeSignals(callbackContext);
                return true;
            default:
                callbackContext.error("Unknown action: " + action);
                return false;
        }
    }

    /**
     * Check if the Play Age Signals API is available
     */
    private void isAvailable(CallbackContext callbackContext) {
        boolean available = ageSignalsManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
        callbackContext.success(available ? 1 : 0);
    }

    /**
     * Validate age gates array
     */
    private boolean validateAgeGates(JSONArray ageGatesArray, CallbackContext callbackContext) throws JSONException {
        if (ageGatesArray == null || ageGatesArray.length() == 0 || ageGatesArray.length() > 3) {
            sendError(callbackContext, "invalid_arguments", "ageGates must be an array of 1-3 integers");
            return false;
        }

        for (int i = 0; i < ageGatesArray.length(); i++) {
            int age = ageGatesArray.getInt(i);
            if (age < MIN_AGE || age > MAX_AGE) {
                sendError(callbackContext, "invalid_arguments",
                    "Age gates must be between " + MIN_AGE + " and " + MAX_AGE);
                return false;
            }
        }

        return true;
    }

    /**
     * Convert JSONArray of age gates to int array
     */
    private int[] getAgeGatesFromArray(JSONArray ageGatesArray) throws JSONException {
        int[] ageGates = new int[ageGatesArray.length()];
        for (int i = 0; i < ageGatesArray.length(); i++) {
            ageGates[i] = ageGatesArray.getInt(i);
        }
        return ageGates;
    }

    /**
     * Request age signals from Google Play
     * Note: On Android, the Play Age Signals API doesn't support custom age gates like iOS.
     * We validate the age gates for consistency and use them to filter/interpret results.
     */
    private void requestAgeRange(JSONArray args, CallbackContext callbackContext) {
        if (ageSignalsManager == null) {
            String errorMsg = initializationError != null
                ? "Play Age Signals API not available: " + initializationError
                : "Play Age Signals API not available";
            sendError(callbackContext, "unsupported", errorMsg);
            return;
        }

        // Parse and validate age gates
        JSONArray ageGatesArray;
        int[] ageGates;
        try {
            ageGatesArray = args.getJSONArray(0);
            if (!validateAgeGates(ageGatesArray, callbackContext)) {
                return;
            }
            ageGates = getAgeGatesFromArray(ageGatesArray);
        } catch (JSONException e) {
            sendError(callbackContext, "invalid_arguments", "Please provide an array of 1-3 age gates");
            return;
        }

        cordova.getThreadPool().execute(() -> {
            try {
                AgeSignalsRequest request = AgeSignalsRequest.builder().build();

                ageSignalsManager.checkAgeSignals(request)
                    .addOnSuccessListener(result -> {
                        try {
                            JSONObject response = processAgeSignalsResult(result, ageGates);
                            callbackContext.success(response);
                        } catch (JSONException e) {
                            sendError(callbackContext, "parse_error", "Failed to parse response: " + e.getMessage());
                        }
                    })
                    .addOnFailureListener(e -> {
                        handleAgeSignalsError(e, callbackContext);
                    });
            } catch (Exception e) {
                sendError(callbackContext, "unknown", e.getMessage());
            }
        });
    }

    /**
     * Check if user is above a specific age
     */
    private void isUserAboveAge(JSONArray args, CallbackContext callbackContext) {
        if (ageSignalsManager == null) {
            String errorMsg = initializationError != null
                ? "Play Age Signals API not available: " + initializationError
                : "Play Age Signals API not available";
            sendError(callbackContext, "unsupported", errorMsg);
            return;
        }

        int minimumAge;
        try {
            minimumAge = args.getInt(0);
        } catch (JSONException e) {
            sendError(callbackContext, "invalid_arguments", "Please provide a minimum age as an integer");
            return;
        }

        // Validate age range
        if (minimumAge < MIN_AGE || minimumAge > MAX_AGE) {
            sendError(callbackContext, "invalid_arguments",
                "minimumAge must be between " + MIN_AGE + " and " + MAX_AGE);
            return;
        }

        cordova.getThreadPool().execute(() -> {
            try {
                AgeSignalsRequest request = AgeSignalsRequest.builder().build();

                ageSignalsManager.checkAgeSignals(request)
                    .addOnSuccessListener(result -> {
                        try {
                            JSONObject response = new JSONObject();
                            response.put("minimumAge", minimumAge);

                            Integer ageLower = result.ageLower();
                            Integer ageUpper = result.ageUpper();
                            AgeSignalsVerificationStatus status = result.userStatus();

                            // Always include bounds for consistent response shape
                            response.put("lowerBound", ageLower != null ? ageLower : JSONObject.NULL);
                            response.put("upperBound", ageUpper != null ? ageUpper : JSONObject.NULL);

                            if (status == null || status == AgeSignalsVerificationStatus.UNKNOWN) {
                                response.put("isAboveAge", false);
                                response.put("declined", false);
                                response.put("unknown", true);
                            } else if (status == AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED) {
                                response.put("isAboveAge", false);
                                response.put("declined", true);
                                response.put("unknown", false);
                            } else {
                                response.put("declined", false);
                                response.put("unknown", false);

                                if (ageLower != null) {
                                    response.put("isAboveAge", ageLower >= minimumAge);
                                } else {
                                    response.put("isAboveAge", false);
                                }
                            }

                            callbackContext.success(response);
                        } catch (JSONException e) {
                            sendError(callbackContext, "parse_error", "Failed to parse response: " + e.getMessage());
                        }
                    })
                    .addOnFailureListener(e -> {
                        handleAgeSignalsError(e, callbackContext);
                    });
            } catch (Exception e) {
                sendError(callbackContext, "unknown", e.getMessage());
            }
        });
    }

    /**
     * Full age signals check - Android-specific method that returns all available data
     */
    private void checkAgeSignals(CallbackContext callbackContext) {
        if (ageSignalsManager == null) {
            String errorMsg = initializationError != null
                ? "Play Age Signals API not available: " + initializationError
                : "Play Age Signals API not available";
            sendError(callbackContext, "unsupported", errorMsg);
            return;
        }

        cordova.getThreadPool().execute(() -> {
            try {
                AgeSignalsRequest request = AgeSignalsRequest.builder().build();

                ageSignalsManager.checkAgeSignals(request)
                    .addOnSuccessListener(result -> {
                        try {
                            // Use default age gates for checkAgeSignals
                            JSONObject response = processAgeSignalsResult(result, new int[]{13, 16, 18});
                            // Add Android-specific fields
                            response.put("installId", result.installId() != null ? result.installId() : JSONObject.NULL);
                            response.put("mostRecentApprovalDate",
                                result.mostRecentApprovalDate() != null ?
                                result.mostRecentApprovalDate().toString() : JSONObject.NULL);

                            callbackContext.success(response);
                        } catch (JSONException e) {
                            sendError(callbackContext, "parse_error", "Failed to parse response: " + e.getMessage());
                        }
                    })
                    .addOnFailureListener(e -> {
                        handleAgeSignalsError(e, callbackContext);
                    });
            } catch (Exception e) {
                sendError(callbackContext, "unknown", e.getMessage());
            }
        });
    }

    /**
     * Get platform information
     */
    private void getPlatformInfo(CallbackContext callbackContext) {
        try {
            JSONObject info = new JSONObject();
            info.put("platform", "android");
            info.put("systemVersion", Build.VERSION.RELEASE);
            info.put("sdkVersion", Build.VERSION.SDK_INT);
            info.put("requiredSdkVersion", 23);
            info.put("minimumVersionMet", Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
            info.put("apiAvailable", ageSignalsManager != null);

            callbackContext.success(info);
        } catch (JSONException e) {
            sendError(callbackContext, "unknown", e.getMessage());
        }
    }

    /**
     * Process the AgeSignalsResult into a JSON response compatible with the cross-platform API
     * @param result The age signals result from Google Play
     * @param ageGates The age gates used for the request (for reference/logging)
     */
    private JSONObject processAgeSignalsResult(AgeSignalsResult result, int[] ageGates) throws JSONException {
        JSONObject response = new JSONObject();

        AgeSignalsVerificationStatus status = result.userStatus();
        Integer ageLower = result.ageLower();
        Integer ageUpper = result.ageUpper();

        // Map status to cross-platform format
        if (status == null || status == AgeSignalsVerificationStatus.UNKNOWN) {
            response.put("status", "unknown");
            response.put("shared", false);
        } else if (status == AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED) {
            response.put("status", "declined");
            response.put("shared", false);
        } else if (status == AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_PENDING) {
            response.put("status", "pending");
            response.put("shared", false);
        } else {
            response.put("status", "shared");
            response.put("shared", true);
        }

        // Add age bounds
        response.put("lowerBound", ageLower != null ? ageLower : JSONObject.NULL);
        response.put("upperBound", ageUpper != null ? ageUpper : JSONObject.NULL);

        // Include the age gates that were requested (for debugging/verification)
        JSONArray ageGatesJson = new JSONArray();
        for (int gate : ageGates) {
            ageGatesJson.put(gate);
        }
        response.put("requestedAgeGates", ageGatesJson);

        // Map verification status to source-like field
        if (status != null) {
            switch (status) {
                case VERIFIED:
                    response.put("source", "verified");
                    response.put("userStatus", "verified");
                    break;
                case SUPERVISED:
                    response.put("source", "supervised");
                    response.put("userStatus", "supervised");
                    break;
                case SUPERVISED_APPROVAL_PENDING:
                    response.put("source", "supervised");
                    response.put("userStatus", "supervised_approval_pending");
                    break;
                case SUPERVISED_APPROVAL_DENIED:
                    response.put("source", "supervised");
                    response.put("userStatus", "supervised_approval_denied");
                    break;
                default:
                    response.put("source", JSONObject.NULL);
                    response.put("userStatus", "unknown");
                    break;
            }
        } else {
            response.put("source", JSONObject.NULL);
            response.put("userStatus", "unknown");
        }

        // Android doesn't have parental controls in the same way, use empty array for compatibility
        response.put("parentalControls", new JSONArray());

        return response;
    }

    /**
     * Handle Age Signals API errors
     */
    private void handleAgeSignalsError(Exception e, CallbackContext callbackContext) {
        String errorCode = "unknown";
        String message = e.getMessage();
        boolean retryable = false;

        if (e instanceof AgeSignalsException) {
            AgeSignalsException ageException = (AgeSignalsException) e;
            int code = ageException.getErrorCode();

            switch (code) {
                case -1: // API_NOT_AVAILABLE
                    errorCode = "api_not_available";
                    message = "Play Store app is too old. Please update.";
                    retryable = true;
                    break;
                case -2: // PLAY_STORE_NOT_FOUND
                    errorCode = "play_store_not_found";
                    message = "Google Play Store is not installed.";
                    retryable = true;
                    break;
                case -3: // NETWORK_ERROR
                    errorCode = "network_error";
                    message = "No network connection available.";
                    retryable = true;
                    break;
                case -4: // PLAY_SERVICES_NOT_FOUND
                    errorCode = "play_services_not_found";
                    message = "Google Play Services is not available.";
                    retryable = true;
                    break;
                case -5: // CANNOT_BIND_TO_SERVICE
                    errorCode = "service_binding_failed";
                    message = "Failed to bind to the service.";
                    retryable = true;
                    break;
                case -6: // PLAY_STORE_VERSION_OUTDATED
                    errorCode = "play_store_outdated";
                    message = "Google Play Store needs to be updated.";
                    retryable = true;
                    break;
                case -7: // PLAY_SERVICES_VERSION_OUTDATED
                    errorCode = "play_services_outdated";
                    message = "Google Play Services needs to be updated.";
                    retryable = true;
                    break;
                case -8: // CLIENT_TRANSIENT_ERROR
                    errorCode = "transient_error";
                    message = "A temporary error occurred. Please try again.";
                    retryable = true;
                    break;
                case -9: // APP_NOT_OWNED
                    errorCode = "app_not_owned";
                    message = "App was not installed from Google Play.";
                    retryable = false;
                    break;
                case -100: // INTERNAL_ERROR
                    errorCode = "internal_error";
                    message = "An internal error occurred.";
                    retryable = false;
                    break;
                default:
                    errorCode = "unknown";
                    message = "Unknown error: " + code;
                    retryable = false;
            }
        }

        try {
            JSONObject error = new JSONObject();
            error.put("error", errorCode);
            error.put("message", message);
            error.put("retryable", retryable);
            callbackContext.error(error);
        } catch (JSONException je) {
            callbackContext.error("Error: " + message);
        }
    }

    /**
     * Send a standardized error response
     */
    private void sendError(CallbackContext callbackContext, String errorCode, String message) {
        try {
            JSONObject error = new JSONObject();
            error.put("error", errorCode);
            error.put("message", message);
            callbackContext.error(error);
        } catch (JSONException e) {
            callbackContext.error("Error: " + message);
        }
    }
}
