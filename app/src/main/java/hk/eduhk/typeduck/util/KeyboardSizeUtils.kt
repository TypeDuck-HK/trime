package hk.eduhk.typeduck.util

import android.annotation.SuppressLint
import android.graphics.Point
import android.os.Build
import android.view.WindowInsets
import hk.eduhk.typeduck.ime.core.Trime
import kotlin.math.floor
import splitties.systemservices.windowManager


object KeyboardSizeUtils {

    private val point = Point(-1, -1)

    @SuppressLint("NewApi")
    @JvmStatic
    fun refreshSize() {
        val windowManager = Trime.getService().windowManager?: return
        var currentVersion = Build.VERSION.SDK_INT

        if (currentVersion >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            val insets = metrics.windowInsets.getInsets(
                WindowInsets.Type.navigationBars() or
                WindowInsets.Type.displayCutout()
            )
            point.set(bounds.width() - insets.left - insets.right,
                    bounds.height() - insets.top - insets.bottom)
        } else {
            windowManager.defaultDisplay.getSize(point)
        }
    }

    @JvmStatic
    fun getScreenWidth(): Int {
        if (point.x < 0) {
            refreshSize()
        }
        return point.x
    }

    @JvmStatic
    fun getScreenHeight(): Int {
        if (point.y < 0) {
            refreshSize()
        }
        return if (point.x <= point.y) point.y else point.y - floor(0.2 * point.y).toInt()
    }
}