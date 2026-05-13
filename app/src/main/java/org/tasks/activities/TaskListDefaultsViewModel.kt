package org.tasks.activities

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.tasks.R
import org.tasks.calendars.CalendarProvider
import org.tasks.data.dao.LocationDao
import org.tasks.data.dao.TagDataDao
import org.tasks.data.dao.TaskListMetadataDao
import org.tasks.data.entity.Alarm
import org.tasks.data.entity.Place
import org.tasks.data.entity.TagData
import org.tasks.data.entity.Task
import org.tasks.data.entity.TaskListDefaults
import org.tasks.data.entity.getDefaults
import org.tasks.data.entity.setDefaults
import org.tasks.preferences.Preferences
import org.tasks.reminders.AlarmToString
import org.tasks.repeats.RepeatRuleToString
import javax.inject.Inject

@HiltViewModel
class TaskListDefaultsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: Preferences,
    private val taskListMetadataDao: TaskListMetadataDao,
    private val tagDataDao: TagDataDao,
    private val calendarProvider: CalendarProvider,
    private val repeatRuleToString: RepeatRuleToString,
    private val locationDao: LocationDao,
) : ViewModel() {
    private var filterKey: String? = null
    private var initialDefaults = TaskListDefaults.EMPTY

    var defaults by mutableStateOf(TaskListDefaults.EMPTY)
        private set
    var tagsSummary by mutableStateOf("")
        private set
    var prioritySummary by mutableStateOf("")
        private set
    var startDateSummary by mutableStateOf("")
        private set
    var dueDateSummary by mutableStateOf("")
        private set
    var calendarSummary by mutableStateOf("")
        private set
    var recurrenceSummary by mutableStateOf("")
        private set
    var recurrenceFromSummary by mutableStateOf("")
        private set
    var remindersSummary by mutableStateOf("")
        private set
    var randomReminderSummary by mutableStateOf("")
        private set
    var remindersModeSummary by mutableStateOf("")
        private set
    var locationSummary by mutableStateOf("")
        private set
    var locationReminderSummary by mutableStateOf("")
        private set
    var initialized by mutableStateOf(false)
        private set

    val priorityEntries: Array<String> = context.resources.getStringArray(R.array.EPr_default_importance)
    val priorityValues: Array<String> = context.resources.getStringArray(R.array.EPr_default_importance_values)
    val startDateEntries: Array<String> = context.resources.getStringArray(R.array.EPr_default_hideUntil)
    val startDateValues: Array<String> = context.resources.getStringArray(R.array.EPr_default_hideUntil_values)
    val dueDateEntries: Array<String> = context.resources.getStringArray(R.array.EPr_default_urgency)
    val dueDateValues: Array<String> = context.resources.getStringArray(R.array.EPr_default_urgency_values)
    val recurrenceFromEntries: Array<String> = context.resources.getStringArray(R.array.repeat_type_capitalized)
    val recurrenceFromValues: Array<String> = context.resources.getStringArray(R.array.repeat_type_values)
    val randomReminderEntries: Array<String> = context.resources.getStringArray(R.array.EPr_reminder_random)
    val randomReminderValues: Array<String> = context.resources.getStringArray(R.array.EPr_reminder_random_hours)
    val remindersModeEntries: Array<String> = context.resources.getStringArray(R.array.EPr_default_reminders_mode)
    val remindersModeValues: Array<String> = context.resources.getStringArray(R.array.EPr_default_reminders_mode_values)
    val locationReminderEntries: Array<String> = context.resources.getStringArray(R.array.EPr_default_location_reminder)
    val locationReminderValues: Array<String> = context.resources.getStringArray(R.array.EPR_default_location_reminder_values)

    val hasChanges: Boolean
        get() = defaults != initialDefaults

    fun initialize(filterKey: String?) {
        if (this.filterKey == filterKey) {
            return
        }
        this.filterKey = filterKey
        initialized = false
        viewModelScope.launch {
            val loadedDefaults = filterKey
                ?.let { taskListMetadataDao.fetchByFilter(it) }
                ?.getDefaults()
                ?: TaskListDefaults.EMPTY
            if (this@TaskListDefaultsViewModel.filterKey != filterKey) {
                return@launch
            }
            initialDefaults = loadedDefaults
            defaults = loadedDefaults
            refreshSummaries()
            initialized = true
        }
    }

    suspend fun save() {
        if (!initialized || !hasChanges) {
            return
        }
        val key = filterKey ?: return
        val current = taskListMetadataDao.fetchByFilter(key)
        if (current == null && defaults.isEmpty) {
            initialDefaults = defaults
            return
        }
        val metadata = current ?: taskListMetadataDao.getOrCreateForFilter(key)
        metadata.setDefaults(defaults)
        taskListMetadataDao.update(metadata)
        initialDefaults = defaults
    }

    fun reset() {
        defaults = TaskListDefaults.EMPTY
        refreshSummaries()
    }

    fun inheritTags() {
        defaults = defaults.copy(tagUids = null)
        refreshTags()
    }

    fun setTags(tags: List<TagData>) {
        defaults = defaults.copy(tagUids = tags.mapNotNull { it.remoteId })
        refreshTags()
    }

    fun clearTags() {
        defaults = defaults.copy(tagUids = emptyList())
        refreshTags()
    }

    fun setPriority(value: String?) {
        defaults = defaults.copy(priority = value?.toIntOrNull())
        refreshPriority()
    }

    fun setStartDate(value: String?) {
        defaults = defaults.copy(hideUntil = value?.toIntOrNull())
        refreshStartDate()
    }

    fun setDueDate(value: String?) {
        defaults = defaults.copy(dueDate = value?.toIntOrNull())
        refreshDueDate()
    }

    fun setCalendar(calendarId: String?) {
        defaults = defaults.copy(calendarId = calendarId ?: TaskListDefaults.NO_CALENDAR)
        refreshCalendar()
    }

    fun inheritCalendar() {
        defaults = defaults.copy(calendarId = null)
        refreshCalendar()
    }

    fun setRecurrence(rrule: String?) {
        defaults = defaults.copy(recurrence = rrule?.takeIf { it.isNotBlank() } ?: TaskListDefaults.NO_RECURRENCE)
        refreshRecurrence()
    }

    fun inheritRecurrence() {
        defaults = defaults.copy(recurrence = null)
        refreshRecurrence()
    }

    fun setRecurrenceFrom(value: String?) {
        defaults = defaults.copy(repeatFrom = value?.toIntOrNull())
        refreshRecurrenceFrom()
    }

    fun setReminders(value: List<Alarm>?) {
        defaults = defaults.copy(alarms = value)
        refreshReminders()
    }

    fun setRandomReminder(value: String?) {
        defaults = defaults.copy(randomReminderHours = value?.toIntOrNull())
        refreshRandomReminder()
    }

    fun setRemindersMode(value: String?) {
        defaults = defaults.copy(ringMode = value?.toIntOrNull())
        refreshRemindersMode()
    }

    fun setLocation(place: Place?) {
        defaults = defaults.copy(locationUid = place?.uid ?: TaskListDefaults.NO_LOCATION)
        refreshLocation()
    }

    fun inheritLocation() {
        defaults = defaults.copy(locationUid = null)
        refreshLocation()
    }

    fun setLocationReminder(value: String?) {
        defaults = defaults.copy(locationReminder = value?.toIntOrNull())
        refreshLocationReminder()
    }

    fun getTagsForEditor(): List<TagData> = defaults.tagUids
        ?.let { runBlocking { tagDataDao.getByUuid(it).sortedBy { tag -> tag.name } } }
        ?: globalTags()

    fun getCalendarForPicker(): String? = when (val overrideCalendar = defaults.calendarId) {
        null -> preferences.defaultCalendar.takeIf { preferences.isDefaultCalendarSet }
        TaskListDefaults.NO_CALENDAR -> null
        else -> overrideCalendar
    }

    fun getRecurrenceRuleForPicker(): String? = when (val overrideRecurrence = defaults.recurrence) {
        null -> preferences.getStringValue(R.string.p_default_recurrence)?.takeIf { it.isNotBlank() }
        TaskListDefaults.NO_RECURRENCE -> null
        else -> overrideRecurrence
    }

    fun getLocationForPicker(): Place? = when (val overrideLocation = defaults.locationUid) {
        null -> preferences.getStringValue(R.string.p_default_location)?.takeIf { it.isNotBlank() }
        TaskListDefaults.NO_LOCATION -> null
        else -> overrideLocation
    }?.let { runBlocking { locationDao.getByUid(it) } }

    fun getRemindersForEditor(): List<Alarm> = defaults.alarms
        ?: runBlocking { preferences.defaultAlarms() }

    private fun refreshSummaries() {
        refreshTags()
        refreshPriority()
        refreshStartDate()
        refreshDueDate()
        refreshCalendar()
        refreshRecurrence()
        refreshRecurrenceFrom()
        refreshReminders()
        refreshRandomReminder()
        refreshRemindersMode()
        refreshLocation()
        refreshLocationReminder()
    }

    private fun refreshTags() {
        val overrideTags = defaults.tagUids
        val summary = tagsSummary(overrideTags ?: globalTagUids())
        tagsSummary = if (overrideTags == null) {
            context.getString(R.string.use_global_default, summary)
        } else {
            summary
        }
    }

    private fun refreshPriority() {
        prioritySummary = inheritedOrOverride(
            overrideValue = defaults.priority,
            values = priorityValues,
            entries = priorityEntries,
            globalValue = preferences.getIntegerFromString(
                R.string.p_default_importance_key,
                Task.Priority.LOW,
            )
        )
    }

    private fun refreshStartDate() {
        startDateSummary = inheritedOrOverride(
            overrideValue = defaults.hideUntil,
            values = startDateValues,
            entries = startDateEntries,
            globalValue = preferences.getIntegerFromString(
                R.string.p_default_hideUntil_key,
                Task.HIDE_UNTIL_NONE,
            )
        )
    }

    private fun refreshDueDate() {
        dueDateSummary = inheritedOrOverride(
            overrideValue = defaults.dueDate,
            values = dueDateValues,
            entries = dueDateEntries,
            globalValue = preferences.getIntegerFromString(
                R.string.p_default_urgency_key,
                Task.URGENCY_NONE,
            )
        )
    }

    private fun refreshCalendar() {
        val overrideCalendar = defaults.calendarId
        val summary = calendarSummary(
            when (overrideCalendar) {
                null -> preferences.defaultCalendar.takeIf { preferences.isDefaultCalendarSet }
                TaskListDefaults.NO_CALENDAR -> null
                else -> overrideCalendar
            }
        )
        calendarSummary = if (overrideCalendar == null) {
            context.getString(R.string.use_global_default, summary)
        } else {
            summary
        }
    }

    private fun refreshRecurrence() {
        val overrideRecurrence = defaults.recurrence
        val summary = recurrenceSummary(
            when (overrideRecurrence) {
                null -> preferences.getStringValue(R.string.p_default_recurrence)?.takeIf { it.isNotBlank() }
                TaskListDefaults.NO_RECURRENCE -> null
                else -> overrideRecurrence
            }
        )
        recurrenceSummary = if (overrideRecurrence == null) {
            context.getString(R.string.use_global_default, summary)
        } else {
            summary
        }
    }

    private fun refreshRecurrenceFrom() {
        recurrenceFromSummary = inheritedOrOverride(
            overrideValue = defaults.repeatFrom,
            values = recurrenceFromValues,
            entries = recurrenceFromEntries,
            globalValue = preferences.getIntegerFromString(
                R.string.p_default_recurrence_from,
                0,
            )
        )
    }

    private fun refreshReminders() {
        val overrideAlarms = defaults.alarms
        remindersSummary = if (overrideAlarms == null) {
            context.getString(
                R.string.use_global_default,
                alarmsSummary(runBlocking { preferences.defaultAlarms() })
            )
        } else {
            alarmsSummary(overrideAlarms)
        }
    }

    private fun refreshRandomReminder() {
        randomReminderSummary = inheritedOrOverride(
            overrideValue = defaults.randomReminderHours,
            values = randomReminderValues,
            entries = randomReminderEntries,
            globalValue = preferences.getIntegerFromString(
                R.string.p_rmd_default_random_hours,
                0,
            )
        )
    }

    private fun refreshRemindersMode() {
        remindersModeSummary = inheritedOrOverride(
            overrideValue = defaults.ringMode,
            values = remindersModeValues,
            entries = remindersModeEntries,
            globalValue = preferences.getIntegerFromString(
                R.string.p_default_reminders_mode_key,
                0,
            )
        )
    }

    private fun refreshLocation() {
        val overrideLocation = defaults.locationUid
        val summary = locationSummary(
            when (overrideLocation) {
                null -> preferences.getStringValue(R.string.p_default_location)?.takeIf { it.isNotBlank() }
                TaskListDefaults.NO_LOCATION -> null
                else -> overrideLocation
            }
        )
        locationSummary = if (overrideLocation == null) {
            context.getString(R.string.use_global_default, summary)
        } else {
            summary
        }
    }

    private fun refreshLocationReminder() {
        locationReminderSummary = inheritedOrOverride(
            overrideValue = defaults.locationReminder,
            values = locationReminderValues,
            entries = locationReminderEntries,
            globalValue = preferences.getIntegerFromString(
                R.string.p_default_location_reminder_key,
                0,
            )
        )
    }

    private fun inheritedOrOverride(
        overrideValue: Int?,
        values: Array<String>,
        entries: Array<String>,
        globalValue: Int,
    ): String {
        val value = overrideValue ?: globalValue
        val entry = entries[values.indexOf(value.toString()).coerceAtLeast(0)]
        return if (overrideValue == null) {
            context.getString(R.string.use_global_default, entry)
        } else {
            entry
        }
    }

    private fun globalTags(): List<TagData> = globalTagUids()
        .takeIf { it.isNotEmpty() }
        ?.let { runBlocking { tagDataDao.getByUuid(it).sortedBy { tag -> tag.name } } }
        ?: emptyList()

    private fun globalTagUids(): List<String> = preferences
        .getStringValue(R.string.p_default_tags)
        ?.split(",")
        ?.filter { it.isNotBlank() }
        ?: emptyList()

    private fun tagsSummary(tagUids: List<String>): String = tagUids
        .takeIf { it.isNotEmpty() }
        ?.let { runBlocking { tagDataDao.getByUuid(it) } }
        ?.mapNotNull { it.name }
        ?.sorted()
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString(", ")
        ?: context.getString(R.string.none)

    private fun calendarSummary(calendarId: String?): String = calendarProvider
        .getCalendar(calendarId)
        ?.name
        ?: context.getString(R.string.dont_add_to_calendar)

    private fun recurrenceSummary(rrule: String?): String = rrule
        ?.takeIf { it.isNotBlank() }
        ?.let {
            try {
                repeatRuleToString.toString(it)
            } catch (_: Exception) {
                null
            }
        }
        ?: context.getString(R.string.repeat_option_does_not_repeat)

    private fun locationSummary(locationUid: String?): String = locationUid
        ?.let { runBlocking { locationDao.getByUid(it) } }
        ?.displayName
        ?: context.getString(R.string.none)

    private fun alarmsSummary(alarms: List<Alarm>): String = if (alarms.isEmpty()) {
        context.getString(R.string.no_reminders)
    } else {
        val alarmToString = AlarmToString(context)
        alarms.joinToString("\n") {
            alarmToString.toString(it).replace("\n", ", ")
        }
    }
}
