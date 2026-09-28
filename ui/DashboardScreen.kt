package com.mlib.dashboard.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mlib.dashboard.MonthTask
import com.mlib.dashboard.Task
import com.mlib.dashboard.WxDay
import com.mlib.dashboard.WxForecast
import com.mlib.dashboard.WxSlot
import com.mlib.dashboard.Weather
import com.mlib.dashboard.WeekTask
import com.mlib.dashboard.MONTH_NAMES
import com.mlib.dashboard.daysInMonth
import com.mlib.dashboard.firstWeekdayMonday0
import com.mlib.dashboard.getGermanPhrase
import com.mlib.dashboard.isoWeekOfYear
import com.mlib.dashboard.loadMonthTasks
import com.mlib.dashboard.loadTasks
import com.mlib.dashboard.loadWeekTasks
import com.mlib.dashboard.saveMonthTasks
import com.mlib.dashboard.saveTasks
import com.mlib.dashboard.saveWeekTasks
import com.mlib.future.FutureTheme
import com.mlib.future.components.*
import com.mlib.future.modifiers.*
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.gestures.detectTapGestures
import com.mlib.future.components.RadialMenuItem
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import com.mlib.future.components.RadialMenuOverlay
import com.mlib.future.components.RadialDots

val CARDS_H_PADDING = 14.dp
val CARDS_V_PADDING = 12.dp

private data class DashboardData(
	val tasks: List<Task>,
	val weekTasks: List<WeekTask>,
	val monthTasks: List<MonthTask>,
	val phrase: String,
	val dateCal: Calendar
)

private suspend fun loadDashboardData(): DashboardData = withContext(Dispatchers.IO) {
	val cal = Calendar.getInstance()
	DashboardData(
		tasks = loadTasks(),
		weekTasks = loadWeekTasks(),
		monthTasks = loadMonthTasks(),
		phrase = getGermanPhrase(cal.get(Calendar.DAY_OF_YEAR)),
		dateCal = cal
	)
}

private class OverscrollGestureState {
	var drawerArmed = false
	var shadeArmed = false
	var accumUp = 0f
	var accumDown = 0f
}

