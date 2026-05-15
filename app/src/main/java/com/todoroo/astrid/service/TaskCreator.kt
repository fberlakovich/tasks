package com.todoroo.astrid.service

import com.todoroo.astrid.api.PermaSql
import org.tasks.data.dao.TaskDao
import org.tasks.data.TaskSaver
import com.todoroo.astrid.gcal.GCalHelper
import com.todoroo.astrid.utility.TitleParser.parse
import org.tasks.Strings.isNullOrEmpty
import org.tasks.data.GoogleTask
import org.tasks.data.UUIDHelper
import org.tasks.data.createDueDate
import org.tasks.data.createGeofence
import org.tasks.data.createHideUntil
import org.tasks.data.getDefaultAlarms
import org.tasks.data.dao.AlarmDao
import org.tasks.data.dao.CaldavDao
import org.tasks.data.dao.GoogleTaskDao
import org.tasks.data.dao.LocationDao
import org.tasks.data.dao.TagDao
import org.tasks.data.dao.TagDataDao
import org.tasks.data.entity.CaldavTask
import org.tasks.data.entity.Place
import org.tasks.data.entity.Tag
import org.tasks.data.entity.TagData
import org.tasks.data.entity.Task
import org.tasks.data.entity.Task.Companion.DUE_DATE
import org.tasks.data.entity.Task.Companion.HIDE_UNTIL
import org.tasks.data.entity.Task.Companion.IMPORTANCE
import org.tasks.filters.CaldavFilter
import org.tasks.filters.Filter
import org.tasks.filters.mapFromSerializedString
import org.tasks.location.LocationService
import org.tasks.preferences.DefaultFilterProvider
import org.tasks.preferences.Preferences
import org.tasks.preferences.ResolvedTaskDefaults
import org.tasks.preferences.TaskDefaultsProvider
import org.tasks.time.DateTimeUtils2.currentTimeMillis
import org.tasks.time.ONE_HOUR
import org.tasks.time.startOfDay
import timber.log.Timber
import javax.inject.Inject

