package org.tasks.activities

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.sizeDp
import com.todoroo.andlib.utility.AndroidUtilities.atLeastS
import com.todoroo.astrid.ui.ReminderControlSetViewModel
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.launch
import org.tasks.R
import org.tasks.analytics.Firebase
import org.tasks.billing.Inventory
import org.tasks.calendars.CalendarPicker
import org.tasks.calendars.CalendarPicker.Companion.newCalendarPicker
import org.tasks.caldav.BaseCaldavCalendarSettingsActivity
import org.tasks.billing.PurchaseActivity
import org.tasks.billing.PurchaseActivityViewModel
import org.tasks.compose.AddAlarmDialog
import org.tasks.compose.AddReminderDialog
import org.tasks.compose.DefaultRemindersList
import org.tasks.compose.DeleteButton
import org.tasks.compose.IconPickerActivity.Companion.launchIconPicker
import org.tasks.compose.IconPickerActivity.Companion.registerForIconPickerResult
import org.tasks.compose.settings.CardPosition
import org.tasks.compose.settings.ListSettingsContent
import org.tasks.compose.settings.ListSettingsScaffold
import org.tasks.compose.settings.PreferenceRow
import org.tasks.compose.settings.SectionHeader
import org.tasks.compose.settings.SettingsCardGap
import org.tasks.compose.settings.SettingsContentPadding
import org.tasks.compose.settings.SettingsItemCard
import org.tasks.data.UUIDHelper
import org.tasks.data.entity.CaldavAccount
import org.tasks.data.entity.Place
import org.tasks.data.entity.TagData
import org.tasks.data.entity.TaskListDefaults
import org.tasks.extensions.addBackPressedCallback
import org.tasks.filters.CaldavFilter
import org.tasks.filters.Filter
import org.tasks.filters.key
import org.tasks.icons.OutlinedGoogleMaterial
import org.tasks.intents.TaskIntents
import org.tasks.location.LocationPickerActivity
import org.tasks.location.LocationPickerActivity.Companion.EXTRA_PLACE
import org.tasks.preferences.DefaultFilterProvider
import org.tasks.repeats.BasicRecurrenceDialog
import org.tasks.tags.TagPickerActivity
import org.tasks.tags.TagPickerActivity.Companion.EXTRA_SELECTED
import org.tasks.themes.ColorProvider
import org.tasks.themes.Theme
import org.tasks.themes.contentColorFor
import org.tasks.widget.RequestPinWidgetReceiver
import org.tasks.widget.RequestPinWidgetReceiver.Companion.EXTRA_COLOR
import org.tasks.widget.RequestPinWidgetReceiver.Companion.EXTRA_FILTER
import org.tasks.widget.TasksWidget
import javax.inject.Inject


abstract class BaseListSettingsActivity : AppCompatActivity() {
    @Inject lateinit var tasksTheme: Theme
    @Inject lateinit var defaultFilterProvider: DefaultFilterProvider
    @Inject lateinit var firebase: Firebase
    @Inject lateinit var inventory: Inventory
    @Inject lateinit var colorProvider: ColorProvider

    protected val baseViewModel: BaseListSettingsViewModel by viewModels()
    protected val taskListDefaultsViewModel: TaskListDefaultsViewModel by viewModels()

    protected abstract val defaultIcon: String

