package org.tasks.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.Task
import org.tasks.data.entity.TaskListDefaults
import org.tasks.data.entity.TaskListMetadata
import org.tasks.data.entity.getDefaults
import org.tasks.data.entity.setDefaults

class TaskListDefaultsTest {
    @Test
    fun emptyDefaultsAreStoredAsNull() {
        val metadata = TaskListMetadata()

        metadata.setDefaults(TaskListDefaults.EMPTY)

        assertNull(metadata.defaults)
        assertEquals(TaskListDefaults.EMPTY, metadata.getDefaults())
    }

    @Test
    fun explicitEmptyReminderListRoundTripsAsOverride() {
        val metadata = TaskListMetadata()

        metadata.setDefaults(TaskListDefaults(alarms = emptyList()))

        assertEquals(TaskListDefaults(alarms = emptyList()), metadata.getDefaults())
    }

    @Test
    fun explicitNoneValuesRoundTripAsOverrides() {
        val defaults = TaskListDefaults(
            priority = Task.Priority.NONE,
            dueDate = Task.URGENCY_NONE,
            hideUntil = Task.HIDE_UNTIL_NONE,
            tagUids = emptyList(),
            calendarId = TaskListDefaults.NO_CALENDAR,
            recurrence = TaskListDefaults.NO_RECURRENCE,
            repeatFrom = Task.RepeatFrom.DUE_DATE,
            randomReminderHours = 0,
            ringMode = 0,
            locationUid = TaskListDefaults.NO_LOCATION,
            locationReminder = 0,
        )
        val metadata = TaskListMetadata()

        metadata.setDefaults(defaults)

        assertEquals(defaults, metadata.getDefaults())
    }

    @Test
    fun alarmDefaultsRoundTripWithoutTaskIdentity() {
        val metadata = TaskListMetadata()

        metadata.setDefaults(
            TaskListDefaults(
                alarms = listOf(Alarm.whenDue(42), Alarm.whenStarted(42)),
            )
        )

        assertEquals(
            TaskListDefaults(
                alarms = listOf(Alarm.whenDue(0), Alarm.whenStarted(0)),
            ),
            metadata.getDefaults(),
        )
    }

    @Test
    fun invalidJsonFallsBackToEmptyDefaults() {
        val metadata = TaskListMetadata().apply {
            defaults = "not json"
        }

        assertEquals(TaskListDefaults.EMPTY, metadata.getDefaults())
    }
}
