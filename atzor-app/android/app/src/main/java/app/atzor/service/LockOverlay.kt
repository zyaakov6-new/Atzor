package app.atzor.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import app.atzor.data.Store
import app.atzor.ui.BlockScreenContent
import app.atzor.ui.GentleScreenContent
import app.atzor.ui.NoticeContent
import app.atzor.ui.theme.AtzorTheme

/**
 * The lock/pause UI as a TYPE_ACCESSIBILITY_OVERLAY window, drawn directly by
 * the accessibility service. No activity launch - immune to the background
 * activity-launch throttling that silently swallowed the screens on One UI.
 * Bonus: the gentle pause appears instantly OVER the app, no bounce.
 */
class LockOverlay(private val service: AccessibilityService) {

    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null

    private var noticeView: ComposeView? = null
    private var noticeOwner: OverlayOwner? = null
    private val noticeHandler = Handler(Looper.getMainLooper())
    private val hideNotice = Runnable { hideNotice() }

    private val windowManager: WindowManager
        get() = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    val isShowing: Boolean get() = view != null

    fun show(pkg: String?, protection: Boolean, gentle: Boolean) {
        hide()
        val stateOwner = OverlayOwner().also { it.start() }
        owner = stateOwner

        val appLabel = pkg?.let {
            try {
                service.packageManager.getApplicationLabel(service.packageManager.getApplicationInfo(it, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }

        val composeView = ComposeView(service).apply {
            setViewTreeLifecycleOwner(stateOwner)
            setViewTreeSavedStateRegistryOwner(stateOwner)
            setContent {
                AtzorTheme {
                    if (gentle && pkg != null) {
                        GentleScreenContent(
                            pkg = pkg,
                            appLabel = appLabel ?: pkg,
                            onEnterAnyway = {
                                Store.grantGentlePass(pkg)
                                hide()
                            },
                            onHome = ::goHomeAndHide,
                        )
                    } else {
                        BlockScreenContent(
                            appLabel = appLabel,
                            protection = protection,
                            onHome = ::goHomeAndHide,
                            onFinished = ::hide,
                        )
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // NO_LIMITS: draw full-bleed under the status bar and nav bar, not just
            // the area the system normally reserves for app windows.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        composeView.fitsSystemWindows = false

        runCatching {
            windowManager.addView(composeView, params)
            view = composeView
        }.onFailure {
            owner?.stop()
            owner = null
        }
    }

    /**
     * A small, self-dismissing banner for something that was bounced rather
     * than blocked outright. Deliberately not a Toast: toasts posted from a
     * service are suppressed on newer Android. It does not take focus, so
     * whatever the user does next still works.
     */
    fun showNotice(@StringRes messageRes: Int, durationMs: Long = 4500L) {
        hideNotice()
        val stateOwner = OverlayOwner().also { it.start() }
        noticeOwner = stateOwner

        val composeView = ComposeView(service).apply {
            setViewTreeLifecycleOwner(stateOwner)
            setViewTreeSavedStateRegistryOwner(stateOwner)
            setContent { AtzorTheme { NoticeContent(messageRes) } }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL }

        runCatching {
            windowManager.addView(composeView, params)
            noticeView = composeView
            noticeHandler.removeCallbacks(hideNotice)
            noticeHandler.postDelayed(hideNotice, durationMs)
        }.onFailure {
            noticeOwner?.stop()
            noticeOwner = null
        }
    }

    fun hideNotice() {
        noticeHandler.removeCallbacks(hideNotice)
        noticeView?.let { v -> runCatching { windowManager.removeView(v) } }
        noticeView = null
        noticeOwner?.stop()
        noticeOwner = null
    }

    fun hide() {
        hideNotice()
        view?.let { v -> runCatching { windowManager.removeView(v) } }
        view = null
        owner?.stop()
        owner = null
    }

    private fun goHomeAndHide() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        hide()
    }

    /** Minimal lifecycle/saved-state owner so ComposeView can live in a service window. */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val controller = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

        fun start() {
            controller.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun stop() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }
}
