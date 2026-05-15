package com.todoroo.astrid.service

import com.todoroo.astrid.api.PermaSql.VALUE_EOD
import com.todoroo.astrid.api.PermaSql.VALUE_EOD_NEXT_WEEK
import com.todoroo.astrid.api.PermaSql.VALUE_EOD_TOMORROW
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavCalendar
import org.tasks.data.entity.Place
import org.tasks.data.entity.TagData
import org.tasks.data.entity.Task
import org.tasks.data.entity.Task.Companion.DUE_DATE
import org.tasks.data.entity.Task.Companion.HIDE_UNTIL
import org.tasks.data.entity.Task.Companion.URGENCY_SPECIFIC_DAY
import org.tasks.data.entity.TaskListDefaults
import org.tasks.data.entity.TaskListMetadata
import org.tasks.data.entity.setDefaults
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.tasks.R
import org.tasks.SuspendFreeze.Companion.freezeAt
import org.tasks.data.createDueDate
import org.tasks.data.dao.LocationDao
import org.tasks.data.dao.TaskListMetadataDao
import org.tasks.data.dao.TagDataDao
import org.tasks.data.getDefaultAlarms
import org.tasks.filters.CaldavFilter
import org.tasks.filters.TodayFilter
import org.tasks.filters.key
import org.tasks.injection.InjectingTestCase
import org.tasks.preferences.DefaultFilterProvider
import org.tasks.preferences.Preferences
import org.tasks.time.DateTime
import org.tasks.time.ONE_HOUR
import javax.inject.Inject

@HiltAndroidTest
class TaskCreatorTest : InjectingTestCase() {
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var taskCreator: TaskCreator
    @Inject lateinit var taskListMetadataDao: TaskListMetadataDao
    @Inject lateinit var defaultFilterProvider: DefaultFilterProvider
    @Inject lateinit var tagDataDao: TagDataDao
    @Inject lateinit var locationDao: LocationDao

    @Test
    fun setStartAndDueFromFilter() = runBlocking {
        val task = freezeAt(DateTime(2021, 2, 4, 14, 56, 34, 126)) {
            taskCreator.create(mapOf(
                    HIDE_UNTIL.name!! to VALUE_EOD,
                    DUE_DATE.name!! to VALUE_EOD_TOMORROW
            ), null)
        }

        assertEquals(DateTime(2021, 2, 4).millis, task.hideUntil)
        assertEquals(
                createDueDate(URGENCY_SPECIFIC_DAY, DateTime(2021, 2, 5).millis),
                task.dueDate
        )
    }

    @Test
    fun setDefaultStartWithFilterDue() = runBlocking {
        preferences.setString(R.string.p_default_hideUntil_key, Task.HIDE_UNTIL_DUE.toString())
        val task = freezeAt(DateTime(2021, 2, 4, 14, 56, 34, 126)) {
            taskCreator.create(mapOf(
                    DUE_DATE.name!! to VALUE_EOD
            ), null)
        }

        assertEquals(DateTime(2021, 2, 4).millis, task.hideUntil)
    }

    @Test
    fun setStartAndDueFromPreferences() = runBlocking {
        preferences.setString(R.string.p_default_urgency_key, Task.URGENCY_TODAY.toString())
        preferences.setString(R.string.p_default_hideUntil_key, Task.HIDE_UNTIL_DUE.toString())

        val task = freezeAt(DateTime(2021, 2, 4, 14, 56, 34, 126)) {
            taskCreator.create(null, "test")
        }

        assertEquals(DateTime(2021, 2, 4).millis, task.hideUntil)
        assertEquals(
                createDueDate(URGENCY_SPECIFIC_DAY, DateTime(2021, 2, 4).millis),
                task.dueDate
        )
    }

    @Test
    fun filterStartOverridesDefaultStart() = runBlocking {
        preferences.setString(R.string.p_default_urgency_key, Task.URGENCY_TODAY.toString())
        preferences.setString(R.string.p_default_hideUntil_key, Task.HIDE_UNTIL_DUE.toString())

        val task = freezeAt(DateTime(2021, 2, 4, 14, 56, 34, 126)) {
            taskCreator.create(mapOf(
                    HIDE_UNTIL.name!! to VALUE_EOD_NEXT_WEEK
            ), null)
        }

        assertEquals(DateTime(2021, 2, 11).millis, task.hideUntil)
    }

    @Test
    fun filterDueOverridesDefaultDue() = runBlocking {
        preferences.setString(R.string.p_default_urgency_key, Task.URGENCY_TODAY.toString())

        val task = freezeAt(DateTime(2021, 2, 4, 14, 56, 34, 126)) {
            taskCreator.create(mapOf(
                    DUE_DATE.name!! to VALUE_EOD_TOMORROW
            ), null)
        }

        assertEquals(
                createDueDate(URGENCY_SPECIFIC_DAY, DateTime(2021, 2, 5).millis),
                task.dueDate
        )
    }

