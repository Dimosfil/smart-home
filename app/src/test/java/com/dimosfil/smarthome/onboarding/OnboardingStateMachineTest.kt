package com.dimosfil.smarthome.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OnboardingStateMachineTest {
    @Test
    fun `wifi credential flow visits all onboarding screens`() {
        var screen = OnboardingScreen.DeviceList

        screen = OnboardingStateMachine.transition(screen, OnboardingEvent.AddDevice)
        assertEquals(OnboardingScreen.Discovery, screen)
        screen = OnboardingStateMachine.transition(screen, OnboardingEvent.CandidateSelected)
        assertEquals(OnboardingScreen.DeviceFound, screen)
        screen = OnboardingStateMachine.transition(screen, OnboardingEvent.ConfirmDevice)
        assertEquals(OnboardingScreen.Installation, screen)
        screen = OnboardingStateMachine.transition(
            screen,
            OnboardingEvent.InstallationReady,
            ProvisioningMode.RequiresWifiCredentials,
        )
        assertEquals(OnboardingScreen.NetworkSetup, screen)
        screen = OnboardingStateMachine.transition(screen, OnboardingEvent.NetworkSubmitted)
        assertEquals(OnboardingScreen.Provisioning, screen)
        screen = OnboardingStateMachine.transition(screen, OnboardingEvent.ProvisioningSucceeded)
        assertEquals(OnboardingScreen.Success, screen)
        screen = OnboardingStateMachine.transition(screen, OnboardingEvent.FinishSuccess)
        assertEquals(OnboardingScreen.DeviceList, screen)
        screen = OnboardingStateMachine.transition(screen, OnboardingEvent.OpenControl)

        assertEquals(OnboardingScreen.Control, screen)
    }

    @Test
    fun `invalid transition is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            OnboardingStateMachine.transition(
                OnboardingScreen.DeviceList,
                OnboardingEvent.NetworkSubmitted,
            )
        }
    }

    @Test
    fun `unsupported device remains on installation screen`() {
        val next = OnboardingStateMachine.transition(
            OnboardingScreen.Installation,
            OnboardingEvent.InstallationReady,
            ProvisioningMode.Unsupported,
        )

        assertEquals(OnboardingScreen.Installation, next)
    }
}
