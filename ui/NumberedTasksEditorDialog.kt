package com.mlib.dashboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mlib.dashboard.MonthTask
import com.mlib.dashboard.WeekTask
import com.mlib.dashboard.nextId
import com.mlib.future.FutureTheme
import com.mlib.future.components.*
import java.util.Calendar
import java.util.Locale

private data class NumberedItem(
	val id: Int,
	val name: String,
	val number: Int,
	val year: Int
)

@Composable
fun WeeklyTasksEditorDialog(
	tasks: List<WeekTask>,
	onSave: (List<WeekTask>) -> Unit
) {
	var working by remember { mutableStateOf(tasks) }

	val saveAndClose: () -> Unit = { onSave(working) }

	FutureDialog(title = "Weekly Tasks", onDismissRequest = saveAndClose) {
		NumberedTasksBody(
			items = working.map { NumberedItem(it.id, it.name, it.week, it.yyyy) },
			numberLabel = "Week",
			numberPlaceholder = "1-53",
			minNumber = 1,
			maxNumber = 53,
			onDelete = { id -> working = working.filterNot { it.id == id } },
			onAdd = { n, year, name ->
				working = working + WeekTask(nextId(working.map { it.id }), name, n, year)
			},
			onClose = saveAndClose
		)
	}
}

@Composable
fun MonthlyTasksEditorDialog(
	tasks: List<MonthTask>,
	onSave: (List<MonthTask>) -> Unit
) {
	var working by remember { mutableStateOf(tasks) }

	val saveAndClose: () -> Unit = { onSave(working) }

	FutureDialog(title = "Monthly Tasks", onDismissRequest = saveAndClose) {
		NumberedTasksBody(
			items = working.map { NumberedItem(it.id, it.name, it.mm, it.yyyy) },
			numberLabel = "Month",
			numberPlaceholder = "1-12",
			minNumber = 1,
			maxNumber = 12,
			onDelete = { id -> working = working.filterNot { it.id == id } },
			onAdd = { n, year, name ->
				working = working + MonthTask(nextId(working.map { it.id }), name, n, year)
			},
			onClose = saveAndClose
		)
	}
}

@Composable
private fun NumberedTasksBody(
	items: List<NumberedItem>,
	numberLabel: String,
	numberPlaceholder: String,
	minNumber: Int,
	maxNumber: Int,
	onDelete: (Int) -> Unit,
	onAdd: (number: Int, year: Int, name: String) -> Unit,
	onClose: () -> Unit
) {
	val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
	var numberInput by remember { mutableStateOf("") }
	var yearInput by remember { mutableStateOf(currentYear.toString()) }
	var nameInput by remember { mutableStateOf("") }

	val parsedNumber = numberInput.toIntOrNull()
	val parsedYear = yearInput.toIntOrNull()
	val canAdd = parsedNumber != null &&
	parsedNumber in minNumber..maxNumber &&
	parsedYear != null && parsedYear in 1970..2100 &&
	nameInput.isNotBlank()

	val grouped = remember(items) {
		items
		.sortedWith(compareBy({ it.year }, { it.number }, { it.id }))
		.groupBy { it.year to it.number }
	}

	Column(modifier = Modifier.fillMaxWidth()) {
		if (grouped.isEmpty()) {
			FutureTextMuted("No tasks yet.")
		} else {
			Column(
				modifier = Modifier
				.fillMaxWidth()
				.heightIn(max = 220.dp)
				.verticalScroll(rememberScrollState())
			) {
				grouped.forEach { (key, group) ->
					val (year, number) = key
					FutureSeparator(Modifier.fillMaxWidth())
					Spacer(Modifier.height(8.dp))
					FutureText(
						"%02d.%04d".format(Locale.US, number, year),
						variant = TextVariant.Highlighted,
						style = FutureTheme.typography.labelLarge
					)
					Spacer(Modifier.height(4.dp))
					group.forEach { item ->
						Row(
							modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
							verticalAlignment = Alignment.CenterVertically
						) {
							FutureText(
								"%d. %s".format(item.id, item.name),
								modifier = Modifier.weight(1f)
							)
							Box(
								modifier = Modifier
								.size(32.dp)
								.clickable { onDelete(item.id) },
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

		Spacer(Modifier.height(8.dp))

		FutureTextField(
			label = "Name",
			placeholder = "Add Name...",
			value = nameInput,
			onValueChange = { nameInput = it },
			modifier = Modifier.fillMaxWidth()
		)

		Spacer(Modifier.height(8.dp))

		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.Bottom
		) {
			FutureNumberInput(
				label = numberLabel,
				value = numberInput,
				onValueChange = { numberInput = it.filter { c -> c.isDigit() } },
				placeholder = numberPlaceholder,
				modifier = Modifier.width(96.dp)
			)
			FutureNumberInput(
				label = "Year",
				value = yearInput,
				onValueChange = { yearInput = it.filter { c -> c.isDigit() } },
				placeholder = "2026",
				modifier = Modifier.width(96.dp)
			)
			Spacer(Modifier.weight(1f))
			FutureButton(
				onClick = {
					val n = parsedNumber
					val y = parsedYear
					if (n != null && y != null) {
						onAdd(n, y, nameInput.trim())
						numberInput = ""
						yearInput = currentYear.toString()
						nameInput = ""
					}
				},
				type = ButtonType.Highlighted,
				enabled = canAdd,
				icon = { FutureIconAdd() }
			)
		}
	}
}
