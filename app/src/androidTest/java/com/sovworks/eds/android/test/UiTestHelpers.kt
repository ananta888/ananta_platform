package com.sovworks.eds.android.test

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.sovworks.eds.android.BuildConfig
import com.sovworks.eds.android.identity.IdentityManager
import com.sovworks.eds.android.settings.UserSettings
import com.sovworks.eds.android.settings.UserSettingsCommon
import java.util.UUID

object UiTestHelpers {
    const val DEFAULT_TIMEOUT_MS = 15_000L

    fun device(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun ensureTestIdentity(context: Context) {
        val identity = IdentityManager.loadIdentity(context)
        if (identity == null) {
            IdentityManager.createNewIdentity(context, "Test-${UUID.randomUUID()}")
        }
    }

    fun configureSignaling(context: Context) {
        val settings = UserSettings.getSettings(context)
        settings.sharedPreferences.edit()
            .putString(UserSettingsCommon.SIGNALING_MODE, UserSettingsCommon.SIGNALING_MODE_WEBSOCKET)
            .putString(UserSettingsCommon.SIGNALING_SERVER_URL, BuildConfig.SIGNALING_SERVER_URL)
            .putString(UserSettingsCommon.SIGNALING_PUBLIC_VISIBILITY, UserSettingsCommon.SIGNALING_VISIBILITY_PUBLIC)
            .putInt(UserSettingsCommon.LAST_VIEWED_CHANGES, Int.MAX_VALUE)
            .apply()
    }

    fun waitForMenu(device: UiDevice, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        device.wait(Until.hasObject(By.desc("Menu")), timeoutMs)
    }

    fun openDrawer(device: UiDevice) {
        val menuButton = device.findObject(By.desc("Menu"))
        checkNotNull(menuButton) { "Menu button not found." }
        menuButton.click()
    }

    fun navigateTo(device: UiDevice, label: String) {
        openDrawer(device)
        val entry = device.wait(Until.findObject(By.text(label)), DEFAULT_TIMEOUT_MS)
        checkNotNull(entry) { "Menu entry not found: $label" }
        entry.click()
    }

    fun waitForTextContains(device: UiDevice, text: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): UiObject2? {
        return device.wait(Until.findObject(By.textContains(text)), timeoutMs)
    }

    fun clickButtonInRowContainingText(device: UiDevice, text: String, buttonText: String) {
        val target = waitForTextContains(device, text)
        checkNotNull(target) { "Row text not found: $text" }
        var current: UiObject2? = target
        repeat(4) {
            val button = current?.findObject(By.text(buttonText))
            if (button != null) {
                button.click()
                return
            }
            current = current?.parent
        }
        error("Button '$buttonText' not found near text: $text")
    }

    fun scrollToTextContains(text: String): Boolean {
        return try {
            val scrollable = UiScrollable(UiSelector().scrollable(true))
            scrollable.scrollTextIntoView(text)
        } catch (_: Exception) {
            false
        }
    }
}
