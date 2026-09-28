package com.mlib.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class WxSlot(
	val tempC: Int? = null,        
	val windMs: Int = 0,
	val precipMm: Float = 0f,
	val symbol: String = "",
	val valid: Boolean = false
)

data class WxDay(val date: String, val weekday: String, val slots: List<WxSlot>)

data class WxForecast(
	val fetchedDate: String = "",
	val fetchedTime: String = "",
	val days: List<WxDay> = emptyList(),
	val nowTemp: Int? = null,
	val nowWind: Int = 0,
	val nowPrecip: Float = 0f,
	val nowSymbol: String = "",
	val valid: Boolean = false
)

object Weather {
	const val DAYS = 5
	const val SLOTS = 4
	const val LAT = "41.48"
	const val LON = "22.09"

	private const val UA = "mlib-dashboard/1.0"

	private const val SLOT_MORNING = 0
	private const val SLOT_NOON = 1
	private const val SLOT_EVENING = 2
	private const val SLOT_NIGHT = 3

	private val utcFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
		timeZone = TimeZone.getTimeZone("UTC")
	}
	private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
	private val wdayFmt = SimpleDateFormat("EEE", Locale.US)

	private fun cacheFile() = File(databaseDir, "weather.txt")
	private fun tempFile() = File(databaseDir, "weather_temp.txt")

	suspend fun get(): WxForecast = withContext(Dispatchers.IO) {
		val today = dayFmt.format(Date())
		if (cacheFresh(today)) parse()?.let { return@withContext it }
		if (fetch(today)) parse()?.let { return@withContext it }
		parse() ?: WxForecast(valid = false)
	}

	private fun cacheFresh(today: String): Boolean {
		val f = cacheFile()
		if (!f.isFile) return false
		val first = f.bufferedReader().use { it.readLine() } ?: return false
		return first.startsWith(today)
	}

	private fun parse(): WxForecast? {
		val f = cacheFile()
		if (!f.isFile) return null
		val lines = f.readLines()
		if (lines.isEmpty()) return null

		val head = lines[0].split(" ")
		val fetchedDate = head.getOrNull(0) ?: return null
		val fetchedTime = head.getOrNull(1) ?: ""

		var nowTemp: Int? = null
		var nowWind = 0
		var nowPrecip = 0f
		var nowSymbol = ""

		val weekdays = LinkedHashMap<String, String>()
		val slots = LinkedHashMap<String, MutableList<WxSlot?>>()

		for (line in lines.drop(1)) {
			if (line.startsWith("#now|")) {
				val p = line.removePrefix("#now|").split("|")
				nowTemp = p.getOrNull(0)?.toIntOrNull()
				nowWind = p.getOrNull(1)?.toIntOrNull() ?: 0
				nowPrecip = p.getOrNull(2)?.toFloatOrNull() ?: 0f
				nowSymbol = p.getOrNull(3) ?: ""
				continue
			}
			val p = line.split("|")
			if (p.size < 7) continue
			val date = p[0]
			weekdays[date] = p[1]
			val slotIdx = p[2].toIntOrNull() ?: continue
			if (slotIdx !in 0 until SLOTS) continue
			val rawTemp = p[3].toIntOrNull()
			val rawWind = p[4].toIntOrNull()
			val temp = if (rawTemp == null || rawTemp == -999) null else rawTemp
			val wind = if (rawWind == null || rawWind == -999) 0 else rawWind
			val slot = WxSlot(
				tempC = temp,
				windMs = wind,
				precipMm = p[5].toFloatOrNull() ?: 0f,
				symbol = p[6],
				valid = temp != null
			)
			val arr = slots.getOrPut(date) { MutableList<WxSlot?>(SLOTS) { null } }
			arr[slotIdx] = slot
		}
		if (slots.isEmpty()) return null

		val dayList = slots.entries.take(DAYS).map { (date, arr) ->
			WxDay(date, weekdays[date] ?: "", arr.map { it ?: WxSlot() })
		}
		return WxForecast(fetchedDate, fetchedTime, dayList, nowTemp, nowWind, nowPrecip, nowSymbol, valid = true)
	}

	private fun fetch(today: String): Boolean {
		val url = URL("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=$LAT&lon=$LON")
		val conn = (url.openConnection() as HttpURLConnection).apply {
			requestMethod = "GET"
			setRequestProperty("User-Agent", UA)
			connectTimeout = 15000
			readTimeout = 15000
		}
		val body = try {
			conn.inputStream.bufferedReader().use { it.readText() }
		} catch (e: Exception) {
			return false
		} finally {
			conn.disconnect()
		}

		val timeseries = try {
			JSONObject(body).getJSONObject("properties").getJSONArray("timeseries")
		} catch (e: Exception) {
			return false
		}

		val cal = Calendar.getInstance()
		val y = cal.get(Calendar.YEAR)
		val m = cal.get(Calendar.MONTH)
		val d = cal.get(Calendar.DAY_OF_MONTH)

		fun localTs(dayOff: Int, hour: Int, min: Int): Long {
			val c = Calendar.getInstance()
			c.set(y, m, d, hour, min, 0)
			c.set(Calendar.MILLISECOND, 0)
			c.add(Calendar.DAY_OF_YEAR, dayOff)
			return c.timeInMillis
		}

		val centers = Array(DAYS) { di ->
			longArrayOf(
				localTs(di, 8, 0),
				localTs(di, 12, 0),
				localTs(di, 18, 0),
				localTs(di + 1, 0, 0)
			)
		}
		val wstarts = Array(DAYS) { di ->
			longArrayOf(
				localTs(di, 6, 0),
				localTs(di, 12, 0),
				localTs(di, 18, 0),
				localTs(di + 1, 0, 0)
			)
		}

		val tempSum = Array(DAYS) { DoubleArray(SLOTS) }
		val windSum = Array(DAYS) { DoubleArray(SLOTS) }
		val counts = Array(DAYS) { IntArray(SLOTS) }
		val nearDiff = Array(DAYS) { LongArray(SLOTS) { Long.MAX_VALUE } }
		val nearTemp = Array(DAYS) { DoubleArray(SLOTS) }
		val nearWind = Array(DAYS) { DoubleArray(SLOTS) }
		val bestDiff = Array(DAYS) { LongArray(SLOTS) { Long.MAX_VALUE } }
		val bestSym = Array(DAYS) { Array(SLOTS) { "" } }
		val bestPrc = Array(DAYS) { FloatArray(SLOTS) }

		var nowTemp: Int? = null
		var nowWind = 0
		var nowPrecip = 0f
		var nowSymbol = ""

		for (i in 0 until timeseries.length()) {
			val entry = timeseries.getJSONObject(i)
			val timeStr = entry.getString("time")
			val t = try { utcFmt.parse(timeStr)?.time } catch (e: Exception) { null } ?: continue

			val data = entry.getJSONObject("data")
			val details = data.getJSONObject("instant").getJSONObject("details")
			val hasTW = details.has("air_temperature") && details.has("wind_speed")
			val temp = if (hasTW) details.getDouble("air_temperature") else 0.0
			val wind = if (hasTW) details.getDouble("wind_speed") else 0.0

			var symbol = "unknown"
			var precip = 0.0
			for (key in listOf("next_6_hours", "next_1_hours")) {
				if (data.has(key)) {
					val nd = data.getJSONObject(key)
					symbol = nd.getJSONObject("summary").getString("symbol_code")
					val det = nd.optJSONObject("details")
					if (det != null && det.has("precipitation_amount"))
					precip = det.getDouble("precipitation_amount")
					break
				}
			}
			if (symbol == "unknown") continue

			if (nowTemp == null && hasTW) {
				nowTemp = roundInt(temp)
				nowWind = roundInt(wind)
				nowPrecip = precip.toFloat()
				nowSymbol = symbol
			}

			if (hasTW) {
				for (di in 0 until DAYS) for (si in 0 until SLOTS) {
					val diff = Math.abs(t - centers[di][si])
					if (diff <= 3600_000L) {
						tempSum[di][si] += temp
						windSum[di][si] += wind
						counts[di][si]++
					}
					if (diff < nearDiff[di][si]) {
						nearDiff[di][si] = diff
						nearTemp[di][si] = temp
						nearWind[di][si] = wind
					}
				}
			}

			for (di in 0 until DAYS) for (si in 0 until SLOTS) {
				val diff = Math.abs(t - wstarts[di][si])
				if (diff < bestDiff[di][si]) {
					bestDiff[di][si] = diff
					bestSym[di][si] = symbol
					bestPrc[di][si] = precip.toFloat()
				}
			}
		}

		val any = (0 until DAYS).any { di ->
			(0 until SLOTS).any { si -> counts[di][si] > 0 || nearDiff[di][si] <= 10_800_000L }
		}
		if (!any) return false

		val hhmm = String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
		val out = StringBuilder()
		out.append("$today $hhmm\n")
		if (nowTemp != null) out.append("#now|$nowTemp|$nowWind|$nowPrecip|$nowSymbol\n")

		for (di in 0 until DAYS) {
			val c = Calendar.getInstance().apply { timeInMillis = localTs(di, 12, 0) }
			val date = dayFmt.format(c.time)
			val wday = wdayFmt.format(c.time)
			for (si in 0 until SLOTS) {
				val temp: Int? = when {
					counts[di][si] > 0 -> roundInt(tempSum[di][si] / counts[di][si])
					nearDiff[di][si] <= 10_800_000L -> roundInt(nearTemp[di][si])
					else -> null
				}
				val wind: Int? = when {
					counts[di][si] > 0 -> roundInt(windSum[di][si] / counts[di][si])
					nearDiff[di][si] <= 10_800_000L -> roundInt(nearWind[di][si])
					else -> null
				}
				val sym = if (bestDiff[di][si] <= 14_400_000L) bestSym[di][si] else "missing"
				out.append(
					"$date|$wday|$si|${temp ?: -999}|${wind ?: -999}|${
						"%.1f".format(Locale.US, bestPrc[di][si])
					}|$sym\n"
				)
			}
		}

		tempFile().writeText(out.toString())
		if (!tempFile().renameTo(cacheFile())) {
			tempFile().delete()
			return false
		}
		return true
	}

	private fun roundInt(x: Double): Int =
	(if (x >= 0) x + 0.5 else x - 0.5).toInt()

	fun symbolEmoji(symbol: String): String {
		if (symbol.isEmpty() || symbol == "missing") return "—"
		val isNight = symbol.endsWith("_night")
		val base = symbol.substringBefore("_day").substringBefore("_night")
		return when {
			base == "clearsky" -> if (isNight) "🌙" else "🌞"
			base == "fair" -> if (isNight) "🌙" else "🌤️"
			base == "partlycloudy" -> "⛅"
			base == "cloudy" -> "☁️"
			base == "fog" -> "🌫️"
			"thunder" in base -> "⛈️"
			"sleet" in base || "snow" in base -> "🌨️"
			"rain" in base -> "🌧️"
			else -> "❓"
		}
	}
}