@Composable
fun DashboardScreen(onOpenApps: () -> Unit, onOpenSettings: () -> Unit) {
	val config = FutureTheme.config
	val context = LocalContext.current
	val listState = rememberLazyListState()
	val scope = rememberCoroutineScope()

	var clock by remember { mutableStateOf(Calendar.getInstance()) }
	var dateCal by remember { mutableStateOf(Calendar.getInstance()) }
	var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
	var weekTasks by remember { mutableStateOf<List<WeekTask>>(emptyList()) }
	var monthTasks by remember { mutableStateOf<List<MonthTask>>(emptyList()) }
	var phrase by remember { mutableStateOf("...") }
	var weather by remember { mutableStateOf<WxForecast?>(null) }

	var showTasksEditor by remember { mutableStateOf(false) }
	var showWeeklyEditor by remember { mutableStateOf(false) }
	var showMonthlyEditor by remember { mutableStateOf(false) }

	val radialPreferences = remember { RadialPreferences(context) }
	var radialApps by remember { mutableStateOf<List<String>>(emptyList()) }

	var radialOpen by remember { mutableStateOf(false) }
	var radialEditing by remember { mutableStateOf(false) }
	var showRadialAdd by remember { mutableStateOf(false) }
	var radialSelectedIndex by remember { mutableStateOf<Int?>(null) }
	var radialFingerPos by remember { mutableStateOf(Offset.Zero) }
	val radialIconBounds = remember { mutableStateMapOf<Int, Rect>() }

	val buttonProgress by animateFloatAsState(
		targetValue = if (radialOpen) 1f else 0f,
		animationSpec = tween(300, easing = EaseOutQuart),
		label = "radial_button"
	)

		val blurRadius by animateDpAsState(
		targetValue = if (radialOpen) 12.dp else 0.dp,
		animationSpec = tween(120, easing = EaseOutQuart),
		label = "radial_blur"
	)

	LaunchedEffect(Unit) {
		radialPreferences.flow.collect { radialApps = it }
	}

	val radialItems = remember(radialApps) {
		val pm = context.packageManager
		radialApps.map { pkg ->
			val label = runCatching {
				pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
			}.getOrDefault(pkg)
			RadialMenuItem(
				id = pkg,
				label = label,
				icon = { m -> RadialAppIcon(pkg, m) }
			)
		}
	}

	val radialItemsState = rememberUpdatedState(radialItems)

	fun applyData(d: DashboardData) {
		tasks = d.tasks
		weekTasks = d.weekTasks
		monthTasks = d.monthTasks
		phrase = d.phrase
		dateCal = d.dateCal
	}

		BackHandler(enabled = radialEditing) {
		radialEditing = false
		radialOpen = false
	}

	LaunchedEffect(Unit) {
		var lastMinute = -1
		while (true) {
			val c = Calendar.getInstance()
			val minute = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
			if (minute != lastMinute) {
				lastMinute = minute
				clock = c
			}
			delay(1_000)
		}
	}

	LaunchedEffect(Unit) {
		applyData(loadDashboardData())
		weather = Weather.get()

		var lastDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
		while (true) {
			delay(60_000)
			val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
			if (today != lastDay) {
				lastDay = today
				applyData(loadDashboardData())
				weather = Weather.get()
			}
		}
	}

	val lifecycleOwner = LocalLifecycleOwner.current
	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) {
				scope.launch {
					applyData(loadDashboardData())
					if (weather?.valid != true) weather = Weather.get()
				}
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}

	val cal = dateCal
	val yyyy = cal.get(Calendar.YEAR)
	val mm = cal.get(Calendar.MONTH) + 1
	val dd = cal.get(Calendar.DAY_OF_MONTH)
	val week = isoWeekOfYear(cal)

	val todayTasks = tasks.filter { it.yyyy == yyyy && it.mm == mm && it.dd == dd }
	val taskDays = tasks.filter { it.yyyy == yyyy && it.mm == mm }.map { it.dd }.toSet()

	val weekGroups = weekTasks
	.sortedWith(compareBy({ it.yyyy }, { it.week }))
	.groupBy { "%02d.%04d".format(Locale.US, it.week, it.yyyy) }
	.mapValues { (_, list) -> list.map { it.id to it.name } }
	val monthGroups = monthTasks
	.sortedWith(compareBy({ it.yyyy }, { it.mm }))
	.groupBy { "%02d.%04d".format(Locale.US, it.mm, it.yyyy) }
	.mapValues { (_, list) -> list.map { it.id to it.name } }

	val density = LocalDensity.current
	val thresholdPx = with(density) { 140.dp.toPx() }
	var overscrollPx by remember { mutableStateOf(0f) }
	var topOverscrollPx by remember { mutableStateOf(0f) }
	val overscrollFraction = (overscrollPx / thresholdPx).coerceIn(0f, 1f)
	val topOverscrollFraction = (topOverscrollPx / thresholdPx).coerceIn(0f, 1f)
	val openApps by rememberUpdatedState(onOpenApps)

	val gestureState = remember { OverscrollGestureState() }
	val gestureArmModifier = Modifier.pointerInput(Unit) {
		awaitEachGesture {
			awaitFirstDown(requireUnconsumed = false)

			val info = listState.layoutInfo
			val viewportHeight =
			(info.viewportEndOffset - info.viewportStartOffset).toFloat()
			val grace = viewportHeight * 0.2f

			val nearBottom = !listState.canScrollForward || run {
				val last = info.visibleItemsInfo.lastOrNull()
				last != null &&
				last.index == info.totalItemsCount - 1 &&
				(info.viewportEndOffset - (last.offset + last.size)) <= grace
			}

			val nearTop = !listState.canScrollBackward || (
				listState.firstVisibleItemIndex == 0 &&
				listState.firstVisibleItemScrollOffset <= grace
			)

			gestureState.drawerArmed = nearBottom
			gestureState.shadeArmed = nearTop

			waitForUpOrCancellation()
		}
	}

	val radialGestureModifier = Modifier.pointerInput(Unit) {
		val btnPx = with(density) { 56.dp.toPx() }
		val padPx = with(density) { 24.dp.toPx() }
		val longPressMs = 180L

		awaitEachGesture {
						if (radialEditing) {
				awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
				return@awaitEachGesture
			}
			val down = awaitFirstDown(
				requireUnconsumed = false,
				pass = PointerEventPass.Initial
			)

			val cornerX = size.width - padPx - btnPx / 2f
			val cornerY = size.height - padPx - btnPx / 2f
			val dxDown = down.position.x - cornerX
			val dyDown = down.position.y - cornerY
			val hitR = btnPx * 0.9f
			if (dxDown * dxDown + dyDown * dyDown > hitR * hitR) {
				return@awaitEachGesture
			}

			down.consume()
			radialFingerPos = down.position

			val items = radialItemsState.value

			if (items.isEmpty()) {
				val job = scope.launch {
					delay(longPressMs)
					radialOpen = true
					radialEditing = true
				}
				while (true) {
					val ev = awaitPointerEvent(PointerEventPass.Initial)
					val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
					ch.consume()
					if (!ch.pressed) {
						job.cancel()
						break
					}
				}
				return@awaitEachGesture
			}

			var longPressFired = false
			val holdJob = scope.launch {
				delay(longPressMs)
				longPressFired = true
				radialOpen = true
			}

			while (true) {
				val ev = awaitPointerEvent(PointerEventPass.Initial)
				val ch = ev.changes.firstOrNull { it.id == down.id } ?: break

				if (ch.pressed) {
					ch.consume()
					radialFingerPos = ch.position
					if (longPressFired) {
						radialSelectedIndex = radialIconBounds.entries
						.firstOrNull { (_, r) -> r.contains(ch.position) }
						?.key
					}
				} else {
					ch.consume()
					holdJob.cancel()
										if (longPressFired) {
						val sel = radialSelectedIndex
						if (sel != null && sel in items.indices) {
							launchPackage(context, items[sel].id)
							radialOpen = false
							radialSelectedIndex = null
						} else {
							val cx = size.width / 2f
							val cy = size.height * 0.80f // 0.80f is the vertialBias
							val dxUp = ch.position.x - cx
							val dyUp = ch.position.y - cy
							if (dxUp * dxUp + dyUp * dyUp <= hitR * hitR) {
								radialEditing = true
							} else {
								radialOpen = false
							}
							radialSelectedIndex = null
						}
					}
					break
				}
			}
		}
	}

	val overscrollConnection = remember(thresholdPx, context) {
		object : NestedScrollConnection {
			override fun onPostScroll(
				consumed: Offset,
				available: Offset,
				source: NestedScrollSource
			): Offset {
				val dy = available.y

				if (dy > 0) {
					gestureState.accumUp = 0f
					overscrollPx = 0f
					if (!listState.canScrollBackward && gestureState.shadeArmed) {
						gestureState.accumDown += dy
						topOverscrollPx = gestureState.accumDown
						if (gestureState.accumDown >= thresholdPx) {
							gestureState.accumDown = 0f
							topOverscrollPx = 0f
							gestureState.shadeArmed = false
							expandNotificationShade(context)
						}
						return Offset(0f, dy)
					}
					gestureState.accumDown = 0f
					topOverscrollPx = 0f
					return Offset.Zero
				}

				if (dy < 0) {
					gestureState.accumDown = 0f
					topOverscrollPx = 0f
					if (!listState.canScrollForward && gestureState.drawerArmed) {
						gestureState.accumUp += -dy
						overscrollPx = gestureState.accumUp
						if (gestureState.accumUp >= thresholdPx) {
							gestureState.accumUp = 0f
							overscrollPx = 0f
							gestureState.drawerArmed = false
							openApps()
						}
						return Offset(0f, dy)
					}
					gestureState.accumUp = 0f
					overscrollPx = 0f
					return Offset.Zero
				}

				return Offset.Zero
			}

			override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
				gestureState.accumUp = 0f
				gestureState.accumDown = 0f
				overscrollPx = 0f
				topOverscrollPx = 0f
				return Velocity.Zero
			}
		}
	}

	Box(
		modifier = Modifier
		.fillMaxSize()
		.background(config.colors.globalBg)
		.then(radialGestureModifier)
	) {
		FutureScreenBackground(modifier = Modifier.fillMaxSize())

		CompositionLocalProvider(LocalOverscrollFactory provides null) {
			Box(
				modifier = Modifier
				.fillMaxSize()
				.blur(blurRadius)
			) {
				LazyColumn(
					state = listState,
					overscrollEffect = null,
					modifier = Modifier
					.fillMaxSize()
					.then(gestureArmModifier)
					.nestedScroll(overscrollConnection)
					.windowInsetsPadding(WindowInsets.statusBars),
				) {
					item(key = "header") {
						Header(clock, isoWeekOfYear(clock), onOpenSettings)
					}
					item(key = "calendar") {
						CalendarCard(yyyy, mm, dd, taskDays) { showTasksEditor = true }
					}
					item(key = "tasks") {
						AllTasksCard(
							todayItems = todayTasks.map { it.id to it.name },
							weekGroups = weekGroups,
							monthGroups = monthGroups,
							onTodayClick = { showTasksEditor = true },
							onWeeklyClick = { showWeeklyEditor = true },
							onMonthlyClick = { showMonthlyEditor = true }
						)
					}

					weather?.let { wf ->
						if (wf.valid) {
							item(key = "weather") { WeatherCard(wf) }
						}
					}

					item(key = "phrase") {
						FutureCard(
							modifier = Modifier.fillMaxWidth(),
							padding = PaddingValues(CARDS_H_PADDING * 2, CARDS_V_PADDING * 2),
							componentBg = false,
							border = false,
							onClick = {
								val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
								clipboard.setPrimaryClip(ClipData.newPlainText("Phrase of the day", phrase))
							}
						) {
							FutureText(
								phrase,
								style = FutureTheme.typography.bodyLarge,
								textAlign = TextAlign.Center,
								modifier = Modifier.fillMaxWidth()
							)
						}
					}
				}
			}
		}

				RadialMenuOverlay(
			items = radialItems,
			selectedIndex = radialSelectedIndex,
			fingerPos = radialFingerPos,
			buttonProgress = buttonProgress,
			iconBounds = radialIconBounds,
			editing = radialEditing,
			onRemove = { index ->
				val pkg = radialApps.getOrNull(index) ?: return@RadialMenuOverlay
				val newList = radialApps.toMutableList().also { it.removeAt(index) }
				radialApps = newList
				scope.launch {
					withContext(Dispatchers.IO) { radialPreferences.save(newList) }
				}
			},
			onSwap = { from, to ->
				if (from == to) return@RadialMenuOverlay
				if (from !in radialApps.indices || to !in radialApps.indices) return@RadialMenuOverlay
				val newList = radialApps.toMutableList()
				val tmp = newList[from]
				newList[from] = newList[to]
				newList[to] = tmp
				radialApps = newList
				scope.launch {
					withContext(Dispatchers.IO) { radialPreferences.save(newList) }
				}
			},
			onAddClick = { showRadialAdd = true },
			onExitEdit = {
				radialEditing = false
				radialOpen = false
			},
			modifier = Modifier.fillMaxSize()
		)

		if (showTasksEditor) {
			TasksEditorDialog(
				tasks = tasks,
				onSave = { newTasks ->
					scope.launch {
						withContext(Dispatchers.IO) { saveTasks(newTasks) }
						tasks = newTasks
						showTasksEditor = false
					}
				}
			)
		}

		if (showWeeklyEditor) {
			WeeklyTasksEditorDialog(
				tasks = weekTasks,
				onSave = { newTasks ->
					scope.launch {
						withContext(Dispatchers.IO) { saveWeekTasks(newTasks) }
						weekTasks = newTasks
						showWeeklyEditor = false
					}
				}
			)
		}

		if (showMonthlyEditor) {
			MonthlyTasksEditorDialog(
				tasks = monthTasks,
				onSave = { newTasks ->
					scope.launch {
						withContext(Dispatchers.IO) { saveMonthTasks(newTasks) }
						monthTasks = newTasks
						showMonthlyEditor = false
					}
				}
			)
		}

		if (showRadialAdd) {
			RadialAddDialog(
				alreadySelected = radialApps,
				onPick = { pkg ->
					if (radialApps.size < 8 && pkg !in radialApps) {
						val newList = radialApps + pkg
						radialApps = newList
						scope.launch {
							withContext(Dispatchers.IO) { radialPreferences.save(newList) }
						}
					}
					showRadialAdd = false
				},
				onDismiss = { showRadialAdd = false }
			)
		}
	}
}

