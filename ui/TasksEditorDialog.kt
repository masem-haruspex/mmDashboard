package com.mlib.dashboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mlib.dashboard.Task
import com.mlib.dashboard.nextId
import com.mlib.future.FutureTheme
import com.mlib.future.components.*
import java.util.Locale

private sealed interface TasksScreen {
	object ListScreen : TasksScreen
	object PickDate : TasksScreen
	data class EnterName(val yyyy: Int, val mm: Int, val dd: Int) : TasksScreen
}

@Composable
fun TasksEditorDialog(
	tasks: List<Task>,
	onSave: (List<Task>) -> Unit
) {
	var working by remember { mutableStateOf(tasks) }
	var screen by remember { mutableStateOf<TasksScreen>(TasksScreen.ListScreen) }
	val saveAndClose: () -> Unit = { onSave(working) }

	FutureDialog(title = "Tasks", onDismissRequest = saveAndClose) {
		when (val s = screen) {
			TasksScreen.ListScreen -> TasksListContent(
				tasks = working,
				onDelete = { id -> working = working.filterNot { it.id == id } },
				onAddClick = { screen = TasksScreen.PickDate },
				onClose = saveAndClose
			)
			TasksScreen.PickDate -> PickDateContent(
				onCancel = { screen = TasksScreen.ListScreen },
				onPicked = { y, m, d -> screen = TasksScreen.EnterName(y, m, d) }
			)
			is TasksScreen.EnterName -> EnterNameContent(
				yyyy = s.yyyy, mm = s.mm, dd = s.dd,
				onCancel = { screen = TasksScreen.ListScreen },
				onSave = { name ->
					val newId = nextId(working.map { it.id })
					working = working + Task(newId, name, s.dd, s.mm, s.yyyy)
					screen = TasksScreen.ListScreen
				}
			)
		}
	}
}

@Composable
private fun TasksListContent(
	tasks: List<Task>,
	onDelete: (Int) -> Unit,
	onAddClick: () -> Unit,
	onClose: () -> Unit
) {
	val grouped = remember(tasks) {
		tasks
		.sortedWith(compareBy({ it.yyyy }, { it.mm }, { it.dd }, { it.id }))
		.groupBy { Triple(it.yyyy, it.mm, it.dd) }
	}

	Column(modifier = Modifier.fillMaxWidth()) {
		if (grouped.isEmpty()) {
			FutureTextMuted("No tasks yet.")
		} else {
			Column(
				modifier = Modifier
				.fillMaxWidth()
				.heightIn(max = 260.dp)
				.verticalScroll(rememberScrollState())
			) {
				grouped.forEach { (dateKey, group) ->
					val (yyyy, mm, dd) = dateKey
					FutureSeparator(Modifier.fillMaxWidth())
					Spacer(Modifier.height(8.dp))
					FutureText(
						"%02d.%02d.%04d".format(Locale.US, dd, mm, yyyy),
						variant = TextVariant.Highlighted,
						style = FutureTheme.typography.labelLarge
					)
					Spacer(Modifier.height(4.dp))
					group.forEach { task ->
						Row(
							modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
							verticalAlignment = Alignment.CenterVertically
						) {
							FutureText(
								"%d. %s".format(task.id, task.name),
								modifier = Modifier.weight(1f)
							)
							Box(
								modifier = Modifier
								.size(32.dp)
								.clickable { onDelete(task.id) },
								contentAlignment = Alignment.Center
							) {
								FutureIconClose(size = 18, color = FutureTheme.config.colors.error)
							}
						}
					}
					Spacer(Modifier.height(8.dp))
				}
			}
		}

		FutureSeparator(Modifier.fillMaxWidth())
		Spacer(Modifier.height(16.dp))

		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			FutureButton(
				"Add New Task",
				onClick = onAddClick,
				type = ButtonType.Highlighted,
				icon = { FutureIconAdd() },
				modifier = Modifier.weight(1f)
			)
		}
	}
}

@Composable
private fun PickDateContent(
	onCancel: () -> Unit,
	onPicked: (yyyy: Int, mm: Int, dd: Int) -> Unit
) {
	var dateStr by remember { mutableStateOf("") }

	LaunchedEffect(dateStr) {
		if (dateStr.isNotEmpty()) {
			val parts = dateStr.split("-")
			if (parts.size == 3) {
				val y = parts[0].toIntOrNull()
				val m = parts[1].toIntOrNull()
				val d = parts[2].toIntOrNull()
				if (y != null && m != null && d != null) onPicked(y, m, d)
			}
		}
	}

	Column(modifier = Modifier.fillMaxWidth()) {
		FutureTextMuted("Pick a date for the new task")
		Spacer(Modifier.height(12.dp))
		FutureDatePicker(
			label = "Date",
			value = dateStr,
			onValueChange = { dateStr = it }
		)
		Spacer(Modifier.height(16.dp))
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.End
		) {
			FutureButton(onClick = onCancel, text = "Back")
		}
	}
}

@Composable
private fun EnterNameContent(
	yyyy: Int, mm: Int, dd: Int,
	onCancel: () -> Unit,
	onSave: (String) -> Unit
) {
	var name by remember { mutableStateOf("") }

	Column(modifier = Modifier.fillMaxWidth()) {
		FutureTextMuted("Task for %02d.%02d.%04d".format(Locale.US, dd, mm, yyyy))
		Spacer(Modifier.height(12.dp))
		FutureTextField(
			label = "Task name",
			value = name,
			onValueChange = { name = it }
		)
		Spacer(Modifier.height(16.dp))
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			FutureButton(onClick = onCancel, text = "Back", modifier = Modifier.weight(1f))
			FutureButton(
				onClick = { onSave(name.trim()) },
				text = "Add",
				enabled = name.isNotBlank(),
				type = ButtonType.Highlighted,
				modifier = Modifier.weight(1f)
			)
		}
	}
}