    @Test
    fun listDefaultDueDateOverridesGlobalDefault() = runBlocking {
        preferences.setString(R.string.p_default_urgency_key, Task.URGENCY_TODAY.toString())
        val filter = listFilter("shopping")
        setListDefaults(filter, TaskListDefaults(dueDate = Task.URGENCY_NONE))

        val task = freezeAt(DateTime(2021, 2, 4, 14, 56, 34, 126)) {
            taskCreator.createWithValues(filter, "test")
        }

        assertEquals(0L, task.dueDate)
    }

    @Test
    fun listDefaultsInheritUnspecifiedFields() = runBlocking {
        preferences.setString(R.string.p_default_urgency_key, Task.URGENCY_TODAY.toString())
        val filter = listFilter("inbox")
        setListDefaults(filter, TaskListDefaults(priority = Task.Priority.HIGH))

        val task = freezeAt(DateTime(2021, 2, 4, 14, 56, 34, 126)) {
            taskCreator.createWithValues(filter, "test")
        }

        assertEquals(Task.Priority.HIGH, task.priority)
        assertEquals(
            createDueDate(URGENCY_SPECIFIC_DAY, DateTime(2021, 2, 4).millis),
            task.dueDate
        )
    }

    @Test
    fun listDefaultsApplyStartDateRandomReminderAndReminderMode() = runBlocking {
        val filter = listFilter("work")
        setListDefaults(
            filter,
            TaskListDefaults(
                dueDate = Task.URGENCY_TODAY,
                hideUntil = Task.HIDE_UNTIL_DUE,
                randomReminderHours = 24,
                ringMode = Task.NOTIFY_MODE_FIVE,
            )
        )

        val task = freezeAt(DateTime(2021, 2, 4, 14, 56, 34, 126)) {
            taskCreator.createWithValues(filter, "test")
        }

        assertEquals(
            createDueDate(URGENCY_SPECIFIC_DAY, DateTime(2021, 2, 4).millis),
            task.dueDate,
        )
        assertEquals(DateTime(2021, 2, 4).millis, task.hideUntil)
        assertEquals(24 * ONE_HOUR, task.randomReminder)
        assertEquals(Task.NOTIFY_MODE_FIVE, task.ringFlags)
    }

    @Test
    fun emptyListReminderOverrideDisablesGlobalDefaultReminders() = runBlocking {
        preferences.setDefaultAlarms(listOf(Alarm.whenDue(0)))
        val filter = listFilter("shopping")
        setListDefaults(
            filter,
            TaskListDefaults(
                dueDate = Task.URGENCY_TODAY,
                alarms = emptyList(),
            )
        )

        val task = freezeAt(DateTime(2021, 2, 4, 14, 56, 34, 126)) {
            taskCreator.createWithValues(filter, "test")
        }

        assertEquals(
            createDueDate(URGENCY_SPECIFIC_DAY, DateTime(2021, 2, 4).millis),
            task.dueDate
        )
        assertEquals(emptyList<Alarm>(), task.getDefaultAlarms(defaultRemindersEnabled = true))
    }

    @Test
    fun listDefaultRepeatFromAppliesWithoutDefaultRecurrence() = runBlocking {
        preferences.setString(R.string.p_default_recurrence, null)
        val filter = listFilter("work")
        setListDefaults(
            filter,
            TaskListDefaults(repeatFrom = Task.RepeatFrom.COMPLETION_DATE)
        )

        val task = taskCreator.createWithValues(filter, "test")

        assertEquals(null, task.recurrence)
        assertEquals(Task.RepeatFrom.COMPLETION_DATE, task.repeatFrom)
    }

    @Test
    fun listDefaultsApplyTagsRecurrenceAndLocation() = runBlocking {
        val tag = TagData(name = "Errands")
        tagDataDao.insert(tag)
        val place = Place(uid = "store", name = "Store")
        locationDao.insert(place)
        val recurrence = "RRULE:FREQ=WEEKLY;INTERVAL=1"
        val filter = listFilter("shopping")
        setListDefaults(
            filter,
            TaskListDefaults(
                tagUids = listOf(tag.remoteId!!),
                recurrence = recurrence,
                repeatFrom = Task.RepeatFrom.COMPLETION_DATE,
                locationUid = place.uid,
                locationReminder = 2,
            )
        )

        val task = taskCreator.createWithValues(filter, "test")

        assertEquals(listOf("Errands"), task.tags)
        assertEquals(recurrence, task.recurrence)
        assertEquals(Task.RepeatFrom.COMPLETION_DATE, task.repeatFrom)
        assertEquals(place.uid, task.getTransitory<String>(Place.KEY))
    }

