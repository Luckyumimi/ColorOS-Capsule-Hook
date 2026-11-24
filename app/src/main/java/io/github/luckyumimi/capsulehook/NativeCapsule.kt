package io.github.luckyumimi.capsulehook

import android.content.Context
import android.graphics.Canvas
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal const val SYSTEM_UI = "com.android.systemui"
internal const val ROOT = "com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleContainerRoot"
private const val HOST = "com.oplus.systemui.statusbar.seeding.CapsulePluginContainerController"
private const val MEDIA = "com.oplus.systemui.media.seedling.livealert.MediaLiveAlertController"
private const val DATA = "com.oplus.systemui.seedlingservice.mediaControl.SeedlingMediaData"
private const val CAPSULE = "com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleView"
private const val CONTAINER = "com.oplus.systemui.plugins.seedling.capsule.ui.view.CapsuleContainer"
private const val PLUGIN = "com.android.systemui.plugins.statusbar.SeedlingPlugin"

internal class HostContract(loader: ClassLoader) {
    private val host = loader.loadClass(HOST)
    private val media = loader.loadClass(MEDIA)
    private val data = loader.loadClass(DATA)
    private val hostView = host.field("capsulePluginContainer", ViewGroup::class.java)
    private val rows = media.field("mediaDataList", List::class.java)
    private val activeId = optionalMember { media.field("lastStatusBarPlayerId", String::class.java) }
    private val notification = optionalMember { media.field("sbn", StatusBarNotification::class.java) }
    private val playerId = data.method("getPlayerId", String::class.java)
    private val token = data.method("getToken", MediaSession.Token::class.java)
    private val owner = data.method("getPackageName", String::class.java)
    val attach = host.method("onViewAttached", Void.TYPE)
    val detach = host.method("onViewDetached", Void.TYPE)
    val pluginAdded = optionalMember {
        host.method("onPluginAdded", Void.TYPE, loader.loadClass(PLUGIN), Context::class.java)
    }
    val pluginRemoved = optionalMember { host.method("onPluginRemove", Void.TYPE, loader.loadClass(PLUGIN)) }
    val publish = if (notification == null) null else optionalMember {
        media.method("sendLiveAlertData", Boolean::class.javaPrimitiveType!!, String::class.java)
    }
    fun dispatch() = media.method("sendDataToPlugin", Boolean::class.javaPrimitiveType!!,
        String::class.java, StatusBarNotification::class.java)
    val release = optionalMember { media.method("release", Void.TYPE) }

    fun view(controller: Any) = hostView.get(controller) as? ViewGroup

    fun mediaKey(context: Context, controller: Any, dispatched: StatusBarNotification?): String? {
        val sbn = dispatched ?: notification?.get(controller) as? StatusBarNotification ?: return null
        if (sbn.packageName != SYSTEM_UI) return null
        val candidates = sbn.notification.extras.getBundle("oplus.mediaData")
            ?.getParcelableArrayList("playerArr", Bundle::class.java) ?: return null
        val cards = candidates.filter { it.getBundle("oplus.livealert.capsule") != null }.map {
            val card = it.getBundle("oplus.livealert.card")
            MediaCard(card?.getString("uniqueId"), card?.getBundle("primaryData")?.getString("pkg"))
        }
        val card = selectCard(activeId?.get(controller) as? String, cards) ?: return null
        val row = (rows.get(controller) as? List<*>)?.singleOrNull {
            data.isInstance(it) && playerId.invoke(it) == card.id
        } ?: return null
        if (owner.invoke(row) != card.owner) return null
        val session = token.invoke(row) as? MediaSession.Token ?: return null
        return sbn.key.takeIf { MediaController(context, session).packageName == card.owner }
    }
}

internal class PluginContract(loader: ClassLoader) {
    private val capsule = loader.loadClass(CAPSULE)
    private val container = loader.loadClass(CONTAINER)
    private val viewItem = capsule.getDeclaredMethod("getViewItem").apply { isAccessible = true }
    private val keys = viewItem.returnType.declaredFields.filter {
        it.type == String::class.java && !Modifier.isStatic(it.modifiers)
    }.onEach { it.isAccessible = true }
    val draw = capsule.method("onDraw", Void.TYPE, Canvas::class.java)
    private val resizing = optionalMember { capsule.method("getWidthChanging", Boolean::class.javaPrimitiveType!!) }

    init {
        require(keys.isNotEmpty()) { "No candidate notification-key fields on the view item" }
    }

    fun find(root: ViewGroup, key: String): ViewGroup? {
        val host = root.walk(4).filterIsInstance<ViewGroup>().singleOrNull { container.isInstance(it) } ?: return null
        return host.walk(4).filterIsInstance<ViewGroup>().singleOrNull {
            capsule.isInstance(it) && it.isShown && it.isAttachedToWindow && owns(it, key)
        }
    }

    fun owns(view: ViewGroup, key: String): Boolean {
        val item = viewItem.invoke(view) ?: return false
        return keys.any { it.get(item) == key }
    }

    fun isResizing(view: ViewGroup) = resizing?.invoke(view) == true
}

internal fun ViewGroup.walk(depth: Int): Sequence<View> = sequence {
    if (depth == 0) return@sequence
    for (i in 0 until childCount) {
        val child = getChildAt(i)
        yield(child)
        if (child is ViewGroup) yieldAll(child.walk(depth - 1))
    }
}

private fun Class<*>.field(name: String, expected: Class<*>): Field = getDeclaredField(name).apply {
    if (!expected.isAssignableFrom(type) || Modifier.isStatic(modifiers)) throw NoSuchFieldException("$name: $expected")
    isAccessible = true
}

private fun Class<*>.method(name: String, result: Class<*>, vararg args: Class<*>): Method =
    getDeclaredMethod(name, *args).apply {
        if (returnType != result || Modifier.isStatic(modifiers)) throw NoSuchMethodException("$name: $result")
        isAccessible = true
    }

private inline fun <T> optionalMember(resolve: () -> T): T? = try {
    resolve()
} catch (_: ReflectiveOperationException) {
    null
}
