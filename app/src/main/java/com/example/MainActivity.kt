package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

// ==========================================
// Log Entry and Settings Data Structures
// ==========================================

sealed class LogEntry {
    abstract val text: String
    abstract val timestamp: Long

    data class Info(override val text: String, override val timestamp: Long = System.currentTimeMillis()) : LogEntry()
    data class Success(override val text: String, val path: String, val isDryRun: Boolean = false, override val timestamp: Long = System.currentTimeMillis()) : LogEntry()
    data class Error(override val text: String, override val timestamp: Long = System.currentTimeMillis()) : LogEntry()
    data class ScanProgress(override val text: String, override val timestamp: Long = System.currentTimeMillis()) : LogEntry()
}

enum class AppAccent(
    val displayName: String,
    val primary: Color,
    val container: Color,
    val text: Color,
    val border: Color
) {
    BLUE("Breeze Blue", Color(0xFF2563EB), Color(0xFFEFF6FF), Color(0xFF1E3A8A), Color(0xFFBFDBFE)),
    GREEN("Emerald Forest", Color(0xFF059669), Color(0xFFECFDF5), Color(0xFF064E3B), Color(0xFFA7F3D0)),
    AMBER("Warm Amber", Color(0xFFD97706), Color(0xFFFEF3C7), Color(0xFF78350F), Color(0xFFFDE68A)),
    PURPLE("Royal Purple", Color(0xFF8B5CF6), Color(0xFFF5F3FF), Color(0xFF4C1D95), Color(0xFFDDD6FE)),
    CRIMSON("Blaze Crimson", Color(0xFFE11D48), Color(0xFFFFF1F2), Color(0xFF881337), Color(0xFFFFC5C5)),
    TEAL("Ocean Teal", Color(0xFF0D9488), Color(0xFFF0FDFA), Color(0xFF115E59), Color(0xFF99F6E4)),
    ORANGE("Sunset Orange", Color(0xFFF97316), Color(0xFFFFF7ED), Color(0xFF9A3412), Color(0xFFFFDDB3)),
    INDIGO("Deep Indigo", Color(0xFF4F46E5), Color(0xFFE0E7FF), Color(0xFF3730A3), Color(0xFFC7D2FE)),
    PINK("Rose Pink", Color(0xFFDB2777), Color(0xFFFFF1F2), Color(0xFF9F1239), Color(0xFFFECDD3)),
    MIDNIGHT_MINT("Midnight Mint", Color(0xFF4F46E5), Color(0xFFECFDF5), Color(0xFF1E1B4B), Color(0xFFA7F3D0)),
    NEON_SYNTH("Neon Synth", Color(0xFF9333EA), Color(0xFFECFEFF), Color(0xFF581C87), Color(0xFF99F6E4)),
    CYBER_CITRUS("Cyber Citrus", Color(0xFFF43F5E), Color(0xFFFEFCE8), Color(0xFF881337), Color(0xFFFEF08A)),
    COSMIC_LAVENDER("Cosmic Lavender", Color(0xFF7C3AED), Color(0xFFF0F9FF), Color(0xFF4C1D95), Color(0xFFBAE6FD));

    companion object {
        fun fromName(name: String): AppAccent {
            return values().firstOrNull { it.displayName == name } ?: BLUE
        }
    }
}

data class CleanerSettings(
    val deleteHiddenFolders: Boolean = true,
    val treatNoMediaAsEmpty: Boolean = true,
    val treatEmptyFilesAsEmpty: Boolean = false,
    val dryRun: Boolean = false,
    val cleanAndroidFolder: Boolean = false,
    val hideDryRun: Boolean = false,
    val accentName: String = "Breeze Blue",
    val enableExternalStorage: Boolean = false,
    val externalStorageUri: String = ""
)

sealed interface ScreenState {
    object Idle : ScreenState
    data class ScanInProgress(
        val currentPath: String,
        val scannedCount: Int,
        val deletedCount: Int
    ) : ScreenState
    data class Finished(
        val totalScanned: Int,
        val totalDeleted: Int,
        val dryRun: Boolean,
        val durationMs: Long
    ) : ScreenState
}

class MutableStats {
    var scannedFolders = 0
    var deletedFolders = 0
}

data class StorageInfo(
    val totalBytes: Long,
    val availableBytes: Long,
    val usedBytes: Long,
    val readableTotal: String,
    val readableAvailable: String,
    val readableUsed: String,
    val percentUsed: Int
)

// ==========================================
// Main ViewModel Logic
// ==========================================

class FolderDeleterViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("cleaner_settings", Context.MODE_PRIVATE)

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _settings = MutableStateFlow(
        CleanerSettings(
            deleteHiddenFolders = prefs.getBoolean("delete_hidden_folders", true),
            treatNoMediaAsEmpty = prefs.getBoolean("treat_no_media", true),
            treatEmptyFilesAsEmpty = prefs.getBoolean("treat_empty_files", false),
            dryRun = prefs.getBoolean("dry_run", false),
            cleanAndroidFolder = prefs.getBoolean("clean_android", false),
            hideDryRun = prefs.getBoolean("hide_dry_run", false),
            accentName = prefs.getString("accent_name", "Breeze Blue") ?: "Breeze Blue",
            enableExternalStorage = prefs.getBoolean("enable_external", false),
            externalStorageUri = prefs.getString("external_uri", "") ?: ""
        )
    )
    val settings: StateFlow<CleanerSettings> = _settings.asStateFlow()

    private val _screenState = MutableStateFlow<ScreenState>(ScreenState.Idle)
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    private val _storageInfo = MutableStateFlow<StorageInfo?>(null)
    val storageInfo: StateFlow<StorageInfo?> = _storageInfo.asStateFlow()

    private var scanJob: Job? = null

    fun updateSettings(updater: (CleanerSettings) -> CleanerSettings) {
        _settings.update { current ->
            val next = updater(current)
            prefs.edit().apply {
                putBoolean("delete_hidden_folders", next.deleteHiddenFolders)
                putBoolean("treat_no_media", next.treatNoMediaAsEmpty)
                putBoolean("treat_empty_files", next.treatEmptyFilesAsEmpty)
                putBoolean("dry_run", next.dryRun)
                putBoolean("clean_android", next.cleanAndroidFolder)
                putBoolean("hide_dry_run", next.hideDryRun)
                putString("accent_name", next.accentName)
                putBoolean("enable_external", next.enableExternalStorage)
                putString("external_uri", next.externalStorageUri)
                apply()
            }
            next
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun addInfoLog(msg: String) {
        addLog(LogEntry.Info(msg))
    }

    private fun addLog(entry: LogEntry) {
        if (entry is LogEntry.ScanProgress) return
        _logs.update { list ->
            if (list.size > 800) list.takeLast(800) + entry else list + entry
        }
    }

    fun loadStorageInfo(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _storageInfo.value = getDeviceStorageStats(context)
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        addLog(LogEntry.Info("Cleaning operation cancelled by user."))
        _screenState.value = ScreenState.Idle
    }

    private val _rootAccessGranted = MutableStateFlow(false)
    val rootAccessGranted: StateFlow<Boolean> = _rootAccessGranted.asStateFlow()

    fun attemptRootRequest() {
        viewModelScope.launch(Dispatchers.IO) {
            addInfoLog("Requesting superuser (SU) privileges...")
            val success = requestRootAccess()
            if (success) {
                _rootAccessGranted.value = true
                addInfoLog("Root access successfully acquired! Superuser actions unlocked.")
            } else {
                _rootAccessGranted.value = false
                addInfoLog("Failed to acquire root access. Ensure device is rooted and SU is granted.")
            }
        }
    }

    fun disableRootAccess() {
        _rootAccessGranted.value = false
        addInfoLog("Root access option disabled.")
    }

    private fun requestRootAccess(): Boolean {
        var process: java.lang.Process? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            val os = java.io.DataOutputStream(process.outputStream)
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }

    fun findSdCardRoot(context: Context): File? {
        val externalFilesDirs = context.getExternalFilesDirs(null)
        for (file in externalFilesDirs) {
            if (file != null) {
                val path = file.absolutePath
                if (Environment.isExternalStorageRemovable(file) || !path.contains("emulated")) {
                    val index = path.indexOf("/Android/data")
                    if (index > 0) {
                        val rootPath = path.substring(0, index)
                        val sdRoot = File(rootPath)
                        if (sdRoot.exists() && sdRoot.isDirectory && sdRoot.canRead()) {
                            return sdRoot
                        }
                    }
                }
            }
        }
        try {
            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                val items = storageDir.listFiles()
                if (items != null) {
                    for (item in items) {
                        if (item.isDirectory && item.name != "self" && item.name != "emulated" && item.name != "sdcard0" && !item.name.startsWith("emulated")) {
                            if (item.canRead()) {
                                return item
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // standard bypass
        }
        return null
    }

    // Direct system File API scan (Requires permissions for absolute device paths)
    fun startDirectFileScan(root: File) {
        scanJob?.cancel()
        clearLogs()
        val currentSettings = _settings.value
        addLog(LogEntry.Info("Initializing delete on storage path: ${root.absolutePath}"))
        if (currentSettings.dryRun) {
            addLog(LogEntry.Info("[PREVIEW MODE] Running simulated scan. No folders will be deleted."))
        }

        val startTime = System.currentTimeMillis()
        _screenState.value = ScreenState.ScanInProgress(
            currentPath = root.absolutePath,
            scannedCount = 0,
            deletedCount = 0
        )

        scanJob = viewModelScope.launch(Dispatchers.IO) {
            val stats = MutableStats()
            try {
                if (_rootAccessGranted.value) {
                    cleanDirectoryWithRoot(
                        root,
                        deleteHidden = currentSettings.deleteHiddenFolders,
                        treatNoMediaAsEmpty = currentSettings.treatNoMediaAsEmpty,
                        treatEmptyFilesAsEmpty = currentSettings.treatEmptyFilesAsEmpty,
                        dryRun = currentSettings.dryRun,
                        cleanAndroidFolder = currentSettings.cleanAndroidFolder,
                        stats = stats,
                        onLog = { entry ->
                            addLog(entry)
                            _screenState.update { state ->
                                if (state is ScreenState.ScanInProgress) {
                                    state.copy(
                                        currentPath = if (entry is LogEntry.ScanProgress) entry.text else state.currentPath,
                                        scannedCount = stats.scannedFolders,
                                        deletedCount = stats.deletedFolders
                                    )
                                } else state
                            }
                        },
                        isCancelled = { !isActive }
                    )
                } else {
                    cleanDirectoryRecursive(
                        root,
                        deleteHidden = currentSettings.deleteHiddenFolders,
                        treatNoMediaAsEmpty = currentSettings.treatNoMediaAsEmpty,
                        treatEmptyFilesAsEmpty = currentSettings.treatEmptyFilesAsEmpty,
                        dryRun = currentSettings.dryRun,
                        cleanAndroidFolder = currentSettings.cleanAndroidFolder,
                        stats = stats,
                        onLog = { entry ->
                            addLog(entry)
                            _screenState.update { state ->
                                if (state is ScreenState.ScanInProgress) {
                                    state.copy(
                                        currentPath = if (entry is LogEntry.ScanProgress) entry.text else state.currentPath,
                                        scannedCount = stats.scannedFolders,
                                        deletedCount = stats.deletedFolders
                                    )
                                } else state
                            }
                        },
                        isCancelled = { !isActive }
                    )
                }

                val duration = System.currentTimeMillis() - startTime
                addLog(LogEntry.Info("Finished"))

                _screenState.value = ScreenState.Finished(
                    totalScanned = stats.scannedFolders,
                    totalDeleted = stats.deletedFolders,
                    dryRun = currentSettings.dryRun,
                    durationMs = duration
                )
            } catch (e: Exception) {
                addLog(LogEntry.Error("Critical scan error: ${e.localizedMessage}"))
                _screenState.value = ScreenState.Idle
            }
        }
    }

    // Ultra-fast superuser empty folder scanner using find/rm shell routines
    private fun cleanDirectoryWithRoot(
        root: File,
        deleteHidden: Boolean,
        treatNoMediaAsEmpty: Boolean,
        treatEmptyFilesAsEmpty: Boolean,
        dryRun: Boolean,
        cleanAndroidFolder: Boolean,
        stats: MutableStats,
        onLog: (LogEntry) -> Unit,
        isCancelled: () -> Boolean
    ) {
        onLog(LogEntry.Info("[ROOT ENGINE] Scanning directories at ultra-fast speeds using superuser privileges..."))

        val rootPathEscaped = root.absolutePath.replace("\"", "\\\"")
        val script = """
            find "$rootPathEscaped" -type d | while read -r d; do
                [ "${'$'}d" = "$rootPathEscaped" ] && continue
                
                # Never delete standard Android system folders themselves
                case "${'$'}d" in
                    */Android|*/Android/data|*/Android/obb|*/Android/media|*/Android/|*/Android/data/|*/Android/obb/|*/Android/media/) continue ;;
                esac

                if [ "$cleanAndroidFolder" = "false" ]; then
                    case "${'$'}d" in
                        */Android|*/Android/*) continue ;;
                    esac
                fi
                
                if [ "$deleteHidden" = "false" ]; then
                    case "${'$'}d" in
                        */.*|*/.*/*) continue ;;
                    esac
                fi
                
                [ -d "${'$'}d" ] || continue
                
                is_empty=1
                files_to_delete=""
                
                for f in "${'$'}d"/.* "${'$'}d"/*; do
                    [ -e "${'$'}f" ] || continue
                    
                    name="${'$'}{f##*/}"
                    [ "${'$'}name" = "." ] && continue
                    [ "${'$'}name" = ".." ] && continue
                    [ "${'$'}name" = "*" ] && continue
                    [ "${'$'}name" = ".*" ] && continue
                    
                    if [ -d "${'$'}f" ]; then
                        is_empty=0
                        break
                    fi
                    
                    is_useless=0
                    if [ "$treatNoMediaAsEmpty" = "true" ] && [ "${'$'}name" = ".nomedia" ]; then
                        is_useless=1
                    elif [ "$deleteHidden" = "true" ] && case "${'$'}name" in .*) true;; *) false;; esac; then
                        is_useless=1
                    elif [ "$treatEmptyFilesAsEmpty" = "true" ] && [ ! -s "${'$'}f" ]; then
                        is_useless=1
                    fi
                    
                    if [ "${'$'}is_useless" = "1" ]; then
                        files_to_delete="${'$'}files_to_delete\"${'$'}f\" "
                    else
                        is_empty=0
                        break
                    fi
                done
                
                if [ "${'$'}is_empty" = "1" ]; then
                    echo "EMPTY_DIR:${'$'}d|${'$'}files_to_delete"
                fi
            done
        """.trimIndent()

        val process = try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", script))
        } catch (e: Exception) {
            onLog(LogEntry.Error("[ROOT] Failed to run optimized root scanner: ${e.localizedMessage}. Falling back to standard JVM scanner."))
            cleanDirectoryRecursive(root, deleteHidden, treatNoMediaAsEmpty, treatEmptyFilesAsEmpty, dryRun, cleanAndroidFolder, stats, onLog, isCancelled)
            return
        }

        val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
        val discoveredEmptyDirs = mutableListOf<Pair<String, String>>() // Pair of (dirPath, filesToDelete)
        
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (isCancelled()) break
                val trimmedLine = line?.trim() ?: continue
                if (trimmedLine.startsWith("EMPTY_DIR:")) {
                    val content = trimmedLine.removePrefix("EMPTY_DIR:")
                    val parts = content.split('|', limit = 2)
                    if (parts.isNotEmpty()) {
                        val dirPath = parts[0]
                        val filesToDelete = if (parts.size > 1) parts[1] else ""
                        discoveredEmptyDirs.add(Pair(dirPath, filesToDelete))
                    }
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            onLog(LogEntry.Error("[ROOT] Error during optimized directory discovery: ${e.localizedMessage}"))
        } finally {
            try { reader.close() } catch (ignored: Exception) {}
            process.destroy()
        }

        if (isCancelled()) return

        // Sort by length descending to process deepest directories first
        discoveredEmptyDirs.sortByDescending { it.first.length }

        onLog(LogEntry.Info("[ROOT] Discovered ${discoveredEmptyDirs.size} empty directories. Proceeding with deletion..."))

        for ((dirPath, filesToDelete) in discoveredEmptyDirs) {
            if (isCancelled()) return
            
            val cleanPath = dirPath.replace("\\", "/").trimEnd('/')
            val isSystemRoot = cleanPath.endsWith("/Android", ignoreCase = true) ||
                    cleanPath.endsWith("/Android/data", ignoreCase = true) ||
                    cleanPath.endsWith("/Android/obb", ignoreCase = true) ||
                    cleanPath.endsWith("/Android/media", ignoreCase = true)
            if (isSystemRoot) {
                continue
            }

            stats.scannedFolders++
            onLog(LogEntry.ScanProgress(dirPath))

            if (dryRun) {
                stats.deletedFolders++
                onLog(LogEntry.Success(dirPath, dirPath, isDryRun = true))
            } else {
                val rmDirSuccess = try {
                    val cmd = if (filesToDelete.trim().isNotEmpty()) {
                        "rm -f $filesToDelete && rmdir \"$dirPath\""
                    } else {
                        "rmdir \"$dirPath\""
                    }
                    val rmProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                    rmProcess.waitFor() == 0
                } catch (e: Exception) {
                    File(dirPath).delete()
                }

                if (rmDirSuccess) {
                    stats.deletedFolders++
                    onLog(LogEntry.Success("Removed: $dirPath", dirPath, isDryRun = false))
                } else {
                    onLog(LogEntry.Error("Failed to remove root directory: $dirPath"))
                }
            }
        }
    }

    // Storage Access Framework DocumentTree scan (Compatible, safe, can query custom cards and custom folders)
    fun startDocumentTreeScan(context: Context, treeUri: Uri) {
        scanJob?.cancel()
        clearLogs()
        val currentSettings = _settings.value
        addLog(LogEntry.Info("Initializing Document Tree delete on selected folder."))
        if (currentSettings.dryRun) {
            addLog(LogEntry.Info("[PREVIEW MODE] Running simulated scan. No contents will be deleted."))
        }

        val startTime = System.currentTimeMillis()
        _screenState.value = ScreenState.ScanInProgress(
            currentPath = "Negotiating Storage Access Framework...",
            scannedCount = 0,
            deletedCount = 0
        )

        scanJob = viewModelScope.launch(Dispatchers.IO) {
            val stats = MutableStats()
            try {
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                if (rootDoc != null && rootDoc.isDirectory) {
                    cleanDocumentTreeRecursive(
                        context,
                        rootDoc,
                        deleteHidden = currentSettings.deleteHiddenFolders,
                        treatNoMediaAsEmpty = currentSettings.treatNoMediaAsEmpty,
                        treatEmptyFilesAsEmpty = currentSettings.treatEmptyFilesAsEmpty,
                        dryRun = currentSettings.dryRun,
                        cleanAndroidFolder = currentSettings.cleanAndroidFolder,
                        stats = stats,
                        onLog = { entry ->
                            addLog(entry)
                            _screenState.update { state ->
                                if (state is ScreenState.ScanInProgress) {
                                    state.copy(
                                        currentPath = if (entry is LogEntry.ScanProgress) entry.text else state.currentPath,
                                        scannedCount = stats.scannedFolders,
                                        deletedCount = stats.deletedFolders
                                    )
                                } else state
                            }
                        },
                        isCancelled = { !isActive }
                    )
                } else {
                    addLog(LogEntry.Error("Unable to acquire folder structure from selected URI."))
                }

                val duration = System.currentTimeMillis() - startTime
                addLog(LogEntry.Info("Finished"))

                _screenState.value = ScreenState.Finished(
                    totalScanned = stats.scannedFolders,
                    totalDeleted = stats.deletedFolders,
                    dryRun = currentSettings.dryRun,
                    durationMs = duration
                )
            } catch (e: Exception) {
                addLog(LogEntry.Error("Document scan error: ${e.localizedMessage}"))
                _screenState.value = ScreenState.Idle
            }
        }
    }

    // Recursive Deleter Engine for general files (Safe-skips operating system entries like /Android)
    private fun cleanDirectoryRecursive(
        dir: File,
        deleteHidden: Boolean,
        treatNoMediaAsEmpty: Boolean,
        treatEmptyFilesAsEmpty: Boolean,
        dryRun: Boolean,
        cleanAndroidFolder: Boolean,
        stats: MutableStats,
        onLog: (LogEntry) -> Unit,
        isCancelled: () -> Boolean
    ) {
        if (isCancelled()) return
        if (!dir.exists() || !dir.isDirectory) return

        val dirName = dir.name
        val isHiddenDir = dirName.startsWith(".")

        // Absolute security: Skip standard system metadata folders
        if (!cleanAndroidFolder && (dirName.equals("Android", ignoreCase = true) || dir.absolutePath.contains("/Android/data") || dir.absolutePath.contains("/Android/obb"))) {
            return
        }

        if (isHiddenDir && !deleteHidden) {
            onLog(LogEntry.ScanProgress("No-Touch (Hidden): $dirName"))
            return
        }

        stats.scannedFolders++
        onLog(LogEntry.ScanProgress(dir.absolutePath))

        val children = dir.listFiles()
        if (children != null) {
            // Traverse subdirectories bottom-up first
            for (child in children) {
                if (isCancelled()) return
                if (child.isDirectory) {
                    cleanDirectoryRecursive(
                        child,
                        deleteHidden,
                        treatNoMediaAsEmpty,
                        treatEmptyFilesAsEmpty,
                        dryRun,
                        cleanAndroidFolder,
                        stats,
                        onLog,
                        isCancelled
                    )
                }
            }
        }

        // Check folder contents again after cleaning nested directories
        val remainingChildren = dir.listFiles()
        if (remainingChildren == null) return
        var isCurrentlyEmpty = remainingChildren.isEmpty()
        val filesToDelete = mutableListOf<File>()

        if (!isCurrentlyEmpty) {
            var canEmpty = true
            for (child in remainingChildren) {
                if (child.isDirectory) {
                    canEmpty = false
                    break
                } else {
                    val fileName = child.name
                    val isNoMedia = fileName.equals(".nomedia", ignoreCase = true)
                    val isHiddenFile = fileName.startsWith(".")
                    val isEmptyFile = child.length() == 0L

                    val isUseless = (treatNoMediaAsEmpty && isNoMedia) ||
                            (deleteHidden && isHiddenFile && !isNoMedia) ||
                            (treatEmptyFilesAsEmpty && isEmptyFile)

                    if (isUseless) {
                        filesToDelete.add(child)
                    } else {
                        canEmpty = false
                        break
                    }
                }
            }
            if (canEmpty) {
                isCurrentlyEmpty = true
            }
        }

        if (isCurrentlyEmpty) {
            val path = dir.absolutePath
            val cleanPath = path.replace("\\", "/").trimEnd('/')
            val isSystemRoot = cleanPath.endsWith("/Android", ignoreCase = true) ||
                    cleanPath.endsWith("/Android/data", ignoreCase = true) ||
                    cleanPath.endsWith("/Android/obb", ignoreCase = true) ||
                    cleanPath.endsWith("/Android/media", ignoreCase = true)
            if (isSystemRoot) {
                return
            }
            if (dryRun) {
                stats.deletedFolders++
                onLog(LogEntry.Success(path, path, isDryRun = true))
            } else {
                // Perform real deletions
                var fileCleanupSuccess = true
                for (file in filesToDelete) {
                    if (!file.delete()) {
                        fileCleanupSuccess = false
                        onLog(LogEntry.Error("Unable to clean file inside: ${file.name}"))
                    }
                }
                if (fileCleanupSuccess) {
                    if (dir.delete()) {
                        stats.deletedFolders++
                        onLog(LogEntry.Success("Removed: $path", path, isDryRun = false))
                    } else {
                        onLog(LogEntry.Error("Failed to remove empty directory: $path"))
                    }
                }
            }
        }
    }

    // Recursive Deleter Engine for Storage Access Framework File Documents
    private fun cleanDocumentTreeRecursive(
        context: Context,
        dir: DocumentFile,
        deleteHidden: Boolean,
        treatNoMediaAsEmpty: Boolean,
        treatEmptyFilesAsEmpty: Boolean,
        dryRun: Boolean,
        cleanAndroidFolder: Boolean,
        stats: MutableStats,
        onLog: (LogEntry) -> Unit,
        isCancelled: () -> Boolean
    ) {
        if (isCancelled()) return
        if (!dir.exists() || !dir.isDirectory) return

        val dirName = dir.name ?: ""
        val isHiddenDir = dirName.startsWith(".")

        // Safety: Skip standard Android system files directories
        if (!cleanAndroidFolder && dirName.equals("Android", ignoreCase = true)) {
            return
        }

        if (isHiddenDir && !deleteHidden) {
            onLog(LogEntry.ScanProgress("No-Touch (Hidden): $dirName"))
            return
        }

        val fullPath = try {
            Uri.decode(dir.uri.toString()).substringAfterLast("document/", dir.name ?: "Unknown Directory")
        } catch (e: Exception) {
            dir.name ?: "Unknown Directory"
        }

        stats.scannedFolders++
        onLog(LogEntry.ScanProgress(fullPath))

        val children = dir.listFiles()
        for (child in children) {
            if (isCancelled()) return
            if (child.isDirectory) {
                cleanDocumentTreeRecursive(
                    context,
                    child,
                    deleteHidden,
                    treatNoMediaAsEmpty,
                    treatEmptyFilesAsEmpty,
                    dryRun,
                    cleanAndroidFolder,
                    stats,
                    onLog,
                    isCancelled
                )
            }
        }

        val remainingChildren = dir.listFiles()
        var isCurrentlyEmpty = remainingChildren.isEmpty()
        val filesToDelete = mutableListOf<DocumentFile>()

        if (!isCurrentlyEmpty) {
            var canEmpty = true
            for (child in remainingChildren) {
                if (child.isDirectory) {
                    canEmpty = false
                    break
                } else {
                    val fileName = child.name ?: ""
                    val isNoMedia = fileName.equals(".nomedia", ignoreCase = true)
                    val isHiddenFile = fileName.startsWith(".")
                    val isEmptyFile = child.length() == 0L

                    val isUseless = (treatNoMediaAsEmpty && isNoMedia) ||
                            (deleteHidden && isHiddenFile && !isNoMedia) ||
                            (treatEmptyFilesAsEmpty && isEmptyFile)

                    if (isUseless) {
                        filesToDelete.add(child)
                    } else {
                        canEmpty = false
                        break
                    }
                }
            }
            if (canEmpty) {
                isCurrentlyEmpty = true
            }
        }

        if (isCurrentlyEmpty) {
            val isSystemRoot = dirName.equals("Android", ignoreCase = true) ||
                    fullPath.replace("\\", "/").trimEnd('/').let { p ->
                        p.endsWith("/Android", ignoreCase = true) ||
                        p.endsWith("/Android/data", ignoreCase = true) ||
                        p.endsWith("/Android/obb", ignoreCase = true) ||
                        p.endsWith("/Android/media", ignoreCase = true)
                    }
            if (isSystemRoot) {
                return
            }
            if (dryRun) {
                stats.deletedFolders++
                onLog(LogEntry.Success(fullPath, dir.uri.toString(), isDryRun = true))
            } else {
                var fileCleanupSuccess = true
                for (file in filesToDelete) {
                    if (!file.delete()) {
                        fileCleanupSuccess = false
                        onLog(LogEntry.Error("Unable to clean file inside document: ${file.name}"))
                    }
                }
                if (fileCleanupSuccess) {
                    if (dir.delete()) {
                        stats.deletedFolders++
                        onLog(LogEntry.Success("Removed: $fullPath", dir.uri.toString(), isDryRun = false))
                    } else {
                        onLog(LogEntry.Error("Failed to remove document directory: $fullPath"))
                    }
                }
            }
        }
    }

    private fun getDeviceStorageStats(context: Context): StorageInfo {
        return try {
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val availableBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - availableBytes

            StorageInfo(
                totalBytes = totalBytes,
                availableBytes = availableBytes,
                usedBytes = usedBytes,
                readableTotal = formatSizeRepresentation(totalBytes),
                readableAvailable = formatSizeRepresentation(availableBytes),
                readableUsed = formatSizeRepresentation(usedBytes),
                percentUsed = if (totalBytes > 0L) ((usedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt() else 0
            )
        } catch (e: Exception) {
            StorageInfo(0, 0, 0, "0 GB", "0 GB", "0 GB", 0)
        }
    }

    private fun formatSizeRepresentation(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}

// ==========================================
// Main Activity View Entry Point
// ==========================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                FolderDeleterDashboard(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ==========================================
// Dashboard Composable Layout
// ==========================================

@SuppressLint("InlinedApi")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderDeleterDashboard(
    modifier: Modifier = Modifier,
    viewModel: FolderDeleterViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val screenState by viewModel.screenState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val storageInfo by viewModel.storageInfo.collectAsState()
    val rootAccessGranted by viewModel.rootAccessGranted.collectAsState()
    val accent = AppAccent.fromName(settings.accentName)

    val checkHasTotalAccess = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val readCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val writeCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            readCheck && writeCheck
        }
    }

    var isPermissionGranted by remember { mutableStateOf(checkHasTotalAccess()) }
    var showPermissionExplanatoryDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = showSettingsDialog) {
        showSettingsDialog = false
    }

    // Onboarding info display and first-launch permission request
    LaunchedEffect(Unit) {
        isPermissionGranted = checkHasTotalAccess()
        if (!isPermissionGranted) {
            showPermissionExplanatoryDialog = true
        }
    }

    // Storage Access Framework Launcher
    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addInfoLog("Folder access acquired via system dialog.")
            viewModel.addInfoLog("Tip: If 'Use this folder' is disabled at storage root, you can select any directory, or use the DELETE - Internal option.")
            viewModel.startDocumentTreeScan(context, uri)
        } else {
            viewModel.addInfoLog("Folder picker selection cancelled.")
        }
    }

    // External Storage custom path tree picker launcher
    val externalStoragePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addInfoLog("Custom external storage path selected.")
            viewModel.addInfoLog("Path Segment: ${uri.lastPathSegment}")
            viewModel.addInfoLog("Tip: System blocks picking direct storage roots, so selecting a sub-directory is recommended.")
            viewModel.updateSettings { it.copy(externalStorageUri = uri.toString()) }
        } else {
            viewModel.addInfoLog("External storage selection cancelled.")
        }
    }

    // Manage Files launcher for API 30+ Settings Redirect
    val allFilesSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val hasPermissionNow = checkHasTotalAccess()
        isPermissionGranted = hasPermissionNow
        if (hasPermissionNow) {
            viewModel.addInfoLog("System All Files Access successfully granted!")
            viewModel.addInfoLog("Please tap the scan button of your choice to begin!")
        } else {
            viewModel.addInfoLog("Permission was not granted. Direct full scan bypassed.")
        }
    }

    MyApplicationTheme(accent = accent) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                modifier = modifier,
                containerColor = Color.Transparent
            ) { paddingValues ->
            if (showSettingsDialog) {
                val accent = AppAccent.fromName(settings.accentName)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                    .padding(vertical = 20.dp)
            ) {
                // Settings Header Panel with Back Arrow Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 20.dp, bottom = 24.dp)
                ) {
                    IconButton(
                        onClick = { showSettingsDialog = false },
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Folder Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .background(accent.primary, shape = androidx.compose.foundation.shape.CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "By BlazeFTL",
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    // Beautiful system description card adapting to chosen accent
                Card(
                    colors = CardDefaults.cardColors(containerColor = accent.container),
                    border = BorderStroke(1.dp, accent.border),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = accent.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "System Engine Rules",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = accent.text
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Configure parameters for search algorithms. Enabling deep system paths can slow down execution but exposes isolated junk caches.",
                            fontSize = 11.sp,
                            color = accent.text.copy(alpha = 0.8f)
                        )
                    }
                }

                Text(
                    text = "ENGINE CONFIGURATION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                )

                // 1. Clean Android Folders Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, accent.primary.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(accent.container, shape = RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderSpecial,
                                    contentDescription = null,
                                    tint = accent.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Clean Android Folders",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Scan/clean folders inside Android system folder",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                        Switch(
                            checked = settings.cleanAndroidFolder,
                            onCheckedChange = { value ->
                                viewModel.updateSettings { it.copy(cleanAndroidFolder = value) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accent.primary,
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.testTag("clean_android_folder_switch")
                        )
                    }
                }

                // Delete Folders Containing Only .nomedia
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, accent.primary.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(accent.container, shape = RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOff,
                                    contentDescription = null,
                                    tint = accent.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Delete .nomedia Folders",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Delete folders containing only .nomedia files",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                        Switch(
                            checked = settings.treatNoMediaAsEmpty,
                            onCheckedChange = { value ->
                                viewModel.updateSettings { it.copy(treatNoMediaAsEmpty = value) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accent.primary,
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.testTag("treat_nomedia_as_empty_switch")
                        )
                    }
                }

                // Hide Preview Mode Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, accent.primary.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(accent.container, shape = RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = accent.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Hide Preview Mode",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Hide the Preview Mode card from the main screen",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                        Switch(
                            checked = settings.hideDryRun,
                            onCheckedChange = { value ->
                                viewModel.updateSettings { it.copy(hideDryRun = value) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accent.primary,
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.testTag("hide_preview_mode_switch")
                        )
                    }
                }

                // 2. Root Access Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, accent.primary.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(accent.container, shape = RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = accent.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Root Access",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (rootAccessGranted) "Superuser privileges active" else "Grant SU root access for system directories",
                                    fontSize = 11.sp,
                                    color = if (rootAccessGranted) Color(0xFF10B981) else Color(0xFF64748B)
                                )
                            }
                        }
                        Switch(
                            checked = rootAccessGranted,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    viewModel.attemptRootRequest()
                                } else {
                                    viewModel.disableRootAccess()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accent.primary,
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.testTag("root_access_switch")
                        )
                    }
                }

                // 3. Custom External Storage Location Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, accent.primary.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(accent.container, shape = RoundedCornerShape(12.dp))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = accent.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "External Storage Location",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Scan custom external storage location",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                            Switch(
                                checked = settings.enableExternalStorage,
                                onCheckedChange = { value ->
                                    viewModel.updateSettings { it.copy(enableExternalStorage = value) }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = accent.primary,
                                    uncheckedThumbColor = Color(0xFF94A3B8),
                                    uncheckedTrackColor = Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier.testTag("enable_external_storage_switch")
                            )
                        }

                        if (settings.enableExternalStorage) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFFF1F5F9))
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Storage Folder Path",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (settings.externalStorageUri.isNotEmpty()) {
                                            try {
                                                Uri.parse(settings.externalStorageUri).lastPathSegment ?: "Custom folder selected"
                                            } catch (e: Exception) {
                                                "Custom folder selected"
                                            }
                                        } else {
                                            "No location chosen yet"
                                        },
                                        fontSize = 11.sp,
                                        color = if (settings.externalStorageUri.isNotEmpty()) accent.primary else Color(0xFFEF4444)
                                    )
                                }

                                Button(
                                    onClick = {
                                        externalStoragePickerLauncher.launch(null)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = accent.container),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (settings.externalStorageUri.isNotEmpty()) "Change" else "Choose",
                                        color = accent.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "APP ACCENT COLOR",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                )

                // Grid of Accent Preset Cards
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    val presets = AppAccent.values().toList()
                    presets.chunked(3).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowPresets.forEach { p ->
                                AccentOptionCard(
                                    accent = p,
                                    isSelected = settings.accentName == p.displayName,
                                    onClick = {
                                        viewModel.updateSettings { it.copy(accentName = p.displayName) }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Fill remaining space if row is not full
                            if (rowPresets.size < 3) {
                                repeat(3 - rowPresets.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { showSettingsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = accent.primary),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Close", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {

                // ==========================================
                // Brand & Header Panel with Inline Permission Badge & Settings
                // ==========================================
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 10.dp)
                ) {
                    Text(
                        text = "Empty Folder Cleaner",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = accent.primary,
                        letterSpacing = (-0.5).sp,
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPermissionGranted) accent.container else Color(0xFFFEF2F2)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isPermissionGranted) accent.border else Color(0xFFFCA5A5)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .then(
                                if (!isPermissionGranted) {
                                    Modifier.clickable {
                                        showPermissionExplanatoryDialog = true
                                    }
                                } else Modifier
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isPermissionGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isPermissionGranted) accent.primary else Color(0xFFEF4444),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isPermissionGranted) "Access: Granted" else "Access: Denied",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPermissionGranted) accent.text else Color(0xFF991B1B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFF64748B)
                        )
                    }
                }

            // ==========================================
            // Action Delete Buttons Column (Stacked Layout)
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, accent.primary.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = screenState !is ScreenState.ScanInProgress) {
                            if (checkHasTotalAccess()) {
                                val extPath = java.io.File(Environment.getExternalStorageDirectory().absolutePath)
                                viewModel.startDirectFileScan(extPath)
                            } else {
                                showPermissionExplanatoryDialog = true
                            }
                        }
                        .testTag("clean_phone_memory_button")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.width(310.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(accent.container, shape = androidx.compose.foundation.shape.CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = accent.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Delete Empty Folders - Internal",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, accent.primary.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = screenState !is ScreenState.ScanInProgress) {
                            if (checkHasTotalAccess()) {
                                val sdRoot = viewModel.findSdCardRoot(context)
                                if (sdRoot != null) {
                                    viewModel.addInfoLog("Physical external SD Card detected at: ${sdRoot.absolutePath}")
                                    viewModel.startDirectFileScan(sdRoot)
                                } else {
                                    viewModel.addInfoLog("No mounted external SD Card detected under standard directories. Opening backup storage folder selector...")
                                    safLauncher.launch(null)
                                }
                            } else {
                                showPermissionExplanatoryDialog = true
                            }
                        }
                        .testTag("clean_sd_card_button")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.width(310.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(accent.container, shape = androidx.compose.foundation.shape.CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = accent.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Delete Empty Folders - SD Card",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }
                }

                if (settings.enableExternalStorage) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, accent.primary.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = screenState !is ScreenState.ScanInProgress) {
                                if (settings.externalStorageUri.isNotEmpty()) {
                                    try {
                                        viewModel.startDocumentTreeScan(context, Uri.parse(settings.externalStorageUri))
                                    } catch (e: Exception) {
                                        viewModel.addInfoLog("Error: Invalid storage path format. Opening folder picker to configure...")
                                        externalStoragePickerLauncher.launch(null)
                                    }
                                } else {
                                    viewModel.addInfoLog("No custom external storage path selected in Settings. Launching chooser...")
                                    externalStoragePickerLauncher.launch(null)
                                }
                            }
                            .testTag("clean_custom_storage_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier.width(310.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(accent.container, shape = androidx.compose.foundation.shape.CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = accent.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "Delete Empty Folders - External Storage",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // Preview Mode Directly Displayed
            // ==========================================
            if (!settings.hideDryRun) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, accent.primary.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Preview Mode",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Find empty folders without deleting them",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Switch(
                            checked = settings.dryRun,
                            onCheckedChange = { value ->
                                viewModel.updateSettings { it.copy(dryRun = value) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accent.primary,
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier.testTag("dry_run_switch")
                        )
                    }
                }
            }

            // ==========================================
            // Live Scanning Progress Card (Mini state display)
            // ==========================================
            AnimatedVisibility(visible = screenState is ScreenState.ScanInProgress) {
                val progressState = screenState as? ScreenState.ScanInProgress
                progressState?.let { progress ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, accent.primary.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "DELETION PROGRESS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    letterSpacing = 0.5.sp
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(accent.primary, shape = androidx.compose.foundation.shape.CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Running",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = accent.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = accent.primary,
                                trackColor = accent.container
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Path: ${progress.currentPath}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Scanned: ${progress.scannedCount}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = if (settings.dryRun) "Found: ${progress.deletedCount}" else "Deleted: ${progress.deletedCount}",
                                    fontSize = 11.sp,
                                    color = accent.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.cancelScan() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent.primary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            ) {
                                Text("Cancel Operation", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // Metrics Summary on Last Run (Compact Layout)
            // ==========================================
            AnimatedVisibility(visible = screenState is ScreenState.Finished) {
                val finished = screenState as? ScreenState.Finished
                finished?.let { stats ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = accent.container),
                        border = BorderStroke(1.dp, accent.border),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(accent.primary, shape = androidx.compose.foundation.shape.CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Done",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (stats.dryRun) "Scan Complete!" else "Deletion Complete!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = accent.text
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text(
                                        text = "Scanned Folders",
                                        fontSize = 10.sp,
                                        color = accent.text.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${stats.totalScanned}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp,
                                        color = accent.text
                                    )
                                }
                                Column {
                                    Text(
                                        text = if (stats.dryRun) "Folders Found" else "Folders Deleted",
                                        fontSize = 10.sp,
                                        color = accent.text.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${stats.totalDeleted}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp,
                                        color = accent.text
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Time",
                                        fontSize = 10.sp,
                                        color = accent.text.copy(alpha = 0.7f),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = String.format("%.2fs", stats.durationMs / 1000.0),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp,
                                        color = accent.text
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // Deletion Log Card (White Background, minimal)
            // ==========================================
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, accent.primary.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(accent.container, shape = RoundedCornerShape(6.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = "Log",
                                    tint = accent.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Log",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Color(0xFF0F172A)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .border(1.dp, accent.primary.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(Color(0xFF10B981), shape = androidx.compose.foundation.shape.CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Live",
                                    fontSize = 8.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(accent.container.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .border(1.dp, accent.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        if (logs.isEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                                    .testTag("log_idle_message_box")
                            ) {
                                Text(
                                    text = "Log idle. Start a scan to see results.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            val listState = rememberLazyListState()

                            LaunchedEffect(logs.count()) {
                                if (logs.isNotEmpty()) {
                                    listState.animateScrollToItem(logs.size - 1)
                                }
                            }

                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(logs.size) { index ->
                                    val log = logs[index]
                                    ConsoleLogLine(log = log, accent = accent)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

    // ==========================================
    // System Storage Permission Dialogue (Material 3 compliant)
    // ==========================================
    if (showPermissionExplanatoryDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionExplanatoryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Storage Access Request", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Android limits access to storage roots on modern versions. To effectively scan and clean empty folders across your internal and external storage, please allow Storage Access in system settings.",
                        fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionExplanatoryDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                allFilesSettingsLauncher.launch(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                allFilesSettingsLauncher.launch(intent)
                            }
                        }
                    }
                ) {
                    Text("Allow Storage Access")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionExplanatoryDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
        }
    }
}

// ==========================================
// Colored Log Line for Console Container
// ==========================================

@Composable
fun ConsoleLogLine(log: LogEntry, accent: AppAccent) {
    val chipColor = when (log) {
        is LogEntry.Success -> if (log.isDryRun) Color(0xFF8B5CF6) else Color(0xFF10B981) // Violet/Purple for EMPTY, Green for DELETED
        is LogEntry.Error -> Color(0xFFEF4444)
        is LogEntry.ScanProgress -> Color(0xFF64748B)
        is LogEntry.Info -> accent.primary
    }

    val label = when (log) {
        is LogEntry.Success -> if (log.isDryRun) "EMPTY" else "DELETED"
        is LogEntry.Error -> "FAILED"
        is LogEntry.ScanProgress -> "SCAN"
        is LogEntry.Info -> "STATUS"
    }

    val rowBgColor = when (log) {
        is LogEntry.Info -> accent.container  // Light theme-colored container tint for System status
        is LogEntry.Error -> Color(0xFFFEF2F2) // Light red tint for System error
        else -> Color.Transparent              // Transparent for standard scanned/deleted items
    }

    val rowBorder = when (log) {
        is LogEntry.Info -> BorderStroke(1.dp, accent.border)
        is LogEntry.Error -> BorderStroke(1.dp, Color(0xFFFEE2E2))
        else -> null
    }

    val textWeight = if (log is LogEntry.Info || log is LogEntry.Error) FontWeight.SemiBold else FontWeight.Normal
    val textColor = when (log) {
        is LogEntry.Info -> accent.text
        is LogEntry.Error -> Color(0xFF991B1B)
        else -> Color(0xFF475569) // Standard slate-dark text color for file paths
    }

    val fontFamily = if (log is LogEntry.Success || log is LogEntry.ScanProgress) FontFamily.Monospace else FontFamily.SansSerif

    Card(
        colors = CardDefaults.cardColors(containerColor = rowBgColor),
        border = rowBorder,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .background(chipColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                    .border(1.dp, chipColor.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                    .padding(vertical = 1.5.dp, horizontal = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = chipColor
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = log.text,
                fontSize = 9.5.sp,
                fontFamily = fontFamily,
                fontWeight = textWeight,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun imageOfLog(log: LogEntry): androidx.compose.ui.graphics.vector.ImageVector {
    return when (log) {
        is LogEntry.Success -> if (log.isDryRun) Icons.Default.Search else Icons.Default.Check
        is LogEntry.Error -> Icons.Default.Error
        is LogEntry.ScanProgress -> Icons.Default.Search
        is LogEntry.Info -> Icons.Default.Info
    }
}

@Composable
fun AccentOptionCard(
    accent: AppAccent,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) accent.container else Color.White
        ),
        border = BorderStroke(
            1.5.dp,
            if (isSelected) accent.primary else Color(0xFFE2E8F0)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .height(72.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(accent.primary, accent.border)
                        ),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = accent.displayName,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) accent.text else Color(0xFF475569)
            )
        }
    }
}