    @Test
    fun quickAddWithListDefaultLocationCreatesGeofenceForTask() = runBlocking {
        val place = Place(uid = "store", name = "Store")
        locationDao.insert(place)
        val defaultList = defaultFilterProvider.getDefaultList()
        setListDefaults(
            defaultList,
            TaskListDefaults(
                locationUid = place.uid,
                locationReminder = 2,
            )
        )

        val task = taskCreator.basicQuickAddTask("test")

        val location = locationDao.getGeofences(task.id)!!
        assertEquals(task.id, location.geofence.task)
        assertEquals(place.uid, location.place.uid)
        assertEquals(false, location.geofence.isArrival)
        assertEquals(true, location.geofence.isDeparture)
    }

    @Test
    fun listDefaultsCanDisableGlobalTagsRecurrenceAndLocation() = runBlocking {
        val tag = TagData(name = "Home")
        tagDataDao.insert(tag)
        val place = Place(uid = "home", name = "Home")
        locationDao.insert(place)
        preferences.setString(R.string.p_default_tags, tag.remoteId)
        preferences.setString(R.string.p_default_recurrence, "RRULE:FREQ=DAILY;INTERVAL=1")
        preferences.setString(R.string.p_default_location, place.uid)
        val filter = listFilter("shopping")
        setListDefaults(
            filter,
            TaskListDefaults(
                tagUids = emptyList(),
                recurrence = TaskListDefaults.NO_RECURRENCE,
                locationUid = TaskListDefaults.NO_LOCATION,
            )
        )

        val task = taskCreator.createWithValues(filter, "test")

        assertEquals(emptyList<String>(), task.tags)
        assertEquals(null, task.recurrence)
        assertEquals(false, task.hasTransitory(Place.KEY))
    }

    @Test
    fun nonListFilterUsesDefaultListDefaultsBeforeFilterValues() = runBlocking {
        val defaultList = defaultFilterProvider.getDefaultList()
        setListDefaults(
            defaultList,
            TaskListDefaults(
                priority = Task.Priority.HIGH,
                dueDate = Task.URGENCY_NONE,
            )
        )

        val task = freezeAt(DateTime(2021, 2, 4, 14, 56, 34, 126)) {
            taskCreator.createWithValues(TodayFilter.create(), "test")
        }

        assertEquals(Task.Priority.HIGH, task.priority)
        assertEquals(
            createDueDate(URGENCY_SPECIFIC_DAY, DateTime(2021, 2, 4).millis),
            task.dueDate,
        )
    }

    @Test
    fun invalidListDefaultDateValuesFallBackToGlobalDefaults() = runBlocking {
        preferences.setString(R.string.p_default_urgency_key, Task.URGENCY_TODAY.toString())
        preferences.setString(R.string.p_default_hideUntil_key, Task.HIDE_UNTIL_NONE.toString())
        val filter = listFilter("broken")
        setListDefaults(
            filter,
            TaskListDefaults(
                dueDate = 999,
                hideUntil = 999,
            )
        )

        val task = freezeAt(DateTime(2021, 2, 4, 14, 56, 34, 126)) {
            taskCreator.createWithValues(filter, "test")
        }

        assertEquals(
            createDueDate(URGENCY_SPECIFIC_DAY, DateTime(2021, 2, 4).millis),
            task.dueDate,
        )
        assertEquals(0L, task.hideUntil)
    }

    @Test
    fun quickAddWithoutFilterUsesDefaultListDefaults() = runBlocking {
        preferences.setString(R.string.p_default_urgency_key, Task.URGENCY_TODAY.toString())
        val defaultList = defaultFilterProvider.getDefaultList()
        setListDefaults(
            defaultList,
            TaskListDefaults(
                priority = Task.Priority.HIGH,
                dueDate = Task.URGENCY_NONE,
            )
        )

        val task = freezeAt(DateTime(2021, 2, 4, 14, 56, 34, 126)) {
            taskCreator.basicQuickAddTask("test")
        }

        assertEquals(Task.Priority.HIGH, task.priority)
        assertEquals(0L, task.dueDate)
    }

    private suspend fun setListDefaults(filter: CaldavFilter, defaults: TaskListDefaults) {
        taskListMetadataDao.createNew(
            TaskListMetadata().apply {
                this.filter = filter.key()
                tagUuid = null
                setDefaults(defaults)
            }
        )
    }

    private fun listFilter(name: String): CaldavFilter = CaldavFilter(
        calendar = CaldavCalendar(
            account = "account",
            uuid = name,
            name = name,
        ),
        account = CaldavAccount(
            uuid = "account",
            accountType = CaldavAccount.TYPE_LOCAL,
        )
    )
}