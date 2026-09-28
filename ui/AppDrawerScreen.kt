package com.mlib.dashboard.ui

import androidx.compose.foundation.layout.imePadding
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlib.future.FutureTheme
import com.mlib.future.components.*
import com.mlib.future.modifiers.*
import kotlinx.coroutines.delay

private data class AppEntry(val label: String, val component: ComponentName)

@Composable
fun AppDrawerScreen(onClose: () -> Unit) {
	val context = LocalContext.current
	val config = FutureTheme.config
	val pm = context.packageManager
	var query by remember { mutableStateOf("") }
	val keyboardController = LocalSoftwareKeyboardController.current
	val focusRequester = remember { FocusRequester() }

	var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }

	LaunchedEffect(Unit) {
		apps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
			val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
			pm.queryIntentActivities(intent, 0)
			.map { ri ->
				AppEntry(
					label = ri.loadLabel(pm).toString(),
					component = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
				)
			}
			.filter { it.component.packageName != context.packageName }
			.sortedBy { it.label.lowercase() }
		}
	}

	LaunchedEffect(Unit) {
		delay(50)
		focusRequester.requestFocus()
		keyboardController?.show()
	}

	val filtered = remember(query, apps) {
		if (query.isBlank()) apps
		else apps.filter { it.label.contains(query, ignoreCase = true) }
	}

	fun launch(app: AppEntry) {
		keyboardController?.hide()
		runCatching {
			val i = Intent(Intent.ACTION_MAIN)
			.addCategory(Intent.CATEGORY_LAUNCHER)
			.setComponent(app.component)
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			context.startActivity(i)
		}
		onClose()
	}

	LaunchedEffect(query) {
		if (query.isNotBlank() && filtered.size == 1) {
			delay(150)
			launch(filtered[0])
		}
	}

	Box(modifier = Modifier.fillMaxSize().background(config.colors.globalBg).statusBarsPadding()) {
		FutureScreenBackground(modifier = Modifier.fillMaxSize())

		Column(modifier = Modifier.fillMaxSize().imePadding()) {

			LazyColumn(
				modifier = Modifier.weight(1f),
				contentPadding = PaddingValues(16.dp),
				verticalArrangement = Arrangement.spacedBy(4.dp)
			) {
				items(filtered, key = { it.component.flattenToString() }) { app ->
					AppRow(app, pm) { launch(app) }
				}
				if (filtered.isEmpty()) {
					item {
						Box(
							modifier = Modifier.fillMaxWidth().padding(32.dp),
							contentAlignment = Alignment.Center
						) {
						}
					}
				}
			}

			FutureSearch(
				placeholder = "Search apps...",
				componentBg = false,
				value = query,
				onValueChange = { query = it },
				modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
				bordered = false
			)
		}
	}
}

@Composable
private fun AppRow(app: AppEntry, pm: PackageManager, onClick: () -> Unit) {
	val config = FutureTheme.config

	Row(
		modifier = Modifier
		.fillMaxWidth()
		.clip(config.shapes.input)
		.clickable(onClick = onClick)
		.padding(horizontal = 8.dp, vertical = 8.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(16.dp)
	) {
		FutureTextHeading(
			app.label,
			size = 24.sp,
			modifier = Modifier.weight(1f)
		)

	}
}
