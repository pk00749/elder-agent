package com.elder.android.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.elder.android.data.db.ElderDatabase
import com.elder.android.testing.ElderRobolectricTestRunner
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class InterviewRepositoryTest {
    private lateinit var db: ElderDatabase
    private lateinit var repo: InterviewRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ElderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = InterviewRepository(db.interviewSessionDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `active session roundtrips turns and draft`() = runTest {
        val created = repo.create(now = 10L)
        val updated = created.copy(
            status = InterviewStatus.REVIEWING,
            turns = listOf(
                InterviewTurn(
                    turnNo = 1,
                    elderText = "今天和老张下棋",
                    assistantText = "赢了吗？",
                    createdAt = 11L,
                )
            ),
            draftText = "今天和老张下棋，赢了。",
            draftSummary = "和老张下棋赢了",
            updatedAt = 12L,
        )
        repo.save(updated)

        val loaded = repo.active()
        assertEquals(updated.id, loaded?.id)
        assertEquals(InterviewStatus.REVIEWING, loaded?.status)
        assertEquals("赢了吗？", loaded?.turns?.single()?.assistantText)
        assertEquals("和老张下棋赢了", loaded?.draftSummary)
    }
}
