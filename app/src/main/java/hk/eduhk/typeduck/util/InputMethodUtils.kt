package hk.eduhk.typeduck.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import hk.eduhk.typeduck.TypeDuckIMEService
import splitties.systemservices.inputMethodManager
import timber.log.Timber

object InputMethodUtils {
    private val serviceName =
        ComponentName(appContext, TypeDuckIMEService::class.java).flattenToShortString()

    fun checkIsTypeDuckEnabled(): Boolean {
        val activeImeIds = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_INPUT_METHODS
        ) ?: "(none)"

        Timber.i("List of active IMEs: $activeImeIds")
        return activeImeIds.split(":").contains(serviceName)
    }

    fun checkIsTypeDuckSelected(): Boolean {
        val selectedImeIds = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: "(none)"
        Timber.i("Selected IME: $selectedImeIds")
        return selectedImeIds == serviceName
    }

    fun showImeEnablerActivity(context: Context) =
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))

    fun showImePicker(): Boolean {
        inputMethodManager.showInputMethodPicker()
        return true
    }
}