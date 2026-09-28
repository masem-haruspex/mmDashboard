package com.mlib.dashboard.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mlib.future.FutureTheme
import com.mlib.future.components.*

private data class AppChoice(val packageName: String, val label: String)

@Composable
fun RadialAddDialog(
	alreadySelected: List<String>,
	onPick: (String) -> Unit,
	onDismiss: () -> Unit
) {
	val context = LocalContext.current
	val pm = context.packageManager

	val available = remember(alreadySelected) {
		val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
		pm.queryIntentActivities(intent, 0)
		.map {
			AppChoice(
				packageName = it.activityInfo.packageName,
				label = it.loadLabel(pm).toString()
			)
		}
		.filter {
			it.packageName != context.packageName &&
			it.packageName !in alreadySelected
		}
		.distinctBy { it.packageName }
		.sortedBy { it.label.lowercase() }
	}

	FutureDialog(title = "Add App", onDismissRequest = onDismiss) {
		Column(modifier = Modifier.fillMaxWidth()) {
			if (available.isEmpty()) {
				FutureTextMuted("No more apps available.")
			} else {
				LazyColumn(
					modifier = Modifier
					.fillMaxWidth()
					.heightIn(max = 380.dp)
				) {
					items(available, key = { it.packageName }) { app ->
						Row(
							modifier = Modifier
							.fillMaxWidth()
							.clickable { onPick(app.packageName) }
							.padding(vertical = 8.dp),
							verticalAlignment = Alignment.CenterVertically
						) {
							FutureText(app.label, maxLines = 1)
						}
						FutureSeparator(Modifier.fillMaxWidth(), thickness = 0.3.dp)
					}
				}
			}
			Spacer(Modifier.height(12.dp))
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.End
			) {
				FutureButton("Cancal", onClick = onDismiss)
			}
		}
	}
}