@Composable
private fun Header(cal: Calendar, week: Int, onOpenSettings: () -> Unit) {
	val hh = cal.get(Calendar.HOUR_OF_DAY)
	val mi = cal.get(Calendar.MINUTE)
	val dd = cal.get(Calendar.DAY_OF_MONTH)
	val mm = cal.get(Calendar.MONTH) + 1
	val yyyy = cal.get(Calendar.YEAR)

	Row(
		modifier = Modifier.fillMaxWidth().padding(12.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Spacer(Modifier.height(8.dp))
		FutureTextTitle(
			"%02d:%02d".format(Locale.US, hh, mi),
			size = 34.sp,
			modifier = Modifier.weight(1f)
		)
		Column(
			horizontalAlignment = Alignment.End,
			modifier = Modifier.padding(end = 12.dp)
		) {
			FutureTextHeading("%02d.%02d.%04d".format(Locale.US, dd, mm, yyyy), size = 24.sp)
			FutureTextHeading("Week %d".format(week), size = 24.sp)
		}
		//FutureButton(onClick = onOpenSettings, icon = { FutureIconSettings() })
	}
}

@Composable
private fun CalendarCard(
	yyyy: Int, mm: Int, dd: Int,
	taskDays: Set<Int>,
	onClick: () -> Unit
) {
	val config = FutureTheme.config

	FutureCard(
		Modifier.fillMaxWidth(),
		padding = PaddingValues(CARDS_H_PADDING, CARDS_V_PADDING),
		componentBg = false,
		border = false,
		onClick = onClick
	) {
		FutureTextHeading("%s %d".format(Locale.US, MONTH_NAMES[mm - 1], yyyy))
		Spacer(Modifier.height(12.dp))

		Row(Modifier.fillMaxWidth()) {
			listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach { d ->
				Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
					FutureText(d, style = FutureTheme.typography.labelSmall)
				}
			}
		}
		Spacer(Modifier.height(4.dp))

		val fw = firstWeekdayMonday0(yyyy, mm)
		val dim = daysInMonth(yyyy, mm)
		var day = 1 - fw
		while (day <= dim) {
			Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
				for (c in 0..6) {
					val d = day + c
					Box(
						modifier = Modifier.weight(1f).height(32.dp),
						contentAlignment = Alignment.Center
					) {
						if (d in 1..dim) {
							val isToday = d == dd
							val textColor = when {
								isToday -> config.colors.globalBg
								d in taskDays -> config.colors.error
								else -> config.colors.textPrimary
							}
							val cellMod = if (isToday)
							Modifier.background(config.colors.primary, RoundedCornerShape(8.dp))
							else
							Modifier
							Box(cellMod.padding(horizontal = 6.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
								FutureText(d.toString(), color = textColor)
							}
						}
					}
				}
			}
			day += 7
		}
	}
}

