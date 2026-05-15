package org.tasks.ui.editviewmodel

import com.natpryce.makeiteasy.MakeItEasy.with
import org.tasks.data.setDefaultReminders
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.tasks.R
import org.tasks.SuspendFreeze.Companion.freezeAt
import org.tasks.data.createDueDate
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.Alarm.Companion.whenDue
import org.tasks.data.entity.Alarm.Companion.whenOverdue
import org.tasks.data.entity.Alarm.Companion.whenStarted
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.CaldavCalendar
import org.tasks.data.entity.Place
import org.tasks.data.entity.Tag
import org.tasks.data.entity.TagData
import org.tasks.data.entity.Task
import org.tasks.data.entity.TaskListDefaults
import org.tasks.data.entity.TaskListMetadata
import org.tasks.data.entity.setDefaults
import org.tasks.filters.CaldavFilter
import org.tasks.filters.TodayFilter
import org.tasks.filters.key
import org.tasks.makers.TaskMaker.DUE_TIME
import org.tasks.makers.TaskMaker.START_DATE
import org.tasks.makers.TaskMaker.newTask
import org.tasks.time.DateTime
import org.tasks.time.DateTimeUtils2.currentTimeMillis
@HiltAndroidTest
class ReminderTests : BaseTaskEditViewModelTest() {
    @Test
    fun whenStartReminder() = runBlocking {
        preferences.setDefaultAlarms(listOf(whenStarted(0)))
        val task = newTask(with(START_DATE, DateTime()))
        task.setDefaultReminders(preferences)

        setup(task)

        assertEquals(
            persistentSetOf(Alarm(type = Alarm.TYPE_REL_START)),
            viewModel.viewState.value.alarms
        )
    }

    @Test
    fun whenDueReminder() = runBlocking {
        preferences.setDefaultAlarms(listOf(whenDue(0)))
        val task = newTask(with(DUE_TIME, DateTime()))
        task.setDefaultReminders(preferences)

        setup(task)

        assertEquals(
            persistentSetOf(Alarm(type = Alarm.TYPE_REL_END)),
            viewModel.viewState.value.alarms
        )
    }

    @Test
    fun whenOverDueReminder() = runBlocking {
        preferences.setDefaultAlarms(listOf(whenOverdue(0)))
        val task = newTask(with(DUE_TIME, DateTime()))
        task.setDefaultReminders(preferences)

        setup(task)

        assertEquals(
            persistentSetOf(whenOverdue(0)),
            viewModel.viewState.value.alarms
        )
    }

    @Test
    fun ringFiveTimes() = runBlocking {
        val task = newTask()
        setup(task)

        viewModel.ringFiveTimes = true

        save()

        assertTrue(taskDao.fetch(task.id)!!.isNotifyModeFive)
    }

    @Test
    fun ringNonstop() = runBlocking {
        val task = newTask()
        setup(task)

        viewModel.ringNonstop = true

        save()

        assertTrue(taskDao.fetch(task.id)!!.isNotifyModeNonstop)
    }

    @Test
    fun ringFiveTimesCantRingNonstop() = runBlocking {
        val task = newTask()
        setup(task)

        viewModel.ringNonstop = true
        viewModel.ringFiveTimes = true

        save()

        assertFalse(taskDao.fetch(task.id)!!.isNotifyModeNonstop)
        assertTrue(taskDao.fetch(task.id)!!.isNotifyModeFive)
    }

    @Test
    fun ringNonStopCantRingFiveTimes() = runBlocking {
        val task = newTask()
        setup(task)

        viewModel.ringFiveTimes = true
        viewModel.ringNonstop = true

        save()

        assertFalse(taskDao.fetch(task.id)!!.isNotifyModeFive)
        assertTrue(taskDao.fetch(task.id)!!.isNotifyModeNonstop)
    }

    @Test
    fun noDefaultRemindersWithNoDates() = runBlocking {
        val task = newTask()
        task.setDefaultReminders(preferences)

        setup(task)

        save()

        assertTrue(alarmDao.getAlarms(task.id).isEmpty())
    }

    @Test
    fun addDefaultRemindersWhenAddingDueDate() = runBlocking {
        val task = setupWithDefaultAlarms(whenDue(0), whenOverdue(0))

        viewModel.setDueDate(
            createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, currentTimeMillis())
        )

