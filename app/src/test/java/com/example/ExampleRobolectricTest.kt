package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ConversationEntity
import com.example.data.local.KhotwaDatabase
import com.example.data.local.MessageEntity
import com.example.service.GuidanceSessionCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify app name is Khotwa AI in Arabic`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("خُطوة AI", appName)
    }

    @Test
    fun `guidance session coordinator starts advances and stops immediately`() {
        GuidanceSessionCoordinator.startSession(
            goal = "تفعيل التحقق بخطوتين في الإعدادات",
            showFloatingHud = true
        )
        assertTrue(GuidanceSessionCoordinator.sessionState.value.isActive)
        assertEquals(1, GuidanceSessionCoordinator.sessionState.value.currentStepNumber)

        val practiceSnapshot = GuidanceSessionCoordinator.createPracticeSnapshotForGoal(
            goal = "تفعيل المصادقة الثنائية",
            stepNumber = 1
        )
        assertFalse(practiceSnapshot.isProtectedOrPasswordScreen)
        assertTrue(practiceSnapshot.elements.isNotEmpty())

        GuidanceSessionCoordinator.markCurrentStepDoneAndAdvance()
        assertEquals(2, GuidanceSessionCoordinator.sessionState.value.currentStepNumber)

        GuidanceSessionCoordinator.stopSessionImmediately()
        assertFalse(GuidanceSessionCoordinator.sessionState.value.isActive)
    }

    @Test
    fun `room database persists and clears conversations and messages`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, KhotwaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.khotwaDao()

        val convId = dao.insertConversation(
            ConversationEntity(
                title = "خطة تعلم البرمجة",
                primaryIntent = "تخطيط خطوة بخطوة",
                messageCount = 2
            )
        )

        dao.insertMessage(
            MessageEntity(
                conversationId = convId,
                isUser = true,
                content = "خطط لي تعلم Kotlin",
                intentName = "TASK_PLANNING"
            )
        )
        dao.insertMessage(
            MessageEntity(
                conversationId = convId,
                isUser = false,
                content = "إليك خطة عملية من 3 خطوات",
                intentName = "TASK_PLANNING",
                modelUsed = "gemini-3.8-flash",
                ttftMs = 310L,
                totalDurationMs = 820L
            )
        )

        val messages = dao.getMessagesOnce(convId)
        assertEquals(2, messages.size)
        assertEquals("gemini-3.8-flash", messages[1].modelUsed)

        dao.clearAllConversations()
        val afterClear = dao.getMessagesOnce(convId)
        assertTrue(afterClear.isEmpty())
        db.close()
    }
}