@Composable
private fun AllTasksCard(
	todayItems: List<Pair<Int, String>>,
	weekGroups: Map<String, List<Pair<Int, String>>>,
	monthGroups: Map<String, List<Pair<Int, String>>>,
	onTodayClick: () -> Unit,
	onWeeklyClick: () -> Unit,
	onMonthlyClick: () -> Unit
) {
	FutureCard(
		Modifier.fillMaxWidth(),
		padding = PaddingValues(CARDS_H_PADDING, CARDS_V_PADDING),
		componentBg = false,
		border = false,
	) {
		FlatTaskSection(
			title = "Todays Tasks:",
			items = todayItems,
			emptyText = "All done!",
			emptyColor = FutureTheme.config.colors.success,
			onClick = onTodayClick
		)
		SectionDivider()
		GroupedTaskSection(
			title = "Weekly Tasks",
			groups = weekGroups,
			emptyText = "No weekly tasks.",
			onClick = onWeeklyClick
		)
		SectionDivider()
		GroupedTaskSection(
			title = "Monthly Tasks",
			groups = monthGroups,
			emptyText = "No monthly tasks.",
			onClick = onMonthlyClick
		)
	}
}

@Composable
private fun SectionDivider() {
	Spacer(Modifier.height(12.dp))
	FutureSeparator(Modifier.fillMaxWidth())
	Spacer(Modifier.height(12.dp))
}

