package app.vnotes

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/** Exercise the real native views and theme resources without needing microphone hardware. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DesignUiTest {
    private var controller: ActivityController<MainActivity>? = null
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("settings", 0)
    @Before fun reset() {
        prefs.edit().clear().commit()
        val store = NoteStore(context)
        try { store.writableDatabase.delete("notes", null, null) } finally { store.close() }
    }
    @After fun cleanup() { controller?.pause()?.stop()?.destroy() }
    private fun start(material: Boolean = false, state: Bundle? = null): MainActivity {
        prefs.edit().putBoolean("material3", material).commit()
        return Robolectric.buildActivity(MainActivity::class.java).also { controller = it }
            .create(state).start().resume().visible().get()
    }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    private fun views(activity: MainActivity) = descendants(activity.findViewById(android.R.id.content))
    private fun add(activity: MainActivity, text: String) {
        views(activity).filterIsInstance<EditText>().single().setText(text)
        views(activity).single { it.contentDescription == "Add note" }.performClick()
    }
    @Test fun originalStaysDefaultAndShortListsAreBottomAnchoredNewestFirst() {
        val activity = start()
        assertTrue(views(activity).none { it is MaterialButton })
        add(activity, "First note")
        add(activity, "Second note")
        val scroll = views(activity).filterIsInstance<ScrollView>().single()
        assertTrue(scroll.isFillViewport)
        val content = scroll.getChildAt(0) as LinearLayout
        assertEquals(Gravity.BOTTOM, content.gravity and Gravity.VERTICAL_GRAVITY_MASK)
        val rows = content.getChildAt(0) as LinearLayout
        val text = descendants(rows).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue(text.indexOf("Second note") < text.indexOf("First note"))
        scroll.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY))
        scroll.layout(0, 0, 360, 700)
        assertTrue("Notes should sit above the composer, leaving space above", rows.top > 0)
    }
    @Test fun materialThemeUsesNativeMaterialWidgetsAcrossListAndSettings() {
        val activity = start(material = true)
        add(activity, "Material note")
        val screen = views(activity)
        assertEquals(1, screen.filterIsInstance<MaterialButtonToggleGroup>().size)
        assertEquals(2, screen.filterIsInstance<FloatingActionButton>().size)
        assertEquals(1, screen.filterIsInstance<MaterialCardView>().size)
        assertEquals(1, screen.filterIsInstance<TextInputLayout>().size)
        screen.single { it.contentDescription == "Settings" }.performClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val settings = descendants(dialog.window!!.decorView)
        assertTrue(settings.filterIsInstance<MaterialSwitch>().single().isChecked)
        assertEquals(1, settings.filterIsInstance<Slider>().size)
        assertEquals(1, settings.filterIsInstance<TextInputLayout>().size)
        dialog.dismiss()
    }
    @Test fun changingDesignPersistsAndPreservesDraftAndSelectedTab() {
        val activity = start()
        views(activity).filterIsInstance<EditText>().single().setText("Unsent draft")
        views(activity).filterIsInstance<Button>().single { it.text == "DONE" }.performClick()
        views(activity).single { it.contentDescription == "Settings" }.performClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        descendants(dialog.window!!.decorView).filterIsInstance<CompoundButton>().single().performClick()
        assertTrue(prefs.getBoolean("material3", false))
        // Let Android's actual recreation path save/restore the Activity and apply its theme.
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val restored = controller!!.get()
        assertEquals(1, views(restored).filterIsInstance<MaterialButtonToggleGroup>().size)
        assertEquals("Unsent draft", views(restored).filterIsInstance<EditText>().single().text.toString())
        assertTrue(views(restored).any { it.contentDescription == "Erase all done notes" })
    }
    @Test fun completionSwipeMovesCardAndCancellationResetsIt() {
        val activity = start(material = true)
        add(activity, "Swipe this card")
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 360, 720)
        val card = views(activity).filterIsInstance<MaterialCardView>().single()
        val row = card.getChildAt(0)
        val distance = 100f * activity.resources.displayMetrics.density
        fun touch(action: Int, x: Float) {
            val event = android.view.MotionEvent.obtain(0, 10, action, x, 20f, 0)
            try { row.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        touch(android.view.MotionEvent.ACTION_DOWN, 10f)
        touch(android.view.MotionEvent.ACTION_MOVE, 10f + distance)
        assertTrue("The outer card must follow the finger", card.translationX > 0f)
        assertEquals("Text and menu stay inside the moving card", 0f, row.translationX, .01f)
        touch(android.view.MotionEvent.ACTION_CANCEL, 10f + distance)
        assertEquals(0f, card.translationX, .01f)
        touch(android.view.MotionEvent.ACTION_DOWN, 10f)
        touch(android.view.MotionEvent.ACTION_MOVE, 10f + distance)
        touch(android.view.MotionEvent.ACTION_UP, 10f + distance)
        val store = NoteStore(context)
        try { assertNotNull(store.all().single().deleted) } finally { store.close() }
        assertTrue(views(activity).none { it is MaterialCardView })
    }
    @Test fun tabSlidesInBothDirectionsAndRapidReversalLeavesOnePage() {
        for (material in listOf(false, true)) {
            val store = NoteStore(context)
            try {
                store.writableDatabase.delete("notes", null, null)
                store.save(Note("todo", "Active page", 1, 1, null))
                store.save(Note("done", "Completed page", 2, 2, 3))
            } finally { store.close() }
            val activity = start(material)
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 360, 720)
            fun tab(name: String) = views(activity).filterIsInstance<Button>().single { it.text == name }
            tab("DONE").performClick()
            var pages = views(activity).filterIsInstance<ScrollView>()
            assertEquals(2, pages.size)
            assertTrue("DONE enters from the right", pages.last().translationX > 0f)
            tab("TODO").performClick()
            pages = views(activity).filterIsInstance<ScrollView>()
            assertEquals("Reversal must discard the stale outgoing page", 2, pages.size)
            assertTrue("TODO enters from the left", pages.last().translationX < 0f)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))
            pages = views(activity).filterIsInstance<ScrollView>()
            assertEquals(1, pages.size)
            assertEquals(0f, pages.single().translationX, .01f)
            assertTrue(views(activity).filterIsInstance<TextView>().any { it.text == "Active page" })
            assertFalse(views(activity).filterIsInstance<TextView>().any { it.text == "Completed page" })
            controller!!.pause().stop().destroy(); controller = null
        }
    }

}
