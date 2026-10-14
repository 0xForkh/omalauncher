package dev.omalauncher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.omalauncher.ui.Palette
import dev.omalauncher.ui.SettingsPage
import dev.omalauncher.ui.SettingsPages
import dev.omalauncher.ui.applySystemBars

/**
 * The launcher's settings and the screens they open (style, agents, store, Linux, …):
 * their own task and Recents card, so going home or to a terminal and back finds
 * them as they were. Home, widgets and anything that needs Linux open a screen
 * directly; closing the last one closes the task.
 */
class SettingsActivity : ComponentActivity() {
    private val runtime get() = (application as App).runtime

    private val stack = mutableStateListOf<SettingsPage>()

    /** The home page that was showing; the store puts widgets there. */
    private var homePage by mutableIntStateOf(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        open(intent)
        applySystemBars(this, Palette.isLight)
        setContent {
            LaunchedEffect(Palette.isLight) { applySystemBars(this@SettingsActivity, Palette.isLight) }
            if (!closing) SettingsPages(runtime, stack, homePage, onClose = ::close)
        }
    }

    /** Set once the last screen closed and the window is see-through: nothing more is drawn. */
    private var closing by mutableStateOf(false)
    private var closeAsked = false

    /**
     * Back from the last screen. Android slides a closing task away, whatever the
     * app asks, so the window turns see-through and empty first: what slides is invisible.
     */
    private fun close() {
        if (closeAsked) return
        closeAsked = true
        if (Build.VERSION.SDK_INT < 30) return finishAndRemoveTask()
        setTranslucent(true)
        // Until the system has made it see-through, an empty window shows black.
        window.decorView.postDelayed({
            closing = true
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.decorView.postOnAnimation { finishAndRemoveTask() }
        }, 80)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        open(intent)
    }

    /**
     * Settings again comes back to where you were in them. A screen asked for by
     * name (a widget's link, Linux for a terminal) shows on its own: back from it
     * returns to whatever asked.
     */
    private fun open(intent: Intent) {
        homePage = intent.getIntExtra(EXTRA_HOME_PAGE, homePage)
        val page = intent.getStringExtra(EXTRA_PAGE)?.let { name -> SettingsPage.entries.firstOrNull { it.name == name } } ?: SettingsPage.Settings
        if (page == SettingsPage.Settings && stack.firstOrNull() == SettingsPage.Settings) return
        stack.clear()
        stack += page
    }

    companion object {
        private const val EXTRA_PAGE = "page"
        private const val EXTRA_HOME_PAGE = "home_page"

        fun intent(
            context: Context,
            page: SettingsPage,
            homePage: Int?,
        ): Intent =
            Intent(context, SettingsActivity::class.java)
                .putExtra(EXTRA_PAGE, page.name)
                .apply { if (homePage != null) putExtra(EXTRA_HOME_PAGE, homePage) }
    }
}