@Composable
private fun FlatTaskSection(
	title: String,
	items: List<Pair<Int, String>>,
	emptyText: String,
	emptyColor: Color,
	onClick: () -> Unit
) {
	ClickableSection(onClick = onClick) {
		FutureTextHeading(title)
		Spacer(Modifier.height(8.dp))
		if (items.isEmpty()) {
			FutureText(emptyText, color = emptyColor)
		} else {
			items.forEach { (id, name) ->
				FutureText("   %d. %s".format(id, name))
			}
		}
	}
}

@Composable
private fun GroupedTaskSection(
	title: String,
	groups: Map<String, List<Pair<Int, String>>>,
	emptyText: String,
	onClick: () -> Unit
) {
	ClickableSection(onClick = onClick) {
		FutureTextHeading(title)
		Spacer(Modifier.height(8.dp))
		if (groups.isEmpty()) {
			FutureText(emptyText)
		} else {
			var first = true
			groups.forEach { (label, groupItems) ->
				if (!first) Spacer(Modifier.height(10.dp))
				first = false
				FutureText(
					"%s:".format(label),
					variant = TextVariant.Highlighted,
					style = FutureTheme.typography.labelLarge
				)
				Spacer(Modifier.height(4.dp))
				groupItems.forEach { (id, name) ->
					FutureText("   %d. %s".format(id, name))
				}
			}
		}
	}
}

