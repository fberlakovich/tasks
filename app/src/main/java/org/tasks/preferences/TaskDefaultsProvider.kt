package org.tasks.preferences

import org.tasks.R
import org.tasks.data.dao.TaskListMetadataDao
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.Task
import org.tasks.data.entity.TaskListDefaults
import org.tasks.data.entity.getDefaults
import org.tasks.filters.Filter
import org.tasks.filters.key
import javax.inject.Inject

class TaskDefaultsProvider @Inject constructor(
    private val preferences: Preferences,
    private val taskListMetadataDao: TaskListMetadataDao,
) {
    suspend fun get(filter: Filter?): ResolvedTaskDefaults {
        val global = getGlobalDefaults()
        val overrides = filter
            ?.key()
            ?.let { taskListMetadataDao.fetchByFilter(it) }
            ?.getDefaults()
            ?: TaskListDefaults.EMPTY
        return global.withOverrides(overrides)
    }

    private suspend fun getGlobalDefaults(): ResolvedTaskDefaults = ResolvedTaskDefaults(
        priority = preferences.defaultPriority(),
        dueDate = preferences.getIntegerFromString(R.string.p_default_urgency_key, Task.URGENCY_NONE),
        hideUntil = preferences.getIntegerFromString(R.string.p_default_hideUntil_key, Task.HIDE_UNTIL_NONE),
        tagUids = preferences.getStringValue(R.string.p_default_tags).toUuidList(),
        calendarId = preferences.defaultCalendar.takeIf { preferences.isDefaultCalendarSet },
        recurrence = preferences.getStringValue(R.string.p_default_recurrence)?.takeIf { it.isNotBlank() },
        repeatFrom = if (preferences.getIntegerFromString(R.string.p_default_recurrence_from, 0) == 1) {
            Task.RepeatFrom.COMPLETION_DATE
        } else {
            Task.RepeatFrom.DUE_DATE
        },
        alarms = preferences.defaultAlarms(),
        randomReminderHours = preferences.defaultRandomHours(),
        ringMode = preferences.defaultRingMode(),
        locationUid = preferences.getStringValue(R.string.p_default_location)?.takeIf { it.isNotBlank() },
        locationReminder = preferences.defaultLocationReminder(),
    )

    private fun String?.toUuidList(): List<String> = this
        ?.split(",")
        ?.filter { it.isNotBlank() }
        ?: emptyList()
}

data class ResolvedTaskDefaults(
    val priority: Int,
    val dueDate: Int,
    val hideUntil: Int,
    val tagUids: List<String>,
    val calendarId: String?,
    val recurrence: String?,
    @Task.RepeatFrom val repeatFrom: Int,
    val alarms: List<Alarm>,
    val randomReminderHours: Int,
    val ringMode: Int,
    val locationUid: String?,
    val locationReminder: Int,
) {
    fun withOverrides(overrides: TaskListDefaults): ResolvedTaskDefaults = copy(
        priority = overrides.priority.validOrNull(VALID_PRIORITIES) ?: priority,
        dueDate = overrides.dueDate.validOrNull(VALID_DUE_DATES) ?: dueDate,
        hideUntil = overrides.hideUntil.validOrNull(VALID_START_DATES) ?: hideUntil,
        tagUids = overrides.tagUids ?: tagUids,
        calendarId = overrides.calendarId.overrideString(TaskListDefaults.NO_CALENDAR, calendarId),
        recurrence = overrides.recurrence.overrideString(TaskListDefaults.NO_RECURRENCE, recurrence),
        repeatFrom = overrides.repeatFrom.validOrNull(VALID_REPEAT_FROM) ?: repeatFrom,
        alarms = overrides.alarms ?: alarms,
        randomReminderHours = overrides.randomReminderHours.validOrNull(VALID_RANDOM_REMINDER_HOURS)
            ?: randomReminderHours,
        ringMode = overrides.ringMode.validOrNull(VALID_RING_MODES) ?: ringMode,
        locationUid = overrides.locationUid.overrideString(TaskListDefaults.NO_LOCATION, locationUid),
        locationReminder = overrides.locationReminder.validOrNull(VALID_LOCATION_REMINDERS) ?: locationReminder,
    )

    private fun Int?.validOrNull(validValues: Set<Int>): Int? = this?.takeIf { it in validValues }

    private fun String?.overrideString(noneValue: String, inherited: String?): String? = when (this) {
        null -> inherited
        noneValue -> null
        else -> this
    }

    companion object {
        private val VALID_PRIORITIES = setOf(
            Task.Priority.HIGH,
            Task.Priority.MEDIUM,
            Task.Priority.LOW,
            Task.Priority.NONE,
        )
        private val VALID_DUE_DATES = setOf(
            Task.URGENCY_NONE,
            Task.URGENCY_TODAY,
            Task.URGENCY_TOMORROW,
            Task.URGENCY_DAY_AFTER,
            Task.URGENCY_NEXT_WEEK,
            Task.URGENCY_IN_TWO_WEEKS,
        )
        private val VALID_START_DATES = setOf(
            Task.HIDE_UNTIL_NONE,
            Task.HIDE_UNTIL_DUE,
            Task.HIDE_UNTIL_DUE_TIME,
            Task.HIDE_UNTIL_DAY_BEFORE,
            Task.HIDE_UNTIL_WEEK_BEFORE,
        )
        private val VALID_REPEAT_FROM = setOf(
            Task.RepeatFrom.DUE_DATE,
            Task.RepeatFrom.COMPLETION_DATE,
        )
        private val VALID_RANDOM_REMINDER_HOURS = setOf(0, 1, 24, 168, 336)
        private val VALID_RING_MODES = setOf(
            0,
            Task.NOTIFY_MODE_FIVE,
            Task.NOTIFY_MODE_NONSTOP,
        )
        private val VALID_LOCATION_REMINDERS = setOf(0, 1, 2, 3)
    }
}
