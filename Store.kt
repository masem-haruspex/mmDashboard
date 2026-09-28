package com.mlib.dashboard

import android.content.Context
import java.io.File
import java.util.Calendar
import java.util.Locale

lateinit var databaseDir: File

fun initFiles(context: Context) {
	val base = context.getExternalFilesDir(null) ?: context.filesDir
	databaseDir = File(base, "database").apply { mkdirs() }
}

data class Task(val id: Int, val name: String, val dd: Int, val mm: Int, val yyyy: Int)
data class WeekTask(val id: Int, val name: String, val week: Int, val yyyy: Int)
data class MonthTask(val id: Int, val name: String, val mm: Int, val yyyy: Int)

private fun parseLines(file: File): List<List<String>> {
	if (!file.isFile) return emptyList()
	return file.readLines().mapNotNull { line ->
		val parts = line.split("|")
		if (parts.size >= 2 && parts[0].toIntOrNull() != null) parts else null
	}
}

fun loadTasks(): List<Task> =
parseLines(File(databaseDir, "tasks.txt")).mapNotNull { p ->
	if (p.size >= 5) Task(
		p[0].toInt(), p[1],
		p[2].toIntOrNull() ?: 0, p[3].toIntOrNull() ?: 0, p[4].toIntOrNull() ?: 0
	) else null
}

fun loadWeekTasks(): List<WeekTask> =
parseLines(File(databaseDir, "week_tasks.txt")).mapNotNull { p ->
	if (p.size >= 4) WeekTask(
		p[0].toInt(), p[1],
		p[2].toIntOrNull() ?: 0, p[3].toIntOrNull() ?: 0
	) else null
}

fun loadMonthTasks(): List<MonthTask> =
parseLines(File(databaseDir, "month_tasks.txt")).mapNotNull { p ->
	if (p.size >= 4) MonthTask(
		p[0].toInt(), p[1],
		p[2].toIntOrNull() ?: 0, p[3].toIntOrNull() ?: 0
	) else null
}


fun saveTasks(tasks: List<Task>) {
	File(databaseDir, "tasks.txt")
	.writeText(tasks.joinToString("\n") { "${it.id}|${it.name}|${it.dd}|${it.mm}|${it.yyyy}" })
}

fun saveWeekTasks(tasks: List<WeekTask>) {
	File(databaseDir, "week_tasks.txt")
	.writeText(tasks.joinToString("\n") { "${it.id}|${it.name}|${it.week}|${it.yyyy}" })
}

fun saveMonthTasks(tasks: List<MonthTask>) {
	File(databaseDir, "month_tasks.txt")
	.writeText(tasks.joinToString("\n") { "${it.id}|${it.name}|${it.mm}|${it.yyyy}" })
}

fun nextId(existingIds: List<Int>): Int = (existingIds.maxOrNull() ?: 0) + 1

fun getGermanPhrase(dayOfYear: Int): String {
	val f = File(databaseDir, "german_texts.txt")
	if (!f.isFile) return "..."
	var n = 0
	f.bufferedReader().use { reader ->
		while (true) {
			val line = reader.readLine() ?: break
			n++
			if (n == dayOfYear) return line.ifBlank { "..." }
		}
	}
	return "..."
}

fun isoWeekOfYear(cal: Calendar): Int {
	val c = cal.clone() as Calendar
	c.firstDayOfWeek = Calendar.MONDAY
	c.minimalDaysInFirstWeek = 4
	return c.get(Calendar.WEEK_OF_YEAR)
}

fun firstWeekdayMonday0(yyyy: Int, mm: Int): Int {
	val c = Calendar.getInstance()
	c.set(yyyy, mm - 1, 1, 12, 0, 0)
	return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
}

fun daysInMonth(yyyy: Int, mm: Int): Int {
	val c = Calendar.getInstance()
	c.set(yyyy, mm - 1, 1)
	return c.getActualMaximum(Calendar.DAY_OF_MONTH)
}

val MONTH_NAMES = listOf(
	"January", "February", "March", "April", "May", "June",
	"July", "August", "September", "October", "November", "December"
)