        save()

        assertEquals(
            listOf(whenDue(1).copy(id = 1), whenOverdue(1).copy(id = 2)),
            alarmDao.getAlarms(task.id)
        )
    }

    @Test
    fun addDefaultRemindersWhenAddingStartDate() = runBlocking {
        val task = setupWithDefaultAlarms(whenStarted(0))

        viewModel.setStartDate(
            createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, currentTimeMillis())
        )

        save()

        assertEquals(
            listOf(whenStarted(1).copy(id = 1)),
            alarmDao.getAlarms(task.id)
        )
    }

    @Test
    fun addRemindersWhenAddingAllDayDueDate() = runBlocking {
        val task = setupWithDefaultAlarms(whenDue(0))

        viewModel.setDueDate(
            createDueDate(Task.URGENCY_SPECIFIC_DAY, currentTimeMillis())
        )

        assertAlarms(whenDue(0).copy(task = task.id))
    }

    @Test
    fun dontAddRemindersWhenAddingAllDayDueDateToggleOff() = runBlocking {
        disableAllDayReminders()
        setupWithDefaultAlarms(whenDue(0))

        viewModel.setDueDate(
            createDueDate(Task.URGENCY_SPECIFIC_DAY, currentTimeMillis())
        )

        assertNoAlarms()
    }

    @Test
    fun addRemindersWhenAddingTimedDueDateToggleOff() = runBlocking {
        disableAllDayReminders()
        val task = setupWithDefaultAlarms(whenDue(0))

        viewModel.setDueDate(
            createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, currentTimeMillis())
        )

        assertAlarms(whenDue(0).copy(task = task.id))
    }

    @Test
    fun addRemindersWhenChangingAllDayToTimedToggleOff() = runBlocking {
        disableAllDayReminders()
        val task = setupWithDefaultAlarms(whenDue(0))

        viewModel.setDueDate(
            createDueDate(Task.URGENCY_SPECIFIC_DAY, currentTimeMillis())
        )
        viewModel.setDueDate(
            createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, currentTimeMillis())
        )

        assertAlarms(whenDue(0).copy(task = task.id))
    }

    @Test
    fun dontAddRemindersWhenChangingAllDayToTimedToggleOn() = runBlocking {
        val task = setupWithDefaultAlarms(whenDue(0))

        viewModel.setDueDate(
            createDueDate(Task.URGENCY_SPECIFIC_DAY, currentTimeMillis())
        )
        viewModel.setDueDate(
            createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, currentTimeMillis())
        )

        // reminders were already added when going from no date -> all day
        assertAlarms(whenDue(0).copy(task = task.id))
    }

    @Test
    fun addRemindersWhenAddingAllDayStartDate() = runBlocking {
        val task = setupWithDefaultAlarms(whenStarted(0))

        viewModel.setStartDate(DateTime().startOfDay().millis)

        assertAlarms(whenStarted(0).copy(task = task.id))
    }

    @Test
    fun dontAddRemindersWhenAddingAllDayStartDateToggleOff() = runBlocking {
        disableAllDayReminders()
        setupWithDefaultAlarms(whenStarted(0))

        viewModel.setStartDate(DateTime().startOfDay().millis)

        assertNoAlarms()
    }

    @Test
    fun addRemindersWhenAddingTimedStartDateToggleOff() = runBlocking {
        disableAllDayReminders()
        val task = setupWithDefaultAlarms(whenStarted(0))

        viewModel.setStartDate(
            createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, currentTimeMillis())
        )

        assertAlarms(whenStarted(0).copy(task = task.id))
    }

    @Test
    fun addRemindersWhenChangingAllDayToTimedStartDateToggleOff() = runBlocking {
        disableAllDayReminders()
        val task = setupWithDefaultAlarms(whenStarted(0))

        viewModel.setStartDate(DateTime().startOfDay().millis)
        viewModel.setStartDate(
            createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, currentTimeMillis())
        )

        assertAlarms(whenStarted(0).copy(task = task.id))
    }

    @Test
    fun dontAddRemindersWhenChangingAllDayToTimedStartDateToggleOn() = runBlocking {
        val task = setupWithDefaultAlarms(whenStarted(0))

        viewModel.setStartDate(DateTime().startOfDay().millis)
        viewModel.setStartDate(
            createDueDate(Task.URGENCY_SPECIFIC_DAY_TIME, currentTimeMillis())
        )

        // reminders were already added when going from no date -> all day
        assertAlarms(whenStarted(0).copy(task = task.id))
    }

    @Test
    fun changingListAppliesDefaultsForUntouchedFields() = runBlocking {
        val tag = TagData(name = "Work")
        tagDataDao.insert(tag)
        val place = Place(uid = "office", name = "Office")
        locationDao.insert(place)
        val recurrence = "RRULE:FREQ=WEEKLY;INTERVAL=1"
        val list = listFilter("work")
        setListDefaults(
            list,
            TaskListDefaults(
                priority = Task.Priority.HIGH,
                dueDate = Task.URGENCY_TODAY,
                tagUids = listOf(tag.remoteId!!),
                recurrence = recurrence,
                repeatFrom = Task.RepeatFrom.COMPLETION_DATE,
                alarms = listOf(whenDue(0)),
                ringMode = Task.NOTIFY_MODE_FIVE,
                locationUid = place.uid,
                locationReminder = 2,
            )
        )
        val task = newTask()
        setup(task)

        freezeAt(DateTime(2021, 2, 4, 14, 56, 34, 126)) {
            viewModel.setList(list)
        }

        assertEquals(Task.Priority.HIGH, viewModel.viewState.value.task.priority)
        assertEquals(
            createDueDate(Task.URGENCY_SPECIFIC_DAY, DateTime(2021, 2, 4).millis),
            viewModel.dueDate.value,
        )
        assertAlarms(whenDue(0).copy(task = task.id))
        assertTrue(viewModel.ringFiveTimes)
        assertEquals(Task.NOTIFY_MODE_FIVE, viewModel.ringMode.value)
        assertEquals(recurrence, viewModel.viewState.value.task.recurrence)
        assertEquals(Task.RepeatFrom.COMPLETION_DATE, viewModel.viewState.value.task.repeatFrom)
        assertEquals(setOf(tag.remoteId), viewModel.viewState.value.tags.map { it.remoteId }.toSet())
        assertEquals(place.uid, viewModel.viewState.value.location!!.place.uid)
        assertFalse(viewModel.viewState.value.location!!.geofence.isArrival)
        assertTrue(viewModel.viewState.value.location!!.geofence.isDeparture)
    }

    @Test
    fun changingListKeepsInitialValuesThatDoNotMatchDefaults() = runBlocking {
        preferences.setString(R.string.p_default_importance_key, Task.Priority.LOW.toString())
        preferences.setString(R.string.p_default_urgency_key, Task.URGENCY_NONE.toString())
        preferences.setString(R.string.p_default_recurrence, null)
        preferences.setString(R.string.p_default_tags, null)
        preferences.setString(R.string.p_default_location, null)
        val tag = TagData(name = "Filter")
        tagDataDao.insert(tag)
        val place = Place(uid = "store", name = "Store")
        locationDao.insert(place)
        val list = listFilter("work")
        setListDefaults(
            list,
            TaskListDefaults(
                priority = Task.Priority.LOW,
                dueDate = Task.URGENCY_TODAY,
                tagUids = emptyList(),
                recurrence = "RRULE:FREQ=WEEKLY;INTERVAL=1",
                locationUid = TaskListDefaults.NO_LOCATION,
            )
        )
        val dueDate = createDueDate(Task.URGENCY_SPECIFIC_DAY, DateTime(2021, 2, 6).millis)
        val recurrence = "RRULE:FREQ=DAILY;INTERVAL=1"
        val task = newTask().apply {
            priority = Task.Priority.HIGH
            this.dueDate = dueDate
            this.recurrence = recurrence
            putTransitory(Tag.KEY, arrayListOf(tag.name!!))
            putTransitory(Place.KEY, place.uid!!)
            setDefaultReminders(preferences)
        }
        setup(task)

        viewModel.setList(list)

        assertEquals(Task.Priority.HIGH, viewModel.viewState.value.task.priority)
        assertEquals(dueDate, viewModel.dueDate.value)
        assertEquals(recurrence, viewModel.viewState.value.task.recurrence)
        assertEquals(setOf(tag.remoteId), viewModel.viewState.value.tags.map { it.remoteId }.toSet())
        assertEquals(place.uid, viewModel.viewState.value.location!!.place.uid)
    }

    @Test
    fun changingListKeepsFilterValuesThatMatchOriginalDefaults() = runBlocking {
        preferences.setString(R.string.p_default_urgency_key, Task.URGENCY_TODAY.toString())
        val list = listFilter("work")
        setListDefaults(list, TaskListDefaults(dueDate = Task.URGENCY_NONE))
        val now = DateTime(2021, 2, 4, 14, 56, 34, 126)
        val task = freezeAt(now) {
            taskCreator.createWithValues(TodayFilter.create(), "test")
        }

        freezeAt(now) {
            setup(task)
            viewModel.setList(list)
        }

        assertEquals(
            createDueDate(Task.URGENCY_SPECIFIC_DAY, DateTime(2021, 2, 4).millis),
            viewModel.dueDate.value,
        )
    }

    @Test
    fun changingListKeepsTitleParserPriorityThatMatchesOriginalDefaults() = runBlocking {
        preferences.setString(R.string.p_default_importance_key, Task.Priority.MEDIUM.toString())
        val list = listFilter("work")
        setListDefaults(list, TaskListDefaults(priority = Task.Priority.NONE))
        val task = taskCreator.createWithValues("Call Bob !!")

        setup(task)
        viewModel.setList(list)

        assertEquals(Task.Priority.MEDIUM, viewModel.viewState.value.task.priority)
    }

    @Test
    fun newLocationUsesListDefaultLocationReminder() = runBlocking {
        val list = listFilter("work")
        setListDefaults(
            list,
            TaskListDefaults(locationReminder = 2)
        )
        setup(newTask())
        viewModel.setList(list)

        val geofence = viewModel.createDefaultGeofence("office")

        assertEquals("office", geofence.place)
        assertFalse(geofence.isArrival)
        assertTrue(geofence.isDeparture)
    }

    @Test
    fun changingListDoesNotReplaceEditedFields() = runBlocking {
        preferences.setDefaultAlarms(emptyList())
        val list = listFilter("work")
        val tag = TagData(name = "Work")
        tagDataDao.insert(tag)
        val place = Place(uid = "office", name = "Office")
        locationDao.insert(place)
        setListDefaults(
            list,
            TaskListDefaults(
                priority = Task.Priority.HIGH,
                dueDate = Task.URGENCY_TODAY,
                tagUids = listOf(tag.remoteId!!),
                recurrence = "RRULE:FREQ=WEEKLY;INTERVAL=1",
                alarms = listOf(whenDue(0)),
                locationUid = place.uid,
            )
        )
        val task = newTask()
        val customDueDate = createDueDate(Task.URGENCY_SPECIFIC_DAY, DateTime(2021, 2, 6).millis)
        val customAlarm = whenStarted(0)
        setup(task)
        viewModel.setPriority(Task.Priority.LOW)
        viewModel.setDueDate(customDueDate)
        viewModel.setRecurrence(null)
        viewModel.setTags(emptySet())
        viewModel.setLocation(null)
        viewModel.addAlarm(customAlarm)

        freezeAt(DateTime(2021, 2, 4, 14, 56, 34, 126)) {
            viewModel.setList(list)
        }

        assertEquals(Task.Priority.LOW, viewModel.viewState.value.task.priority)
        assertEquals(customDueDate, viewModel.dueDate.value)
        assertEquals(null, viewModel.viewState.value.task.recurrence)
        assertEquals(emptySet<TagData>(), viewModel.viewState.value.tags)
        assertEquals(null, viewModel.viewState.value.location)
        assertAlarms(customAlarm)
    }

    private fun disableAllDayReminders() {
        preferences.setBoolean(R.string.p_rmd_time_enabled, false)
    }

    private fun setupWithDefaultAlarms(vararg alarms: Alarm): Task {
        preferences.setDefaultAlarms(alarms.toList())
        val task = newTask()
        setup(task)
        return task
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

    private fun assertAlarms(vararg alarms: Alarm) {
        assertEquals(persistentSetOf(*alarms), viewModel.viewState.value.alarms)
    }

    private fun assertNoAlarms() {
        assertEquals(persistentSetOf<Alarm>(), viewModel.viewState.value.alarms)
    }
}