class TaskCreator @Inject constructor(
    private val gcalHelper: GCalHelper,
    private val preferences: Preferences,
    private val tagDataDao: TagDataDao,
    private val taskDao: TaskDao,
    private val taskSaver: TaskSaver,
    private val tagDao: TagDao,
    private val googleTaskDao: GoogleTaskDao,
    private val defaultFilterProvider: DefaultFilterProvider,
    private val caldavDao: CaldavDao,
    private val locationDao: LocationDao,
    private val locationService: LocationService,
    private val alarmDao: AlarmDao,
    private val taskDefaultsProvider: TaskDefaultsProvider,
) {
    suspend fun basicQuickAddTask(title: String, filter: Filter? = null): Task {
        val values = mapFromSerializedString(filter?.valuesForNewTasks)
        val defaultsFilter = resolveDefaultsFilter(filter ?: defaultFilterProvider.getDefaultList(), values)
        val defaults = taskDefaultsProvider.get(defaultsFilter)
        val task = create(
            values,
            title.trim { it <= ' ' },
            defaults,
        )
        taskDao.createNew(task)
        applyPostCreateDefaults(task, defaults)
        val addToTop = preferences.addTasksToTop()
        if (task.hasTransitory(GoogleTask.KEY)) {
            googleTaskDao.insertAndShift(
                task,
                CaldavTask(
                    task = task.id,
                    calendar = task.getTransitory<String>(GoogleTask.KEY)!!,
                    remoteId = null
                ),
                addToTop
            )
        } else if (task.hasTransitory(CaldavTask.KEY)) {
            caldavDao.insert(
                task,
                CaldavTask(
                    task = task.id,
                    calendar = task.getTransitory(CaldavTask.KEY),
                ),
                addToTop
            )
        } else {
            val remoteList = defaultFilterProvider.getDefaultList()
            if (remoteList.isGoogleTasks) {
                googleTaskDao.insertAndShift(
                    task,
                    CaldavTask(
                        task = task.id,
                        calendar = remoteList.uuid,
                        remoteId = null
                    ),
                    addToTop
                )
            } else {
                caldavDao.insert(
                    task,
                    CaldavTask(
                        task = task.id,
                        calendar = remoteList.uuid,
                    ),
                    addToTop
                )
            }
        }
        taskSaver.save(task, null)
        alarmDao.insert(task.getDefaultAlarms(preferences.isDefaultDueTimeEnabled()))
        return task
    }

    suspend fun createWithValues(title: String?): Task {
        return create(null, title, null)
    }

    suspend fun createWithDefaultListValues(title: String?): Task =
        createWithValues(defaultFilterProvider.getDefaultList(), title)

    suspend fun createWithValues(filter: Filter?, title: String?): Task {
        val values = mapFromSerializedString(filter?.valuesForNewTasks)
        return create(values, title, resolveDefaultsFilter(filter, values))
    }

    /**
     * Create a task from the given content values. This version doesn't need to start with a base task model.
     */
    internal suspend fun create(values: Map<String, Any>?, title: String?): Task = create(values, title, null)

    private suspend fun create(
        values: Map<String, Any>?,
        title: String?,
        filter: Filter?,
    ): Task = create(values, title, taskDefaultsProvider.get(filter))

    private suspend fun create(
        values: Map<String, Any>?,
        title: String?,
        defaults: ResolvedTaskDefaults,
    ): Task {
        val task = Task(
            title = title?.trim { it <= ' ' },
            creationDate = currentTimeMillis(),
            modificationDate = currentTimeMillis(),
            remoteId = UUIDHelper.newUUID(),
            priority = defaults.priority,
        )
        task.repeatFrom = defaults.repeatFrom
        defaults.recurrence?.let {
            task.recurrence = it
        }
        defaults.locationUid?.let { task.putTransitory(Place.KEY, it) }
        task.randomReminder = ONE_HOUR * defaults.randomReminderHours
        task.putTransitory(Task.TRANS_DEFAULT_ALARMS, defaults.alarms)
        task.ringFlags = defaults.ringMode
        val tags = ArrayList<String>()
        val initialValues = ArrayList<String>()
        values?.entries?.forEach { (key, value) ->
            when (key) {
                Tag.KEY -> {
                    tags.add(value as String)
                    initialValues.add(Tag.KEY)
                }
                GoogleTask.KEY, CaldavTask.KEY -> task.putTransitory(key, value)
                Place.KEY -> {
                    task.putTransitory(key, value)
                    initialValues.add(Place.KEY)
                }
                DUE_DATE.name -> value.substitute()?.toLongOrNull()?.let {
                    task.dueDate = createDueDate(Task.URGENCY_SPECIFIC_DAY, it)
                    initialValues.add(key)
                }
                IMPORTANCE.name -> value.substitute()?.toIntOrNull()?.let {
                    task.priority = it
                    initialValues.add(key)
                }
                HIDE_UNTIL.name -> value.substitute()?.toLongOrNull()?.let {
                    task.hideUntil = it.startOfDay()
                    initialValues.add(key)
                }
            }
        }
        if (values?.containsKey(DUE_DATE.name) != true) {
            task.dueDate = createDueDate(defaults.dueDate, 0)
        }
        if (values?.containsKey(HIDE_UNTIL.name) != true) {
            task.hideUntil = task.createHideUntil(defaults.hideUntil, 0)
        }
        if (tags.isEmpty()) {
            defaults.tagUids
                .map { tagDataDao.getByUuid(it) }
                .mapNotNull { it?.name }
                .let { tags.addAll(it) }
        }
        try {
            initialValues.addAll(parse(tagDataDao, task, tags))
        } catch (e: Throwable) {
            Timber.e(e)
        }
        if (initialValues.isNotEmpty()) {
            task.putTransitory(Task.TRANS_INITIAL_VALUES, initialValues)
        }
        task.putTransitory(Tag.KEY, tags)
        return task
    }

    private suspend fun resolveDefaultsFilter(filter: Filter?, values: Map<String, Any>?): Filter? =
        values?.resolveListFilter()
            ?: (filter as? CaldavFilter)
            ?: filter?.let { defaultFilterProvider.getDefaultList() }

    private suspend fun Map<String, Any>.resolveListFilter(): CaldavFilter? =
        ((this[CaldavTask.KEY] ?: this[GoogleTask.KEY]) as? String)
            ?.let { caldavDao.getCalendarByUuid(it) }
            ?.let { calendar ->
                calendar.account
                    ?.let { caldavDao.getAccountByUuid(it) }
                    ?.let { account -> CaldavFilter(calendar = calendar, account = account) }
            }

    internal suspend fun applyPostCreateDefaults(task: Task, defaults: ResolvedTaskDefaults) {
        createCalendarEvent(task, defaults.calendarId)
        createTags(task)
        createLocationReminder(task, defaults.locationReminder)
    }

    private suspend fun createCalendarEvent(task: Task, calendarId: String?) {
        if (!isNullOrEmpty(task.title)
            && calendarId != null
            && task.hasDueDate()
            && isNullOrEmpty(task.calendarURI)
        ) {
            gcalHelper.createTaskEvent(task, calendarId)?.let {
                task.calendarURI = it.toString()
            }
        }
    }

    private suspend fun createLocationReminder(task: Task, locationReminder: Int) {
        if (task.hasTransitory(Place.KEY)) {
            val place = locationDao.getPlace(task.getTransitory<String>(Place.KEY)!!)
            if (place != null) {
                locationDao.insert(createGeofence(place.uid, locationReminder).copy(task = task.id))
                locationService.updateGeofences(place)
            }
        }
    }

    suspend fun createTags(task: Task) {
        for (tag in task.tags) {
            val tagData = tagDataDao.getTagByName(tag)
            ?: TagData(name = tag).also { tagDataDao.insert(it) }
            tagDao.insert(
                Tag(
                    task = task.id,
                    taskUid = task.uuid,
                    name = tagData.name,
                    tagUid = tagData.remoteId
                )
            )
        }
    }

    companion object {
        private fun Any?.substitute(): String? =
            (this as? String)?.let { PermaSql.replacePlaceholdersForNewTask(it) }
    }
}
