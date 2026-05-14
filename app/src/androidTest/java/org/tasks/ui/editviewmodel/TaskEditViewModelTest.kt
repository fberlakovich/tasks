package org.tasks.ui.editviewmodel

import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.SuspendFreeze.Companion.freezeAt
import org.tasks.data.createDueDate
import org.tasks.data.entity.Place
import org.tasks.data.entity.TagData
import org.tasks.data.entity.Task
import org.tasks.data.entity.TaskListDefaults
import org.tasks.data.entity.TaskListMetadata
import org.tasks.data.entity.setDefaults
import org.tasks.filters.key
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
    fun savingNewSubtaskPersistsListDefaultTagsAndLocation() = runBlocking {
        val list = defaultFilterProvider.getDefaultList()
        val tag = TagData(name = "Work")
        tagDataDao.insert(tag)
        val place = Place(uid = "office", name = "Office")
        locationDao.insert(place)
        taskListMetadataDao.createNew(
            TaskListMetadata().apply {
                filter = list.key()
                tagUuid = null
                setDefaults(
                    TaskListDefaults(
                        tagUids = listOf(tag.remoteId!!),
                        locationUid = place.uid,
                        locationReminder = 1,
                    )
                )
            }
        )
        setup(newTask())
        val subtask = taskCreator.createWithValues(list, "Subtask")

        viewModel.setSubtasks(listOf(subtask))

        assertTrue(save())
        assertEquals(listOf("Work"), db.tagDao().getTagsForTask(subtask.id).map { it.name })
        val location = locationDao.getGeofences(subtask.id)!!
        assertEquals(place.uid, location.place.uid)
        assertTrue(location.geofence.isArrival)
        assertFalse(location.geofence.isDeparture)
    }

    @Test
    fun dontSaveTaskTwice() = runBlocking {
        setup(newTask())

        viewModel.setPriority(Task.Priority.HIGH)

        assertTrue(save())

        assertFalse(viewModel.save())
    }
}