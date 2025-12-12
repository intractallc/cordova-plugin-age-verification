import Foundation
import UIKit

#if canImport(DeclaredAgeRange)
import DeclaredAgeRange
#endif

@objc(AgeVerificationIOS)
class AgeVerificationIOS: CDVPlugin {

    // MARK: - Check Availability

    /// Check if the DeclaredAgeRange API is available on this device
    @objc(isAvailable:)
    func isAvailable(command: CDVInvokedUrlCommand) {
        var pluginResult: CDVPluginResult

        if #available(iOS 26.0, *) {
            #if canImport(DeclaredAgeRange)
            pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: true)
            #else
            pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: false)
            #endif
        } else {
            pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: false)
        }

        self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
    }

    // MARK: - Request Age Range

    /// Request the user's age range with specified age gates
    /// Arguments: [ageGates: [Int]] - Array of age thresholds (1-3 values)
    @objc(requestAgeRange:)
    func requestAgeRange(command: CDVInvokedUrlCommand) {
        guard #available(iOS 26.0, *) else {
            let pluginResult = CDVPluginResult(
                status: CDVCommandStatus_ERROR,
                messageAs: ["error": "unsupported", "message": "DeclaredAgeRange requires iOS 26.0 or later"]
            )
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
            return
        }

        #if canImport(DeclaredAgeRange)
        // Parse age gates from arguments
        guard let ageGatesArray = command.arguments.first as? [Int], !ageGatesArray.isEmpty else {
            let pluginResult = CDVPluginResult(
                status: CDVCommandStatus_ERROR,
                messageAs: ["error": "invalid_arguments", "message": "Please provide an array of 1-3 age gates"]
            )
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
            return
        }

        // Validate age gates count (max 3)
        guard ageGatesArray.count <= 3 else {
            let pluginResult = CDVPluginResult(
                status: CDVCommandStatus_ERROR,
                messageAs: ["error": "invalid_arguments", "message": "Maximum of 3 age gates allowed"]
            )
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
            return
        }

        // Execute age range request on main actor
        Task { @MainActor in
            await self.performAgeRangeRequest(ageGates: ageGatesArray, callbackId: command.callbackId)
        }
        #else
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_ERROR,
            messageAs: ["error": "unsupported", "message": "DeclaredAgeRange framework not available"]
        )
        self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
        #endif
    }

    #if canImport(DeclaredAgeRange)
    @available(iOS 26.0, *)
    @MainActor
    private func performAgeRangeRequest(ageGates: [Int], callbackId: String) async {
        // Capture view controller reference before async operation to avoid race condition
        guard let presentingViewController = self.viewController else {
            let pluginResult = CDVPluginResult(
                status: CDVCommandStatus_ERROR,
                messageAs: ["error": "not_available", "message": "View controller not available"]
            )
            self.commandDelegate.send(pluginResult, callbackId: callbackId)
            return
        }

        do {
            let response: AgeRangeResponse

            // Call the appropriate overload based on number of age gates
            // The API expects separate Int parameters, not an array
            switch ageGates.count {
            case 1:
                response = try await AgeRangeService.shared.requestAgeRange(
                    ageGates: ageGates[0],
                    in: presentingViewController
                )
            case 2:
                response = try await AgeRangeService.shared.requestAgeRange(
                    ageGates: ageGates[0], ageGates[1],
                    in: presentingViewController
                )
            case 3:
                response = try await AgeRangeService.shared.requestAgeRange(
                    ageGates: ageGates[0], ageGates[1], ageGates[2],
                    in: presentingViewController
                )
            default:
                let pluginResult = CDVPluginResult(
                    status: CDVCommandStatus_ERROR,
                    messageAs: ["error": "invalid_arguments", "message": "Invalid number of age gates"]
                )
                self.commandDelegate.send(pluginResult, callbackId: callbackId)
                return
            }

            // Process the response
            let resultData = self.processAgeRangeResponse(response)
            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: resultData)
            self.commandDelegate.send(pluginResult, callbackId: callbackId)

        } catch let error as AgeRangeService.Error {
            let pluginResult: CDVPluginResult
            switch error {
            case .invalidRequest:
                pluginResult = CDVPluginResult(
                    status: CDVCommandStatus_ERROR,
                    messageAs: ["error": "invalid_request", "message": "Invalid age range request. Ensure age ranges have at least 2 years between them."]
                )
            case .notAvailable:
                pluginResult = CDVPluginResult(
                    status: CDVCommandStatus_ERROR,
                    messageAs: ["error": "not_available", "message": "Age range service not available on this device configuration."]
                )
            @unknown default:
                pluginResult = CDVPluginResult(
                    status: CDVCommandStatus_ERROR,
                    messageAs: ["error": "unknown", "message": "An unknown error occurred: \(error.localizedDescription)"]
                )
            }
            self.commandDelegate.send(pluginResult, callbackId: callbackId)

        } catch {
            let pluginResult = CDVPluginResult(
                status: CDVCommandStatus_ERROR,
                messageAs: ["error": "unknown", "message": error.localizedDescription]
            )
            self.commandDelegate.send(pluginResult, callbackId: callbackId)
        }
    }

    @available(iOS 26.0, *)
    private func processAgeRangeResponse(_ response: AgeRangeResponse) -> [String: Any] {
        switch response {
        case .declinedSharing:
            return [
                "status": "declined",
                "shared": false,
                "lowerBound": NSNull(),
                "upperBound": NSNull(),
                "source": NSNull(),
                "parentalControls": [] as [String]
            ]

        case .sharing(let range):
            var resultDict: [String: Any] = [
                "status": "shared",
                "shared": true
            ]

            // Add bounds - these are optional Int values
            if let lowerBound = range.lowerBound {
                resultDict["lowerBound"] = lowerBound
            } else {
                resultDict["lowerBound"] = NSNull()
            }

            if let upperBound = range.upperBound {
                resultDict["upperBound"] = upperBound
            } else {
                resultDict["upperBound"] = NSNull()
            }

            // Add source information
            switch range.source {
            case .selfDeclared:
                resultDict["source"] = "selfDeclared"
            case .guardianDeclared:
                resultDict["source"] = "guardianDeclared"
            @unknown default:
                resultDict["source"] = "unknown"
            }

            // Add parental controls if available
            var parentalControls: [String] = []
            let activeControls = range.activeParentalControls

            // Check for each possible parental control type
            if activeControls.contains(.communicationLimits) {
                parentalControls.append("communicationLimits")
            }
            if activeControls.contains(.screenTime) {
                parentalControls.append("screenTime")
            }
            if activeControls.contains(.contentRestrictions) {
                parentalControls.append("contentRestrictions")
            }
            resultDict["parentalControls"] = parentalControls

            return resultDict

        @unknown default:
            return [
                "status": "unknown",
                "shared": false,
                "lowerBound": NSNull(),
                "upperBound": NSNull(),
                "source": NSNull(),
                "parentalControls": [] as [String]
            ]
        }
    }
    #endif

    // MARK: - Check if User is Above Age

    /// Convenience method to check if user is at or above a specific age
    /// Arguments: [minimumAge: Int]
    @objc(isUserAboveAge:)
    func isUserAboveAge(command: CDVInvokedUrlCommand) {
        guard #available(iOS 26.0, *) else {
            let pluginResult = CDVPluginResult(
                status: CDVCommandStatus_ERROR,
                messageAs: ["error": "unsupported", "message": "DeclaredAgeRange requires iOS 26.0 or later"]
            )
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
            return
        }

        #if canImport(DeclaredAgeRange)
        guard let minimumAge = command.arguments.first as? Int else {
            let pluginResult = CDVPluginResult(
                status: CDVCommandStatus_ERROR,
                messageAs: ["error": "invalid_arguments", "message": "Please provide a minimum age as an integer"]
            )
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
            return
        }

        Task { @MainActor in
            await self.performAgeCheck(minimumAge: minimumAge, callbackId: command.callbackId)
        }
        #else
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_ERROR,
            messageAs: ["error": "unsupported", "message": "DeclaredAgeRange framework not available"]
        )
        self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
        #endif
    }

    #if canImport(DeclaredAgeRange)
    @available(iOS 26.0, *)
    @MainActor
    private func performAgeCheck(minimumAge: Int, callbackId: String) async {
        // Capture view controller reference before async operation to avoid race condition
        guard let presentingViewController = self.viewController else {
            let pluginResult = CDVPluginResult(
                status: CDVCommandStatus_ERROR,
                messageAs: ["error": "not_available", "message": "View controller not available"]
            )
            self.commandDelegate.send(pluginResult, callbackId: callbackId)
            return
        }

        do {
            let response = try await AgeRangeService.shared.requestAgeRange(
                ageGates: minimumAge,
                in: presentingViewController
            )

            var resultDict: [String: Any] = [
                "minimumAge": minimumAge
            ]

            switch response {
            case .declinedSharing:
                resultDict["isAboveAge"] = false
                resultDict["declined"] = true
                resultDict["lowerBound"] = NSNull()
                resultDict["upperBound"] = NSNull()

            case .sharing(let range):
                resultDict["declined"] = false

                if let lowerBound = range.lowerBound {
                    resultDict["isAboveAge"] = lowerBound >= minimumAge
                    resultDict["lowerBound"] = lowerBound
                } else {
                    // No lower bound means age is below the first gate
                    resultDict["isAboveAge"] = false
                    resultDict["lowerBound"] = NSNull()
                }

                if let upperBound = range.upperBound {
                    resultDict["upperBound"] = upperBound
                } else {
                    resultDict["upperBound"] = NSNull()
                }

            @unknown default:
                resultDict["isAboveAge"] = false
                resultDict["declined"] = false
                resultDict["lowerBound"] = NSNull()
                resultDict["upperBound"] = NSNull()
            }

            let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: resultDict)
            self.commandDelegate.send(pluginResult, callbackId: callbackId)

        } catch {
            let pluginResult = CDVPluginResult(
                status: CDVCommandStatus_ERROR,
                messageAs: ["error": "request_failed", "message": error.localizedDescription]
            )
            self.commandDelegate.send(pluginResult, callbackId: callbackId)
        }
    }
    #endif

    // MARK: - Check Age Signals (iOS implementation - maps to requestAgeRange)

    /// For cross-platform compatibility with Android's checkAgeSignals
    @objc(checkAgeSignals:)
    func checkAgeSignals(command: CDVInvokedUrlCommand) {
        // On iOS, this is equivalent to requestAgeRange with default gates
        guard #available(iOS 26.0, *) else {
            let pluginResult = CDVPluginResult(
                status: CDVCommandStatus_ERROR,
                messageAs: ["error": "unsupported", "message": "DeclaredAgeRange requires iOS 26.0 or later"]
            )
            self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
            return
        }

        #if canImport(DeclaredAgeRange)
        Task { @MainActor in
            await self.performAgeRangeRequest(ageGates: [13, 16, 18], callbackId: command.callbackId)
        }
        #else
        let pluginResult = CDVPluginResult(
            status: CDVCommandStatus_ERROR,
            messageAs: ["error": "unsupported", "message": "DeclaredAgeRange framework not available"]
        )
        self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
        #endif
    }

    // MARK: - Get Platform Info

    /// Get information about the current platform and API availability
    @objc(getPlatformInfo:)
    func getPlatformInfo(command: CDVInvokedUrlCommand) {
        var info: [String: Any] = [
            "platform": "ios",
            "systemVersion": UIDevice.current.systemVersion,
            "requiredVersion": "26.0"
        ]

        if #available(iOS 26.0, *) {
            info["minimumVersionMet"] = true
            #if canImport(DeclaredAgeRange)
            info["frameworkAvailable"] = true
            info["apiAvailable"] = true
            #else
            info["frameworkAvailable"] = false
            info["apiAvailable"] = false
            #endif
        } else {
            info["minimumVersionMet"] = false
            info["frameworkAvailable"] = false
            info["apiAvailable"] = false
        }

        let pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: info)
        self.commandDelegate.send(pluginResult, callbackId: command.callbackId)
    }
}