    private val tagsLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val tags = result.data?.getParcelableArrayListExtra<TagData>(EXTRA_SELECTED)
            taskListDefaultsViewModel.setTags(tags ?: emptyList())
        }
    }

    private val locationLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val place = result.data?.getParcelableExtra<Place>(EXTRA_PLACE)
            taskListDefaultsViewModel.setLocation(place)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        baseViewModel.setIcon(defaultIcon)

        supportFragmentManager.setFragmentResultListener(
            CalendarPicker.REQUEST_KEY,
            this,
        ) { _, bundle ->
            val calendarId = bundle.getString(CalendarPicker.EXTRA_CALENDAR_ID)
            taskListDefaultsViewModel.setCalendar(calendarId)
        }
        supportFragmentManager.setFragmentResultListener(
            BasicRecurrenceDialog.REQUEST_KEY,
            this,
        ) { _, bundle ->
            val rrule = bundle.getString(BasicRecurrenceDialog.EXTRA_RRULE)
            taskListDefaultsViewModel.setRecurrence(rrule)
        }

        addBackPressedCallback {
            discard()
        }
    }

    protected abstract fun hasChanges(): Boolean
    protected abstract suspend fun save()
    protected val isNew: Boolean
        get() = filter == null

    protected abstract val filter: Filter?
    protected abstract val toolbarTitle: String?
    protected abstract suspend fun delete()
    protected open fun discard() {
        if (hasChanges()) {
            baseViewModel.promptDiscard(true)
        } else {
            finish()
        }
    }

    private val launcher = registerForIconPickerResult { selected ->
        baseViewModel.setIcon(selected)
    }

    fun showIconPicker() {
        firebase.logEvent(
            R.string.event_settings_click,
            R.string.param_type to "icon_picker",
            R.string.param_source to settingsSource,
        )
        launcher.launchIconPicker(this, baseViewModel.icon)
    }

    private val settingsSource: String
        get() = when (this) {
            is TagSettingsActivity -> "tag"
            is FilterSettingsActivity -> "filter"
            is PlaceSettingsActivity -> "place"
            is GoogleTaskListSettingsActivity -> "google_task_list"
            is BaseCaldavCalendarSettingsActivity -> "list"
            else -> "unknown"
        }

    protected open fun promptDelete() { baseViewModel.promptDelete(true) }

    /** Standard @Compose view content for descendants. Caller must wrap it to TasksTheme{} */
    @Composable
    protected fun BaseSettingsContent(
        title: String = toolbarTitle ?: "",
        requestKeyboard: Boolean = isNew,
        optionButton: @Composable () -> Unit = {
            if (!isNew) DeleteButton(toolbarTitle ?: "") { delete() }
        },
        fab: @Composable () -> Unit = {},
        headerContent: @Composable ColumnScope.() -> Unit = {},
        extensionContent: @Composable ColumnScope.() -> Unit = {},
    ) {
        val viewState = baseViewModel.viewState.collectAsStateWithLifecycle().value
        val color = if (viewState.color == 0) MaterialTheme.colorScheme.primary else Color(viewState.color)
        ListSettingsScaffold(
            title = title,
            color = color,
            promptDiscard = viewState.promptDiscard,
            showProgress = viewState.showProgress,
            dismissDiscardPrompt = { baseViewModel.promptDiscard(false) },
            save = { lifecycleScope.launch { save() } },
            discard = { finish() },
            actions = optionButton,
            fab = fab,
        ) {
            ListSettingsContent(
                hasPro = remember { inventory.purchasedThemes() },
                color = viewState.color,
                colors = remember { colorProvider.getThemeColors() },
                icon = viewState.icon ?: defaultIcon,
                text = viewState.title,
                error = viewState.error,
                requestKeyboard = requestKeyboard,
                isNew = isNew,
                setText = {
                    baseViewModel.setTitle(it)
                    baseViewModel.setError("")
                },
                setColor = {
                    baseViewModel.setColor(it)
                    firebase.logEvent(
                        R.string.event_settings_click,
                        R.string.param_type to "color_picker",
                        R.string.param_source to settingsSource,
                    )
                },
                pickIcon = { showIconPicker() },
                addShortcutToHome = { createShortcut(color) },
                addWidgetToHome = { createWidget() },
                headerContent = headerContent,
                extensionContent = extensionContent,
                purchase = {
                    startActivity(
                        Intent(this@BaseListSettingsActivity, PurchaseActivity::class.java)
                            .putExtra(PurchaseActivityViewModel.EXTRA_SOURCE, "list_colors")
                    )
                },
            )
        }
    }

    protected val canEditTaskListDefaults: Boolean
        get() = filter?.isWritable == true

    protected fun hasTaskListDefaultChanges(): Boolean = taskListDefaultsViewModel.hasChanges

    protected suspend fun saveTaskListDefaults() {
        taskListDefaultsViewModel.save()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    protected fun TaskListDefaultsContent() {
        val currentFilter = filter ?: return
        val filterKey = currentFilter.key()
        val accountType = (currentFilter as? CaldavFilter)
            ?.account
            ?.accountType
            ?: CaldavAccount.TYPE_LOCAL
        val viewModel = taskListDefaultsViewModel
        LaunchedEffect(filterKey) {
            viewModel.initialize(filterKey)
        }
        if (!viewModel.initialized) {
            return
        }

        var showTagsDialog by rememberSaveable { mutableStateOf(false) }
        var showPriorityDialog by rememberSaveable { mutableStateOf(false) }
        var showStartDateDialog by rememberSaveable { mutableStateOf(false) }
        var showDueDateDialog by rememberSaveable { mutableStateOf(false) }
        var showCalendarDialog by rememberSaveable { mutableStateOf(false) }
        var showRemindersDialog by rememberSaveable { mutableStateOf(false) }
        var showRandomReminderDialog by rememberSaveable { mutableStateOf(false) }
        var showRemindersModeDialog by rememberSaveable { mutableStateOf(false) }
        var showRecurrenceDialog by rememberSaveable { mutableStateOf(false) }
        var showRecurrenceFromDialog by rememberSaveable { mutableStateOf(false) }
        var showLocationDialog by rememberSaveable { mutableStateOf(false) }
        var showLocationReminderDialog by rememberSaveable { mutableStateOf(false) }

        SectionHeader(
            R.string.task_defaults,
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
        )
        Column(
            modifier = Modifier.padding(horizontal = SettingsContentPadding),
            verticalArrangement = Arrangement.spacedBy(SettingsCardGap),
        ) {
            SettingsItemCard(position = CardPosition.First) {
                PreferenceRow(
                    title = stringResource(R.string.default_tags),
                    icon = Icons.AutoMirrored.Outlined.Label,
                    summary = viewModel.tagsSummary,
                    onClick = { showTagsDialog = true },
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(R.string.EPr_default_importance_title),
                    icon = Icons.Outlined.Flag,
                    summary = viewModel.prioritySummary,
                    onClick = { showPriorityDialog = true },
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(R.string.default_start_date),
                    icon = Icons.Outlined.PendingActions,
                    summary = viewModel.startDateSummary,
                    onClick = { showStartDateDialog = true },
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(R.string.default_due_date),
                    icon = Icons.Outlined.Schedule,
                    summary = viewModel.dueDateSummary,
                    onClick = { showDueDateDialog = true },
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(R.string.default_calendar),
                    icon = Icons.Outlined.Event,
                    summary = viewModel.calendarSummary,
                    onClick = { showCalendarDialog = true },
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(R.string.EPr_default_reminders_title),
                    icon = Icons.Outlined.Notifications,
                    summary = viewModel.remindersSummary,
                    summaryMaxLines = Int.MAX_VALUE,
                    onClick = { showRemindersDialog = true },
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(R.string.rmd_EPr_defaultRemind_title),
                    summary = viewModel.randomReminderSummary,
                    onClick = { showRandomReminderDialog = true },
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(R.string.EPr_default_reminders_mode_title),
                    summary = viewModel.remindersModeSummary,
                    onClick = { showRemindersModeDialog = true },
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(R.string.default_recurrence),
                    icon = Icons.Outlined.Repeat,
                    summary = viewModel.recurrenceSummary,
                    onClick = { showRecurrenceDialog = true },
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(R.string.repeats_from),
                    summary = viewModel.recurrenceFromSummary,
                    onClick = { showRecurrenceFromDialog = true },
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(R.string.default_location),
                    icon = Icons.Outlined.Place,
                    summary = viewModel.locationSummary,
                    onClick = { showLocationDialog = true },
                )
            }
            SettingsItemCard(position = CardPosition.Middle) {
                PreferenceRow(
                    title = stringResource(R.string.EPr_default_location_reminder_title),
                    summary = viewModel.locationReminderSummary,
                    onClick = { showLocationReminderDialog = true },
                )
            }
            SettingsItemCard(position = CardPosition.Last) {
                PreferenceRow(
                    title = stringResource(R.string.reset_task_defaults),
                    icon = Icons.Outlined.Restore,
                    onClick = { viewModel.reset() },
                )
            }
        }

        if (showTagsDialog) {
            TaskListDefaultOverrideDialog(
                title = stringResource(R.string.default_tags),
                noneText = stringResource(R.string.none),
                chooseText = stringResource(R.string.default_tags),
                isGlobalSelected = viewModel.defaults.tagUids == null,
                isNoneSelected = viewModel.defaults.tagUids?.isEmpty() == true,
                onUseGlobal = viewModel::inheritTags,
                onNone = viewModel::clearTags,
                onChoose = {
                    tagsLauncher.launch(
                        Intent(this, TagPickerActivity::class.java)
                            .putParcelableArrayListExtra(
                                EXTRA_SELECTED,
                                ArrayList(viewModel.getTagsForEditor())
                            )
                    )
                },
                onDismiss = { showTagsDialog = false },
            )
        }
        if (showPriorityDialog) {
            InheritableListPreferenceDialog(
                title = stringResource(R.string.EPr_default_importance_title),
                entries = viewModel.priorityEntries,
                values = viewModel.priorityValues,
                currentValue = viewModel.defaults.priority?.toString(),
                onSelect = viewModel::setPriority,
                onDismiss = { showPriorityDialog = false },
            )
        }
        if (showStartDateDialog) {
            InheritableListPreferenceDialog(
                title = stringResource(R.string.default_start_date),
                entries = viewModel.startDateEntries,
                values = viewModel.startDateValues,
                currentValue = viewModel.defaults.hideUntil?.toString(),
                onSelect = viewModel::setStartDate,
                onDismiss = { showStartDateDialog = false },
            )
        }
        if (showDueDateDialog) {
            InheritableListPreferenceDialog(
                title = stringResource(R.string.default_due_date),
                entries = viewModel.dueDateEntries,
                values = viewModel.dueDateValues,
                currentValue = viewModel.defaults.dueDate?.toString(),
                onSelect = viewModel::setDueDate,
                onDismiss = { showDueDateDialog = false },
            )
        }
        if (showCalendarDialog) {
            TaskListDefaultOverrideDialog(
                title = stringResource(R.string.default_calendar),
                noneText = stringResource(R.string.dont_add_to_calendar),
                chooseText = stringResource(R.string.default_calendar),
                isGlobalSelected = viewModel.defaults.calendarId == null,
                isNoneSelected = viewModel.defaults.calendarId == TaskListDefaults.NO_CALENDAR,
                onUseGlobal = viewModel::inheritCalendar,
                onNone = { viewModel.setCalendar(null) },
                onChoose = {
                    newCalendarPicker(viewModel.getCalendarForPicker())
                        .show(supportFragmentManager, FRAG_TAG_CALENDAR_PICKER)
                },
                onDismiss = { showCalendarDialog = false },
            )
        }
        if (showRandomReminderDialog) {
            InheritableListPreferenceDialog(
                title = stringResource(R.string.rmd_EPr_defaultRemind_title),
                entries = viewModel.randomReminderEntries,
                values = viewModel.randomReminderValues,
                currentValue = viewModel.defaults.randomReminderHours?.toString(),
                onSelect = viewModel::setRandomReminder,
                onDismiss = { showRandomReminderDialog = false },
            )
        }
        if (showRemindersModeDialog) {
            InheritableListPreferenceDialog(
                title = stringResource(R.string.EPr_default_reminders_mode_title),
                entries = viewModel.remindersModeEntries,
                values = viewModel.remindersModeValues,
                currentValue = viewModel.defaults.ringMode?.toString(),
                onSelect = viewModel::setRemindersMode,
                onDismiss = { showRemindersModeDialog = false },
            )
        }
        if (showRecurrenceDialog) {
            TaskListDefaultOverrideDialog(
                title = stringResource(R.string.default_recurrence),
                noneText = stringResource(R.string.repeat_option_does_not_repeat),
                chooseText = stringResource(R.string.default_recurrence),
                isGlobalSelected = viewModel.defaults.recurrence == null,
                isNoneSelected = viewModel.defaults.recurrence == TaskListDefaults.NO_RECURRENCE,
                onUseGlobal = viewModel::inheritRecurrence,
                onNone = { viewModel.setRecurrence(null) },
                onChoose = {
                    BasicRecurrenceDialog
                        .newBasicRecurrenceDialog(
                            rrule = viewModel.getRecurrenceRuleForPicker(),
                            dueDate = 0,
                            accountType = accountType,
                        )
                        .show(supportFragmentManager, FRAG_TAG_BASIC_RECURRENCE)
                },
                onDismiss = { showRecurrenceDialog = false },
            )
        }
        if (showRecurrenceFromDialog) {
            InheritableListPreferenceDialog(
                title = stringResource(R.string.repeats_from),
                entries = viewModel.recurrenceFromEntries,
                values = viewModel.recurrenceFromValues,
                currentValue = viewModel.defaults.repeatFrom?.toString(),
                onSelect = viewModel::setRecurrenceFrom,
                onDismiss = { showRecurrenceFromDialog = false },
            )
        }
        if (showLocationDialog) {
            TaskListDefaultOverrideDialog(
                title = stringResource(R.string.default_location),
                noneText = stringResource(R.string.none),
                chooseText = stringResource(R.string.choose_a_location),
                isGlobalSelected = viewModel.defaults.locationUid == null,
                isNoneSelected = viewModel.defaults.locationUid == TaskListDefaults.NO_LOCATION,
                onUseGlobal = viewModel::inheritLocation,
                onNone = { viewModel.setLocation(null) },
                onChoose = {
                    val intent = Intent(this, LocationPickerActivity::class.java)
                    viewModel.getLocationForPicker()?.let { intent.putExtra(EXTRA_PLACE, it) }
                    locationLauncher.launch(intent)
                },
                onDismiss = { showLocationDialog = false },
            )
        }
        if (showLocationReminderDialog) {
            InheritableListPreferenceDialog(
                title = stringResource(R.string.EPr_default_location_reminder_title),
                entries = viewModel.locationReminderEntries,
                values = viewModel.locationReminderValues,
                currentValue = viewModel.defaults.locationReminder?.toString(),
                onSelect = viewModel::setLocationReminder,
                onDismiss = { showLocationReminderDialog = false },
            )
        }
        if (showRemindersDialog) {
            TaskListRemindersDialog(
                viewModel = viewModel,
                onDismiss = { showRemindersDialog = false },
            )
        }
    }

    @Composable
    private fun TaskListDefaultOverrideDialog(
        title: String,
        noneText: String,
        chooseText: String,
        isGlobalSelected: Boolean,
        isNoneSelected: Boolean,
        onUseGlobal: () -> Unit,
        onNone: () -> Unit,
        onChoose: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column {
                    PreferenceChoiceRow(
                        text = stringResource(R.string.use_global_default_short),
                        selected = isGlobalSelected,
                    ) {
                        onUseGlobal()
                        onDismiss()
                    }
                    PreferenceChoiceRow(
                        text = noneText,
                        selected = isNoneSelected,
                    ) {
                        onNone()
                        onDismiss()
                    }
                    PreferenceChoiceRow(
                        text = chooseText,
                        selected = !isGlobalSelected && !isNoneSelected,
                    ) {
                        onChoose()
                        onDismiss()
                    }
                }
            },
            confirmButton = {},
        )
    }

    @Composable
    private fun InheritableListPreferenceDialog(
        title: String,
        entries: Array<String>,
        values: Array<String>,
        currentValue: String?,
        onSelect: (String?) -> Unit,
        onDismiss: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column {
                    PreferenceChoiceRow(
                        text = stringResource(R.string.use_global_default_short),
                        selected = currentValue == null,
                    ) {
                        onSelect(null)
                        onDismiss()
                    }
                    entries.forEachIndexed { index, entry ->
                        PreferenceChoiceRow(
                            text = entry,
                            selected = values[index] == currentValue,
                        ) {
                            onSelect(values[index])
                            onDismiss()
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    @Composable
    private fun PreferenceChoiceRow(
        text: String,
        selected: Boolean,
        onClick: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
            )
            Text(
                text = text,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
    @Composable
    private fun TaskListRemindersDialog(
        viewModel: TaskListDefaultsViewModel,
        onDismiss: () -> Unit,
    ) {
        val reminderViewModel: ReminderControlSetViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
        val viewState = reminderViewModel.viewState.collectAsStateWithLifecycle().value
        var alarms by remember { mutableStateOf(viewModel.getRemindersForEditor().toSet()) }

        BasicAlertDialog(onDismissRequest = onDismiss) {
            DefaultRemindersList(
                alarms = alarms,
                onAlarmClick = { alarm ->
                    reminderViewModel.setReplace(alarm)
                    reminderViewModel.showAddAlarm(visible = true)
                },
                onAlarmRemove = { alarm ->
                    alarms = alarms - alarm
                    viewModel.setReminders(alarms.toList())
                },
                onAddClick = { reminderViewModel.showAddAlarm(visible = true) },
                extraContent = {
                    PreferenceChoiceRow(
                        text = stringResource(R.string.use_global_default_short),
                        selected = viewModel.defaults.alarms == null,
                    ) {
                        viewModel.setReminders(null)
                        alarms = viewModel.getRemindersForEditor().toSet()
                    }
                    PreferenceChoiceRow(
                        text = stringResource(R.string.no_reminders),
                        selected = viewModel.defaults.alarms?.isEmpty() == true,
                    ) {
                        alarms = emptySet()
                        viewModel.setReminders(emptyList())
                    }
                },
            )
        }

        AddAlarmDialog(
            viewState = viewState,
            existingAlarms = alarms.toPersistentSet(),
            showRandom = false,
            showDateTimePicker = false,
            addAlarm = {
                viewState.replace?.let { old -> alarms = alarms - old }
                alarms = alarms + it
                viewModel.setReminders(alarms.toList())
            },
            addRandom = { },
            addCustom = { reminderViewModel.showCustomDialog(visible = true) },
            pickDateAndTime = { },
            dismiss = { reminderViewModel.showAddAlarm(visible = false) },
        )

        if (viewState.showCustomDialog) {
            AddReminderDialog.AddCustomReminderDialog(
                alarm = viewState.replace,
                updateAlarm = {
                    viewState.replace?.let { old -> alarms = alarms - old }
                    alarms = alarms + it
                    viewModel.setReminders(alarms.toList())
                },
                closeDialog = { reminderViewModel.showCustomDialog(visible = false) }
            )
        }
    }

    protected fun createShortcut(color: Color) {
        filter?.let { f ->
            val filterId = defaultFilterProvider.getFilterPreferenceValue(f)
            val shortcutInfo = ShortcutInfoCompat.Builder(this, UUIDHelper.newUUID())
                .setShortLabel(baseViewModel.title.takeIf { it.isNotBlank() } ?: getString(R.string.app_name))
                .setIcon(
                    baseViewModel.icon
                        ?.let { icon ->
                            try {
                                createShortcutIcon(
                                    context = this,
                                    backgroundColor = color,
                                    icon = icon,
                                    iconColor = contentColorFor(color.toArgb()),
                                )
                            } catch (e: Exception) {
                                firebase.reportException(e)
                                null
                            }
                        }
                        ?: createShortcutIcon(this, backgroundColor = color)
                )
                .setIntent(TaskIntents.getTaskListByIdIntent(this, filterId))
                .build()

            val pinnedShortcutCallbackIntent = ShortcutManagerCompat
                .createShortcutResultIntent(this, shortcutInfo)

            // Create callback intent
            val successCallback = PendingIntent.getBroadcast(
                this, 0,
                pinnedShortcutCallbackIntent,
                PendingIntent.FLAG_IMMUTABLE
            )

            ShortcutManagerCompat.requestPinShortcut(
                this,
                shortcutInfo,
                successCallback.intentSender
            )

            firebase.logEvent(R.string.event_create_shortcut, R.string.param_type to "settings_activity")
        }
    }

    protected fun createWidget() {
        val filter = filter ?: return
        val appWidgetManager = getSystemService(AppWidgetManager::class.java)
        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            val provider = ComponentName(this, TasksWidget::class.java)
            val configIntent = Intent(this, RequestPinWidgetReceiver::class.java).apply {
                action = RequestPinWidgetReceiver.ACTION_CONFIGURE_WIDGET
                putExtra(EXTRA_FILTER, defaultFilterProvider.getFilterPreferenceValue(filter))
                putExtra(EXTRA_COLOR, baseViewModel.color)
            }
            val successCallback = PendingIntent.getBroadcast(
                this,
                filter.hashCode(),
                configIntent,
                if (atLeastS()) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
            )
            appWidgetManager.requestPinAppWidget(provider, null, successCallback)
            firebase.logEvent(R.string.event_create_widget, R.string.param_type to "settings_activity")
        }
    }

    companion object {
        private const val FRAG_TAG_CALENDAR_PICKER = "frag_tag_calendar_picker"
        private const val FRAG_TAG_BASIC_RECURRENCE = "frag_tag_basic_recurrence"

        fun createShortcutIcon(context: Context, backgroundColor: Color): IconCompat {
            val size = context.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

            Canvas(bitmap).apply {
                // Draw circular background
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = backgroundColor.toArgb()
                }
                drawCircle(size/2f, size/2f, size/2f, paint)

                // Draw foreground icon
                val foreground = ResourcesCompat.getDrawable(
                    context.resources,
                    org.tasks.kmp.R.drawable.ic_launcher_no_shadow_foreground,
                    null
                )
                foreground?.let {
                    it.setBounds(0, 0, size, size)
                    it.draw(this)
                }
            }

            return IconCompat.createWithBitmap(bitmap)
        }

        fun createShortcutIcon(
            context: Context,
            backgroundColor: Color,
            icon: String,
            iconColor: Color = Color.White,
            iconSizeDp: Int = 24
        ): IconCompat {
            val size = context.resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

            Canvas(bitmap).apply {
                // Draw circular background
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = backgroundColor.toArgb()
                }
                drawCircle(size/2f, size/2f, size/2f, paint)

                // Create and draw IconicsDrawable
                val drawable = IconicsDrawable(context, OutlinedGoogleMaterial.getIcon("gmo_$icon")).apply {
                    colorInt = iconColor.toArgb()
                    sizeDp = iconSizeDp
                }

                // Center the icon
                val iconSize = (size * 0.5f).toInt()
                drawable.setBounds(
                    (size - iconSize) / 2,
                    (size - iconSize) / 2,
                    (size + iconSize) / 2,
                    (size + iconSize) / 2
                )
                drawable.draw(this)
            }

            return IconCompat.createWithBitmap(bitmap)
        }
    }
}
