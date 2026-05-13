package org.tasks.data.entity

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val TASK_LIST_DEFAULTS_JSON = Json { ignoreUnknownKeys = true }

@Serializable
data class TaskListDefaults(
    val priority: Int? = null,
    val dueDate: Int? = null,
    val hideUntil: Int? = null,
    val tagUids: List<String>? = null,
    val calendarId: String? = null,
    val recurrence: String? = null,
    val repeatFrom: Int? = null,
    val alarms: List<Alarm>? = null,
    val randomReminderHours: Int? = null,
    val ringMode: Int? = null,
    val locationUid: String? = null,
    val locationReminder: Int? = null,
) {
    val isEmpty: Boolean
        get() = this == EMPTY

    companion object {
        const val NO_CALENDAR = ""
        const val NO_LOCATION = ""
        const val NO_RECURRENCE = ""

        val EMPTY = TaskListDefaults()
    }
}

fun TaskListMetadata.getDefaults(): TaskListDefaults = defaults
    ?.takeIf { it.isNotBlank() }
    ?.let {
        try {
            TASK_LIST_DEFAULTS_JSON.decodeFromString<TaskListDefaults>(it)
        } catch (_: IllegalArgumentException) {
            TaskListDefaults.EMPTY
        } catch (_: SerializationException) {
            TaskListDefaults.EMPTY
        }
    }
    ?: TaskListDefaults.EMPTY

fun TaskListMetadata.setDefaults(defaults: TaskListDefaults) {
    this.defaults = defaults
        .takeUnless { it.isEmpty }
        ?.let { TASK_LIST_DEFAULTS_JSON.encodeToString(it) }
}
