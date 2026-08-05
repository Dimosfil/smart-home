package com.dimosfil.smarthome.onboarding

enum class OnboardingEvent {
    AddDevice,
    CandidateSelected,
    ConfirmDevice,
    InstallationReady,
    NetworkSubmitted,
    ProvisioningSucceeded,
    FinishSuccess,
    OpenControl,
    Back,
}

object OnboardingStateMachine {
    fun transition(
        current: OnboardingScreen,
        event: OnboardingEvent,
        provisioningMode: ProvisioningMode? = null,
    ): OnboardingScreen = when (event) {
        OnboardingEvent.AddDevice -> requireCurrent(current, OnboardingScreen.DeviceList) {
            OnboardingScreen.Discovery
        }
        OnboardingEvent.CandidateSelected -> requireCurrent(current, OnboardingScreen.Discovery) {
            OnboardingScreen.DeviceFound
        }
        OnboardingEvent.ConfirmDevice -> requireCurrent(current, OnboardingScreen.DeviceFound) {
            OnboardingScreen.Installation
        }
        OnboardingEvent.InstallationReady -> requireCurrent(current, OnboardingScreen.Installation) {
            when (provisioningMode) {
                ProvisioningMode.RequiresWifiCredentials -> OnboardingScreen.NetworkSetup
                ProvisioningMode.AlreadyNetworked -> OnboardingScreen.Provisioning
                ProvisioningMode.Unsupported, null -> OnboardingScreen.Installation
            }
        }
        OnboardingEvent.NetworkSubmitted -> requireCurrent(current, OnboardingScreen.NetworkSetup) {
            OnboardingScreen.Provisioning
        }
        OnboardingEvent.ProvisioningSucceeded -> requireCurrent(current, OnboardingScreen.Provisioning) {
            OnboardingScreen.Success
        }
        OnboardingEvent.FinishSuccess -> requireCurrent(current, OnboardingScreen.Success) {
            OnboardingScreen.DeviceList
        }
        OnboardingEvent.OpenControl -> requireCurrent(current, OnboardingScreen.DeviceList) {
            OnboardingScreen.Control
        }
        OnboardingEvent.Back -> when (current) {
            OnboardingScreen.DeviceList -> OnboardingScreen.DeviceList
            OnboardingScreen.Discovery -> OnboardingScreen.DeviceList
            OnboardingScreen.DeviceFound -> OnboardingScreen.Discovery
            OnboardingScreen.Installation -> OnboardingScreen.DeviceFound
            OnboardingScreen.NetworkSetup -> OnboardingScreen.Installation
            OnboardingScreen.Provisioning -> OnboardingScreen.NetworkSetup
            OnboardingScreen.Success -> OnboardingScreen.DeviceList
            OnboardingScreen.Control -> OnboardingScreen.DeviceList
        }
    }

    private inline fun requireCurrent(
        current: OnboardingScreen,
        required: OnboardingScreen,
        next: () -> OnboardingScreen,
    ): OnboardingScreen {
        require(current == required) { "Invalid onboarding transition: $current" }
        return next()
    }
}
