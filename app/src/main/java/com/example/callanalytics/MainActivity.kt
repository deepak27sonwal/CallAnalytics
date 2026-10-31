package com.example.callanalytics

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CallItem(
    val number: String,
    val name: String,
    val type: Int,
    val date: Long,
    val duration: Long
)

class MainActivity : ComponentActivity() {
    private var pendingExport: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF087F70),
                    secondary = Color(0xFFD6A743),
                    background = Color(0xFFF5F7F7)
                )
            ) {
                CallAnalyticsScreen()
            }
        }
    }

    @Composable
    private fun CallAnalyticsScreen() {
        var hasPermission by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED)
        }
        var calls by remember { mutableStateOf(emptyList<CallItem>()) }
        var selectedRange by remember { mutableStateOf("30 days") }
        var search by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasPermission = granted
            message = if (granted) "Permission granted" else
                "Call-log permission was not granted. You can enable it in Android Settings if available."
            if (granted) calls = loadCalls()
        }

        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/csv")
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingExport) }
                    message = "CSV report saved successfully"
                } catch (e: Exception) {
                    message = "Could not save report: ${e.localizedMessage}"
                }
            }
        }

        LaunchedEffect(hasPermission) {
            if (hasPermission) calls = loadCalls()
        }

        val cutoff = when (selectedRange) {
            "Today" -> System.currentTimeMillis() - 24L * 60 * 60 * 1000
            "7 days" -> System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            "30 days" -> System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            else -> 0L
        }
        val filtered = calls.filter {
            it.date >= cutoff &&
                (it.number.contains(search, true) || it.name.contains(search, true))
        }
        val incoming = filtered.count { it.type == CallLog.Calls.INCOMING_TYPE }
        val outgoing = filtered.count { it.type == CallLog.Calls.OUTGOING_TYPE }
        val missed = filtered.count {
            it.type == CallLog.Calls.MISSED_TYPE || it.type == CallLog.Calls.REJECTED_TYPE
        }
        val totalDuration = filtered.sumOf { it.duration }
        val topContacts = filtered.groupBy { if (it.name.isBlank()) it.number else it.name }
            .entries.sortedByDescending { it.value.size }.take(5)

        Scaffold(
            topBar = {
                Surface(color = Color(0xFF075F55), shadowElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("CALL ANALYTICS", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Private call reports on this device", color = Color(0xFFD4E9E5), fontSize = 12.sp)
                    }
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().background(Color(0xFFF5F7F7))
                    .padding(padding).padding(16.dp)
            ) {
                if (!hasPermission) {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Allow call history access", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("The app needs Android's READ_CALL_LOG permission to calculate reports. Your records remain on this phone.")
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_CALL_LOG) }) {
                                Text("Grant permission")
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Today", "7 days", "30 days", "All").forEach { range ->
                            FilterChip(
                                selected = selectedRange == range,
                                onClick = { selectedRange = range },
                                label = { Text(range, fontSize = 11.sp) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        StatCard("Incoming", incoming.toString(), Color(0xFF168A70), Modifier.weight(1f))
                        StatCard("Outgoing", outgoing.toString(), Color(0xFF347DB7), Modifier.weight(1f))
                        StatCard("Missed", missed.toString(), Color(0xFFC28A22), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Total talk time", color = Color.Gray, fontSize = 13.sp)
                            Text(formatDuration(totalDuration), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Color(0xFF075F55))
                            Text("${filtered.size} calls in selected period", color = Color.Gray, fontSize = 12.sp)
                            Spacer(Modifier.height(12.dp))
                            val total = (incoming + outgoing + missed).coerceAtLeast(1)
                            Row(Modifier.fillMaxWidth().height(10.dp).background(Color.LightGray, RoundedCornerShape(8.dp))) {
                                Box(Modifier.weight(incoming.toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(Color(0xFF168A70)))
                                Box(Modifier.weight(outgoing.toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(Color(0xFF347DB7)))
                                Box(Modifier.weight(missed.toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(Color(0xFFC28A22)))
                            }
                            Text("Incoming $incoming  •  Outgoing $outgoing  •  Missed/rejected $missed",
                                fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Top contacts", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    if (topContacts.isEmpty()) Text("No calls found for this period.", color = Color.Gray)
                    topContacts.forEachIndexed { index, entry ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}.", color = Color.Gray, modifier = Modifier.width(26.dp))
                            Text(entry.key, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text("${entry.value.size} calls", color = Color(0xFF087F70), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = search, onValueChange = { search = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search number or contact") },
                        singleLine = true
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent calls", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            pendingExport = buildCsv(filtered)
                            exportLauncher.launch("call-report-${System.currentTimeMillis()}.csv")
                        }) { Text("Export CSV") }
                    }
                    LazyColumn(Modifier.weight(1f)) {
                        items(filtered.take(300)) { call ->
                            CallRow(call)
                            HorizontalDivider(color = Color(0xFFE7ECEB))
                        }
                    }
                    Text("Showing up to 300 recent records. Reports are processed locally.",
                        color = Color.Gray, fontSize = 10.sp)
                }
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(message, color = Color(0xFF087F70), fontSize = 12.sp)
                }
            }
        }
    }

    private fun loadCalls(): List<CallItem> {
        val result = mutableListOf<CallItem>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION
        )
        contentResolver.query(
            CallLog.Calls.CONTENT_URI, projection, null, null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val numberCol = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val nameCol = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val typeCol = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationCol = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (cursor.moveToNext()) {
                result.add(CallItem(
                    cursor.getString(numberCol) ?: "Unknown",
                    cursor.getString(nameCol) ?: "",
                    cursor.getInt(typeCol),
                    cursor.getLong(dateCol),
                    cursor.getLong(durationCol)
                ))
            }
        }
        return result
    }

    private fun buildCsv(items: List<CallItem>): String {
        val out = StringBuilder("Name,Number,Type,Date,Time,Duration_seconds\n")
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        items.forEach {
            val type = when (it.type) {
                CallLog.Calls.INCOMING_TYPE -> "Incoming"
                CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                CallLog.Calls.MISSED_TYPE -> "Missed"
                CallLog.Calls.REJECTED_TYPE -> "Rejected"
                CallLog.Calls.BLOCKED_TYPE -> "Blocked"
                else -> "Other"
            }
            fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
            out.append("${q(it.name)},${q(it.number)},$type,")
                .append("${dateFmt.format(Date(it.date))},${timeFmt.format(Date(it.date))},${it.duration}\n")
        }
        return out.toString()
    }
}

@Composable
private fun StatCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 11.sp, color = Color.Gray)
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun CallRow(call: CallItem) {
    val (label, color) = when (call.type) {
        CallLog.Calls.INCOMING_TYPE -> "Incoming" to Color(0xFF168A70)
        CallLog.Calls.OUTGOING_TYPE -> "Outgoing" to Color(0xFF347DB7)
        CallLog.Calls.MISSED_TYPE -> "Missed" to Color(0xFFC28A22)
        CallLog.Calls.REJECTED_TYPE -> "Rejected" to Color(0xFFC05A50)
        CallLog.Calls.BLOCKED_TYPE -> "Blocked" to Color.Gray
        else -> "Other" to Color.Gray
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (call.name.isBlank()) call.number else call.name,
                Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(label, color = color, fontSize = 11.sp)
        }
        Text(call.number, color = Color.Gray, fontSize = 12.sp)
        Text("${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(call.date))}  •  ${formatDuration(call.duration)}",
            color = Color.Gray, fontSize = 10.sp)
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "${hours}h ${minutes}m"
    else if (minutes > 0) "${minutes}m ${secs}s"
    else "${secs}s"
}