@Composable
private fun ClickableSection(
	onClick: () -> Unit,
	content: @Composable () -> Unit
) {
	val config = FutureTheme.config
	val interactionSource = remember { MutableInteractionSource() }
	val isHovered by interactionSource.collectIsHoveredAsState()

	Column(
		modifier = Modifier
		.fillMaxWidth()
		.clip(config.shapes.input)
		.clickable(
			interactionSource = interactionSource,
			indication = null,
			onClick = onClick
		)
		.background(config.colors.primary.copy(alpha = if (isHovered) 0.06f else 0f))
		.padding(horizontal = 4.dp, vertical = 4.dp)
	) {
		content()
	}
}

private fun windColor(ms: Int): Color? = when {
	ms <= 0 -> null
	ms <= 3 -> Color(0xFF1B5E20)
	ms <= 8 -> Color(0xFFF9A825)
	ms <= 15 -> Color(0xFFB71C1C)
	else -> Color(0xFF6A1B9A)
}

private fun rainColor(mm: Float): Color? = when {
	mm <= 0f -> null
	mm <= 2.5f -> Color(0xFF1B5E20)
	mm <= 10f -> Color(0xFFF9A825)
	mm <= 40f -> Color(0xFFB71C1C)
	else -> Color(0xFF6A1B9A)
}

