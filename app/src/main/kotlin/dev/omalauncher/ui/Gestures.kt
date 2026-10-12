package dev.omalauncher.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.omalauncher.Runtime
import dev.omalauncher.linux.LinuxState

/** What a gesture on home does. */
enum class GestureAction(
    /** Saved in preferences: never rename. */
    val id: String,
    val label: String,
    val description: String,
) {
    Search("search", "search", "the app drawer with the keyboard up, to type right away"),
    Drawer("drawer", "app drawer", "the app drawer; tap the bar to type"),
    Settings("settings", "settings", "this settings screen"),
    Style("style", "style", "theme, background and home screen"),
    Store("store", "widget store", "add widgets to the home screen"),
    Agents("agents", "coding agents", "install and pick agents; with no Linux, the Linux screen"),
    None("none", "nothing", "the swipe does nothing"),
    ;

    /** Opens the drawer under the finger rather than once the swipe is done. */
    val opensDrawer get() = this == Search || this == Drawer

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/** The actions picked for home's gestures. Swiping up is the only one so far. */
class Gestures(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    private var up by mutableStateOf(GestureAction.of(prefs.getString(SWIPE_UP, null)) ?: GestureAction.Search)

    /** Search by default: the drawer opened with the keyboard, as it always did. Saved as it's set. */
    var swipeUp: GestureAction
        get() = up
        set(action) {
            up = action
            prefs.edit().putString(SWIPE_UP, action.id).apply()
        }

    private companion object {
        const val SWIPE_UP = "gesture_swipe_up"
    }
}

/** Runs [action] from a gesture; screens that need Linux offer to install it instead. */
internal fun LauncherState.perform(
    action: GestureAction,
    runtime: Runtime,
) {
    when (action) {
        GestureAction.Search, GestureAction.Drawer -> {
            keyboardOnOpen = action == GestureAction.Search
            open()
        }
        GestureAction.Settings -> settingsOpen = true
        GestureAction.Style -> styleOpen = true
        GestureAction.Store -> storeOpen = true
        GestureAction.Agents -> if (runtime.bootstrap.state.value == LinuxState.Installed) agentsOpen = true else linuxOpen = true
        GestureAction.None -> {}
    }
}

/**
 * Dragging up opens the drawer under the finger and dragging down closes it, as on stock launchers: the panel
 * follows the drag and a fling or how far it got decides where it settles. Scrollables inside (a widget's
 * list, the recent apps strip) take their own direction first. [keyboard] says whether a drawer opened by it
 * raises the keyboard.
 */
@Composable
internal fun Modifier.drawerSwipe(
    state: LauncherState,
    enabled: Boolean = true,
    keyboard: Boolean = true,
): Modifier {
    val screenHeight = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    return draggable(
        orientation = Orientation.Vertical,
        enabled = enabled,
        state = rememberDraggableState { dy -> state.dragBy(-dy / screenHeight) },
        // Only a drag that opens it decides; one that closes it leaves the keyboard to the bar.
        onDragStarted = { if (state.drawer.value == 0f) state.keyboardOnOpen = keyboard },
        onDragStopped = { velocity -> state.settle(velocity) },
    )
}

/**
 * Swiping up on home (the pages, the recent apps strip, the bar) does what's picked in gesture settings. The
 * drawer follows the finger; any other action runs once the swipe is long or fast enough. With the drawer open,
 * the swipe drags it, whatever the setting, so it can still be pulled closed.
 */
@Composable
internal fun Modifier.homeSwipe(
    runtime: Runtime,
    state: LauncherState,
    enabled: Boolean = true,
): Modifier {
    val action = runtime.gestures.swipeUp
    val drawerShown by remember(state) { derivedStateOf { state.drawer.value > 0f } }
    if (action.opensDrawer || drawerShown) return drawerSwipe(state, enabled, keyboard = action != GestureAction.Drawer)
    val threshold = with(LocalDensity.current) { SwipeAction.toPx() }
    var dragged by remember { mutableStateOf(0f) }
    return draggable(
        orientation = Orientation.Vertical,
        enabled = enabled && action != GestureAction.None,
        state = rememberDraggableState { dy -> dragged += dy },
        onDragStarted = { dragged = 0f },
        onDragStopped = { velocity ->
            // Up far enough, or flung up without coming back down.
            if (dragged < -threshold || (velocity < -FlingVelocity && dragged < 0f)) state.perform(action, runtime)
        },
    )
}

/** How far a swipe up goes before it runs an action that isn't the drawer. */
private val SwipeAction = 96.dp

/** px/s: a flick this fast counts however short it was, as [LauncherState.settle] counts one for the drawer. */
private const val FlingVelocity = 800f

/** Gesture settings: what swiping up on home does. */
@Composable
fun GesturesScreen(
    runtime: Runtime,
    onDismiss: () -> Unit,
) {
    val gestures = runtime.gestures
    OverlayPanel("gestures", onDismiss, contentWidth = ReadingWidth) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("swipe up on home")
            for (action in GestureAction.entries) {
                val picked = action == gestures.swipeUp
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(LauncherStyle.shape(12.dp))
                        .background(Palette.surface)
                        .clickable { gestures.swipeUp = action }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        BasicText(action.label, style = Mono.copy(color = if (picked) Palette.accent else Palette.foreground, fontSize = TextSize.Item))
                        BasicText(action.description, style = Mono.copy(color = Palette.muted, fontSize = TextSize.Small))
                    }
                    if (picked) Symbol(Glyph.Check, Palette.accent, fontSize = TextSize.Title)
                }
            }
        }
    }
}
