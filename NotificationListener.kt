package com.mlib.dashboard

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotificationListener : NotificationListenerService() {

	companion object {

		private val _notifications = MutableStateFlow<List<StatusBarNotification>>(emptyList())
		val notifications: StateFlow<List<StatusBarNotification>> = _notifications.asStateFlow()

		private val _connected = MutableStateFlow(false)
		val connected: StateFlow<Boolean> = _connected.asStateFlow()

		private var instance: NotificationListener? = null

		fun cancel(key: String) {
			val i = instance
			if (i == null) {
				return
			}
			try {
				i.cancelNotification(key)
			} catch (e: Throwable) {
			}
		}
	}

	override fun onListenerConnected() {
		super.onListenerConnected()
		instance = this
		_connected.value = true
		refresh()
	}

	override fun onListenerDisconnected() {
		super.onListenerDisconnected()
		instance = null
		_connected.value = false
		_notifications.value = emptyList()
	}

	override fun onNotificationPosted(sbn: StatusBarNotification) {
		refresh()
	}

	override fun onNotificationRemoved(sbn: StatusBarNotification) {
		refresh()
	}

	private fun refresh() {
		val list = activeNotifications?.toList() ?: emptyList()
		_notifications.value = list
	}
}
