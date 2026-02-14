package com.panda.ktorwebsocketmvvm.ui.chat

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.panda.ktorwebsocketmvvm.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatActivityUITest {

    @get:Rule
    val activityRule = ActivityScenarioRule(ChatActivity::class.java)

    // ── View Visibility ──────────────────────────────

    @Test
    fun topBar_isDisplayed() {
        onView(withId(R.id.btnSettings)).check(matches(isDisplayed()))
        onView(withId(R.id.btnConnect)).check(matches(isDisplayed()))
        onView(withId(R.id.btnDisconnect)).check(matches(isDisplayed()))
        onView(withId(R.id.tvStatus)).check(matches(isDisplayed()))
    }

    @Test
    fun messageInput_isDisplayed() {
        onView(withId(R.id.etMessage)).check(matches(isDisplayed()))
    }

    @Test
    fun sendButton_isDisplayed() {
        onView(withId(R.id.btnSend)).check(matches(isDisplayed()))
    }

    @Test
    fun recyclerView_isDisplayed() {
        onView(withId(R.id.recyclerMessages)).check(matches(isDisplayed()))
    }

    // ── Initial State ────────────────────────────────

    @Test
    fun initialState_connectEnabled() {
        onView(withId(R.id.btnConnect)).check(matches(isEnabled()))
    }

    @Test
    fun initialState_disconnectDisabled() {
        onView(withId(R.id.btnDisconnect)).check(matches(isNotEnabled()))
    }

    @Test
    fun initialState_sendDisabled() {
        onView(withId(R.id.btnSend)).check(matches(isNotEnabled()))
    }

    @Test
    fun initialState_settingsEnabled() {
        onView(withId(R.id.btnSettings)).check(matches(isEnabled()))
    }

    @Test
    fun initialState_statusShowsDisconnected() {
        onView(withId(R.id.tvStatus)).check(matches(withText(R.string.status_disconnected)))
    }

    // ── Input Interaction ────────────────────────────

    @Test
    fun typeMessage_textAppearsInEditText() {
        onView(withId(R.id.etMessage))
            .perform(typeText("Hello World"), closeSoftKeyboard())
        onView(withId(R.id.etMessage))
            .check(matches(withText("Hello World")))
    }

    @Test
    fun clearMessage_editTextIsEmpty() {
        onView(withId(R.id.etMessage))
            .perform(typeText("temp"), closeSoftKeyboard())
        onView(withId(R.id.etMessage))
            .perform(clearText())
        onView(withId(R.id.etMessage))
            .check(matches(withText("")))
    }

    // ── Settings Dialog ──────────────────────────────

    @Test
    fun settingsButton_opensDialog() {
        onView(withId(R.id.btnSettings)).perform(click())
        onView(withText(R.string.settings_title)).check(matches(isDisplayed()))
    }

    @Test
    fun settingsDialog_hasUsernameField() {
        onView(withId(R.id.btnSettings)).perform(click())
        onView(withId(R.id.etDialogUsername)).check(matches(isDisplayed()))
    }

    @Test
    fun settingsDialog_hasHostField() {
        onView(withId(R.id.btnSettings)).perform(click())
        onView(withId(R.id.etDialogHost)).check(matches(isDisplayed()))
    }

    @Test
    fun settingsDialog_hasPortField() {
        onView(withId(R.id.btnSettings)).perform(click())
        onView(withId(R.id.etDialogPort)).check(matches(isDisplayed()))
    }

    @Test
    fun settingsDialog_hasThemeSwitch() {
        onView(withId(R.id.btnSettings)).perform(click())
        onView(withId(R.id.switchTheme)).check(matches(isDisplayed()))
    }

    @Test
    fun settingsDialog_cancelCloses() {
        onView(withId(R.id.btnSettings)).perform(click())
        onView(withText(R.string.btn_cancel)).perform(click())
        // Dialog closed, main UI visible again
        onView(withId(R.id.btnConnect)).check(matches(isDisplayed()))
    }
}
