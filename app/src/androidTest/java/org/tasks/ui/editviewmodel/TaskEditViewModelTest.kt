package org.tasks.ui.editviewmodel

import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.SuspendFreeze.Companion.freezeAt
import org.tasks.data.createDueDate
import org.tasks.data.entity.Task
import org.tasks.makers.TaskMaker.newTask
import org.tasks.time.DateTime

@HiltAndroidTest
class TaskEditViewModelTest : BaseTaskEditViewModelTest() {
    @Test
    fun noChangesForNewTask() {
        setup(newTask())

        assertFalse(viewModel.hasChanges())
    }

    @Test
    fun dontSaveTaskWithoutChanges() = runBlocking {
        setup(newTask())

        assertFalse(save())

        assertTrue(taskDao.getAll().isEmpty())
    }

    @Test
    fun settingRecurrenceOnNewTaskAddsDueDate() = runBlocking {
        val task = newTask()
        setup(task)

        freezeAt(DateTime(2021, 2, 4, 14, 56, 34, 126)) {
            viewModel.setRecurrence("RRULE:FREQ=DAILY;INTERVAL=1")
        }
        save()

        assertEquals(
            createDueDate(Task.URGENCY_SPECIFIC_DAY, DateTime(2021, 2, 4).millis),
            taskDao.fetch(task.id)!!.dueDate,
        )
    }

    @Test
    fun dontSaveTaskTwice() = runBlocking {
        setup(newTask())

        viewModel.setPriority(Task.Priority.HIGH)

        assertTrue(save())

        assertFalse(viewModel.save())
    }
}