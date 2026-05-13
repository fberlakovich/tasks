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
        priority = overrides.priority ?: priority,
        dueDate = overrides.dueDate ?: dueDate,
        hideUntil = overrides.hideUntil ?: hideUntil,
        tagUids = overrides.tagUids ?: tagUids,
        calendarId = overrides.calendarId.overrideString(TaskListDefaults.NO_CALENDAR, calendarId),
        recurrence = overrides.recurrence.overrideString(TaskListDefaults.NO_RECURRENCE, recurrence),
        repeatFrom = overrides.repeatFrom ?: repeatFrom,
        alarms = overrides.alarms ?: alarms,
        randomReminderHours = overrides.randomReminderHours ?: randomReminderHours,
        ringMode = overrides.ringMode ?: ringMode,
        locationUid = overrides.locationUid.overrideString(TaskListDefaults.NO_LOCATION, locationUid),
        locationReminder = overrides.locationReminder ?: locationReminder,
    )

    private fun String?.overrideString(noneValue: String, inherited: String?): String? = when (this) {
        null -> inherited
        noneValue -> null
        else -> this
    }
}
