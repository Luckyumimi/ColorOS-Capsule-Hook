package io.github.luckyumimi.capsulehook

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.error.XposedFrameworkError
import java.lang.reflect.Method
import kotlin.math.roundToInt

class CapsuleHookModule : XposedModule() {
    private var targetProcess = false
    private val main = Handler(Looper.getMainLooper())

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        targetProcess = param.processName == SYSTEM_UI
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!targetProcess || !param.isFirstPackage || param.packageName != SYSTEM_UI) return
        val handles = mutableListOf<HookHandle>()
        checked("Install host hooks", onFailure = { handles.forEach(HookHandle::unhook) }) {
            val host = HostContract(param.classLoader)
            val binding = Binding()
            handles += after(host.attach) { receiver, _, _ -> onMain { binding.attach(host.view(receiver)) } }
            handles += after(host.detach) { receiver, _, _ -> onMain { binding.detach(host.view(receiver)) } }
            host.pluginAdded?.let { hook -> handles += after(hook) { receiver, _, _ -> onMain { binding.attach(host.view(receiver)) } } }
            host.pluginRemoved?.let { hook -> handles += after(hook) { receiver, _, _ -> onMain {
                if (binding.host === host.view(receiver)) binding.dropPlugin()
            } } }
            host.release?.let { hook -> handles += after(hook) { receiver, _, _ -> onMain { binding.release(receiver) } } }

            fun publish(receiver: Any, args: List<Any?>, result: Any?, dispatched: StatusBarNotification?) = onMain {
                if (args.firstOrNull() == "cancelLiveAlert") {
                    if (result == true) binding.release(receiver)
                } else if (result == true) {
                    val context = binding.host?.context ?: return@onMain
                    val key = checked("Verify media") { host.mediaKey(context, receiver, dispatched) }
                    binding.setMedia(receiver, key)
                }
            }
            val primary = host.publish?.let { method -> checked("Install primary media hook") {
                after(method) { receiver, args, result -> publish(receiver, args, result, null) }
            } }
            handles += primary ?: after(host.dispatch()) { receiver, args, result ->
                publish(receiver, args, result, args.getOrNull(1) as? StatusBarNotification)
            }
            Log.i(TAG, "Host hooks installed (${if (primary != null) "primary" else "dispatch"})")
        }
    }

    private fun after(method: Method, action: (Any, List<Any?>, Any?) -> Unit): HookHandle =
        hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
            val result = chain.proceed()
            checked(method.name) { chain.thisObject?.let { action(it, chain.args, result) } }
            result
        }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == main.looper) checked("Update binding", action = action)
        else main.post { checked("Update binding", action = action) }
    }

    private inner class Binding {
        var host: ViewGroup? = null
            private set
        private var observer: ViewTreeObserver? = null
        private var root: ViewGroup? = null
        private var rejectedLoader: ClassLoader? = null
        private var plugin: PluginContract? = null
        private var drawHook: HookHandle? = null
        private var controller: Any? = null
        private var mediaKey: String? = null
        private var capsule: ViewGroup? = null
        private var dirty = true
        private val length = CapsuleLength<ViewGroup.LayoutParams>()
        private val background = demoBackgroundArgb?.let { argb ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb }
        }
        private val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(88, 214, 141) }
        private val layout = ViewTreeObserver.OnGlobalLayoutListener { dirty = true }
        private val preDraw = ViewTreeObserver.OnPreDrawListener {
            checked("Update capsule", onFailure = { bind(null) }) {
                refresh()
                maintainLength()
            }
            true
        }

        fun attach(view: ViewGroup?) {
            if (view?.display?.displayId != Display.DEFAULT_DISPLAY || !view.isAttachedToWindow) return
            if (host !== view) {
                detach(host)
                host = view
                observer = view.viewTreeObserver.also {
                    it.addOnGlobalLayoutListener(layout)
                    it.addOnPreDrawListener(preDraw)
                }
            }
            dirty = true
            view.invalidate()
        }

        fun detach(view: ViewGroup?) {
            if (view == null || host !== view) return
            observer?.takeIf { it.isAlive }?.also {
                it.removeOnGlobalLayoutListener(layout)
                it.removeOnPreDrawListener(preDraw)
            }
            observer = null
            host = null
            releaseMedia()
            dropPlugin()
            rejectedLoader = null
        }

        fun dropPlugin() {
            bind(null)
            root = null
            plugin = null
            drawHook?.unhook()
            drawHook = null
            dirty = true
        }

        fun setMedia(receiver: Any, key: String?) {
            if (key != mediaKey) bind(null)
            controller = receiver.takeIf { key != null }
            mediaKey = key
            dirty = true
            host?.invalidate()
        }

        fun release(receiver: Any) {
            if (controller === receiver) releaseMedia()
        }

        fun releaseMedia() {
            controller = null
            mediaKey = null
            bind(null)
        }

        private fun refresh() {
            val hostView = host ?: return
            if (!hostView.isAttachedToWindow) {
                detach(hostView)
                return
            }
            if (!dirty && root != null && (mediaKey == null || capsule != null)) return
            dirty = false
            val nextRoot = hostView.walk(4).filterIsInstance<ViewGroup>().singleOrNull {
                it.javaClass.name == ROOT && it.isAttachedToWindow
            }
            if (root !== nextRoot) {
                dropPlugin()
                root = nextRoot
                val loader = nextRoot?.javaClass?.classLoader ?: return
                if (loader === rejectedLoader) return
                checked("Install plugin draw hook", onFailure = { rejectedLoader = loader }) {
                    val contract = PluginContract(loader)
                    drawHook = after(contract.draw) { receiver, args, _ ->
                        if (Looper.myLooper() == main.looper) (args.firstOrNull() as? Canvas)?.let { draw(receiver, it) }
                    }
                    plugin = contract
                    Log.i(TAG, "Plugin draw hook installed")
                }
            }
            val key = mediaKey
            bind(if (nextRoot != null && key != null) plugin?.find(nextRoot, key) else null)
        }

        private fun bind(view: ViewGroup?) {
            if (view === capsule) return
            val previous = capsule
            capsule = view
            if (previous != null) {
                restoreLength(previous)
                previous.invalidate()
            }
            view?.invalidate()
        }

        private fun ownsBoundView(target: ViewGroup, contract: PluginContract, key: String): Boolean {
            val activeRoot = root ?: return false
            val activeHost = host ?: return false
            return target.isShown && target.isAttachedToWindow && target.isUnder(activeRoot) &&
                activeRoot.isUnder(activeHost) && contract.owns(target, key)
        }

        private fun maintainLength() {
            val target = capsule ?: return
            val contract = plugin ?: return
            val key = mediaKey ?: return
            val widthDp = demoWidthDp ?: return
            if (!ownsBoundView(target, contract, key)) {
                bind(null)
                return
            }
            val resizing = contract.isResizing(target)
            if (resizing == true) return
            if (resizing == null || !contract.isOnlyShowing(target, key)) {
                restoreLength(target)
                return
            }
            if (target.width <= 0 || target.height <= 0) return
            val params = target.layoutParams ?: return
            val desired = (widthDp * target.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
            length.apply(params, params.width, desired)?.let {
                params.width = it
                target.layoutParams = params
            }
        }

        private fun restoreLength(target: ViewGroup) {
            val params = target.layoutParams
            val restored = length.restore(params, params?.width) ?: return
            checkNotNull(params).width = restored
            target.layoutParams = params
        }

        private fun draw(receiver: Any, canvas: Canvas) {
            val target = capsule ?: return
            val contract = plugin ?: return
            val key = mediaKey ?: return
            if (receiver !== target || !ownsBoundView(target, contract, key)) return
            val resizing = contract.isResizing(target)
            val animated = if (resizing == true) contract.animatedWidth(target) ?: return else null
            if (resizing != null && background != null) {
                val bounds = capsuleBounds(target.width, target.measuredWidth, target.height, animated) ?: return
                canvas.drawRoundRect(bounds.left.toFloat(), 0f, bounds.right.toFloat(), bounds.height.toFloat(),
                    bounds.radius, bounds.radius, background)
            }
            if (resizing == true) return
            val dp = target.resources.displayMetrics.density
            if (target.width < 32 * dp || target.height < 16 * dp) return
            canvas.drawCircle(target.width - 10 * dp, target.height - 7 * dp, 2 * dp, marker)
        }
    }
}

private val TAG = "ColorOSCapsuleHook"

private fun View.isUnder(parentView: ViewGroup): Boolean =
    generateSequence(parent) { it.parent }.any { it === parentView }

private inline fun <T> checked(step: String, onFailure: () -> Unit = {}, action: () -> T): T? = try {
    action()
} catch (e: Throwable) {
    if (e !is Exception && e !is LinkageError && e !is XposedFrameworkError) throw e
    Log.w(TAG, "$step: ${e.javaClass.simpleName}")
    onFailure()
    null
}
