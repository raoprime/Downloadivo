package com.raoprime.downloadivo.data

import android.content.Context
import com.raoprime.downloadivo.model.DownloadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object DownloadRepository {

    private val _history = MutableStateFlow<List<DownloadTask>>(emptyList())
    val history = _history.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private var historyFile: File? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        if (historyFile == null) {
            historyFile = File(context.filesDir, "download_history.json")
            loadHistory()
        }
    }

    private fun loadHistory() {
        scope.launch {
            try {
                historyFile?.let { file ->
                    if (file.exists()) {
                        val content = file.readText()
                        if (content.isNotBlank()) {
                            val items = json.decodeFromString<List<DownloadTask>>(content)
                            _history.value = items.sortedByDescending { it.timestamp }
                        }
                    }
                }
            } catch (e: Exception) {
                _history.value = emptyList()
            }
        }
    }

    fun addTask(task: DownloadTask) {
        scope.launch {
            val current = _history.value.toMutableList()
            current.removeAll { it.id == task.id }
            current.add(0, task)
            _history.value = current
            saveHistory()
        }
    }

    fun updateTask(task: DownloadTask) {
        scope.launch {
            val current = _history.value.toMutableList()
            val index = current.indexOfFirst { it.id == task.id }
            if (index != -1) {
                current[index] = task
            } else {
                current.add(0, task)
            }
            _history.value = current
            // High-Performance Guard: Do NOT write JSON to flash disk on every 400ms progress tick!
            // Only persist to disk when terminal state is reached (SUCCESS, ERROR, CANCELLED)
            if (task.status != com.raoprime.downloadivo.model.DownloadStatus.DOWNLOADING) {
                saveHistory()
            }
        }
    }

    fun removeTask(taskId: String) {
        scope.launch {
            val current = _history.value.toMutableList()
            current.removeAll { it.id == taskId }
            _history.value = current
            saveHistory()
        }
    }

    fun clearHistory() {
        scope.launch {
            _history.value = emptyList()
            saveHistory()
        }
    }

    private fun saveHistory() {
        try {
            historyFile?.let { file ->
                val str = json.encodeToString(_history.value.take(100))
                file.writeText(str)
            }
        } catch (_: Exception) {}
    }
}