@Composable
private fun SeverityChip(text: String, color: Color?) {
	if (color != null) {
		Box(
			modifier = Modifier
			.background(color.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
			.padding(horizontal = 6.dp, vertical = 2.dp),
			contentAlignment = Alignment.Center
		) {
			FutureText(text, style = FutureTheme.typography.labelMedium, color = Color.White)
		}
	} else {
		FutureText(text, style = FutureTheme.typography.labelMedium)
	}
}

@Composable
private fun WeatherCard(wf: WxForecast) {
	FutureCard(
		Modifier.fillMaxWidth(),
		padding = PaddingValues(CARDS_H_PADDING, CARDS_V_PADDING),
		componentBg = false,
		border = false,
	) {
		wf.days.forEachIndexed { index, day ->
			if (index > 0) {
				FutureSeparator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
			}
			WeatherDayRow(day)
		}
	}
}

@Composable
private fun WeatherDayRow(day: WxDay) {
	val dateStr = try {
		val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day.date)
		SimpleDateFormat("dd.MM.", Locale.US).format(d!!)
	} catch (e: Exception) { day.date }

	Column(Modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween
		) {
			FutureTextHeading(dateStr)
			FutureText(day.weekday.uppercase(Locale.US))
		}
		Spacer(Modifier.height(8.dp))
		Row(Modifier.fillMaxWidth()) {
			for (i in 0 until Weather.SLOTS) {
				val slot = day.slots.getOrNull(i) ?: WxSlot()
				Column(
					modifier = Modifier.weight(1f),
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(4.dp)
				) {
					if (slot.valid) {
						FutureText(
							Weather.symbolEmoji(slot.symbol),
							style = FutureTheme.typography.headlineMedium.copy(fontSize = 22.sp)
						)
						FutureText("%d°C".format(slot.tempC))
						SeverityChip("💨%d".format(slot.windMs), windColor(slot.windMs))
						SeverityChip("💧%.1f".format(Locale.US, slot.precipMm), rainColor(slot.precipMm))
					} else {
						FutureText("—")
					}
				}
			}
		}
	}
}

internal fun expandNotificationShade(context: android.content.Context) {
	try {
		val service = context.getSystemService(android.content.Context.STATUS_BAR_SERVICE)
		val clazz = Class.forName("android.app.StatusBarManager")
		val method = clazz.getMethod("expandNotificationsPanel")
		method.invoke(service)
	} catch (e: Throwable) {
		android.util.Log.e("Dashboard", "expandNotificationsPanel failed", e)
	}
}

@Composable
private fun RadialTriggerCard(
	hasApps: Boolean,
	appCount: Int,
	onTrigger: () -> Unit
) {
	val config = FutureTheme.config
	FutureCard(
		modifier = Modifier
		.fillMaxWidth()
		.pointerInput(Unit) {
			detectTapGestures(
				onLongPress = { onTrigger() }
			)
		}
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(16.dp)
		) {
			Box(
				modifier = Modifier
				.size(44.dp)
				.clip(androidx.compose.foundation.shape.CircleShape)
				.background(config.colors.componentBg)
				.border(2.dp, config.colors.primary, androidx.compose.foundation.shape.CircleShape),
				contentAlignment = Alignment.Center
			) {
				androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
					val r = 2.dp.toPx()
					val s = size.width / 3f
					for (row in 0..2) for (col in 0..2) {
						drawCircle(
							color = config.colors.primary,
							radius = r,
							center = Offset(s * (col + 0.5f), s * (row + 0.5f))
						)
					}
				}
			}
			Column(modifier = Modifier.weight(1f)) {
				FutureTextHeading("Quick Launch")
				FutureTextMuted(
					if (hasApps) "$appCount app${if (appCount == 1) "" else "s"} · long-press to open"
					else "Long-press to choose apps"
				)
			}
		}
	}
}

@Composable
private fun RadialAppIcon(packageName: String, modifier: Modifier) {
	val context = LocalContext.current
	val bitmap by produceState<ImageBitmap?>(initialValue = null, packageName) {
		value = withContext(Dispatchers.IO) {
			runCatching {
				val pm = context.packageManager
				val drawable: Drawable = pm.getApplicationIcon(packageName)
				drawable.toBitmap(128, 128).asImageBitmap()
			}.getOrNull()
		}
	}
	val bmp = bitmap
	if (bmp != null) {
		Image(bitmap = bmp, contentDescription = null, modifier = modifier)
	} else {
		Box(modifier, contentAlignment = Alignment.Center) {
			FutureText(
				packageName.substringAfterLast('.').take(1).uppercase(),
				variant = TextVariant.Glow
			)
		}
	}
}

private fun launchPackage(context: android.content.Context, packageName: String) {
	val intent = context.packageManager.getLaunchIntentForPackage(packageName)
	if (intent != null) {
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		runCatching { context.startActivity(intent) }
	}
}
