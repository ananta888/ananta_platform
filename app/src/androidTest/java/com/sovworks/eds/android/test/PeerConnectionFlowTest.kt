package com.sovworks.eds.android.test

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.sovworks.eds.android.filemanager.activities.FileManagerActivity
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class PeerConnectionFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiTestHelpers.device()
    private lateinit var context: Context
    private var scenario: ActivityScenario<FileManagerActivity>? = null

    @Before
    fun setUp() {
        context = instrumentation.targetContext
        UiTestHelpers.configureSignaling(context)
        UiTestHelpers.ensureTestIdentity(context)
        scenario = ActivityScenario.launch(FileManagerActivity::class.java)
        UiTestHelpers.waitForMenu(device)
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun peerConnectionsDoesNotAutoConnectOnOpen() {
        UiTestHelpers.navigateTo(device, "Peer Connections")
        device.wait(Until.hasObject(By.text("Peers")), UiTestHelpers.DEFAULT_TIMEOUT_MS)
        val hasConnecting = device.hasObject(By.textContains("Status: connecting"))
        assertFalse("Auto-connect detected: status=connecting visible", hasConnecting)
    }

    @Test
    fun peerConnectionRequiresMutualConfirmation() {
        val args = instrumentation.arguments
        val role = args.getString("role") ?: "initiator"
        val peerKeyPrefix = args.getString("peerKeyPrefix")
            ?: error("Missing instrumentation arg: peerKeyPrefix (e.g. ABCDEF12)")

        if (role == "initiator") {
            UiTestHelpers.navigateTo(device, "Device Pairing")
            val refresh = device.wait(Until.findObject(By.text("Refresh")), UiTestHelpers.DEFAULT_TIMEOUT_MS)
            checkNotNull(refresh) { "Refresh button not found on Pairing screen." }
            refresh.click()

            if (!UiTestHelpers.scrollToTextContains(peerKeyPrefix)) {
                device.wait(Until.findObject(By.textContains(peerKeyPrefix)), UiTestHelpers.DEFAULT_TIMEOUT_MS)
            }
            UiTestHelpers.clickButtonInRowContainingText(device, "Key: $peerKeyPrefix", "Ping")

            UiTestHelpers.navigateTo(device, "Peer Connections")
            device.wait(Until.hasObject(By.textContains("Request: outgoing")), UiTestHelpers.DEFAULT_TIMEOUT_MS)
        } else {
            UiTestHelpers.navigateTo(device, "Peer Connections")
            if (!UiTestHelpers.scrollToTextContains("Request: incoming")) {
                device.wait(Until.hasObject(By.textContains("Request: incoming")), UiTestHelpers.DEFAULT_TIMEOUT_MS)
            }
            UiTestHelpers.clickButtonInRowContainingText(device, "Request: incoming", "Confirm")
            device.wait(Until.hasObject(By.textContains("Status: connected")), UiTestHelpers.DEFAULT_TIMEOUT_MS)
        }
    }
}
