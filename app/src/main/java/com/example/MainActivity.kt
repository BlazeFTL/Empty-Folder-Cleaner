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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val border: Color,
    val secondaryColor: Color = primary,
    val isMixed: Boolean = false
) {
    // Concept Ash/Green Default
    CONCEPT_ASH_GREEN("Concept Ash/Green", Color(0xFF5C6B8A), Color(0xFFECEEF9), Color(0xFF12172B), Color(0xFFDEE3EF), secondaryColor = Color(0xFF00B37E), isMixed = true),

    // 13 Preset Tailwind Color Styles matching user mockup
    BREEZE_BLUE("Breeze Blue", Color(0xFF3E7BFA), Color(0xFFEEF4FF), Color(0xFF1E40AF), Color(0xFFBFDBFE)),
    EMERALD_FOREST("Emerald Forest", Color(0xFF00B37E), Color(0xFFE1F8EF), Color(0xFF007A56), Color(0xFFA3F0D3)),
    WARM_AMBER("Warm Amber", Color(0xFFF5A623), Color(0xFFFFFBEB), Color(0xFFB45309), Color(0xFFFDE68A)),
    ROYAL_PURPLE("Royal Purple", Color(0xFF8B5CF6), Color(0xFFF5F3FF), Color(0xFF6D28D9), Color(0xFFDDD6FE)),
    BLAZE_CRIMSON("Blaze Crimson", Color(0xFFE63950), Color(0xFFFFF1F2), Color(0xFFBE123C), Color(0xFFFECDD3)),
    OCEAN_TEAL("Ocean Teal", Color(0xFF14B8A6), Color(0xFFF0FDFA), Color(0xFF0F766E), Color(0xFF99F6E4)),
    SUNSET_ORANGE("Sunset Orange", Color(0xFFFF8A3D), Color(0xFFFFF7ED), Color(0xFFC2410C), Color(0xFFFFEDD5)),
    DEEP_INDIGO("Deep Indigo", Color(0xFF3F4BD1), Color(0xFFEEF2FF), Color(0xFF2833A5), Color(0xFFC7D2FE)),
    ROSE_PINK("Rose Pink", Color(0xFFFF5C8A), Color(0xFFFDF2F8), Color(0xFFBE185D), Color(0xFFFBCFE8)),

    // Dual-accent gradient mixes
    MIDNIGHT_MINT("Midnight Mint", Color(0xFF2E8FB0), Color(0xFFE1F8EF), Color(0xFF0F766E), Color(0xFFA3F0D3), secondaryColor = Color(0xFF00B37E), isMixed = true),
    NEON_SYNTH("Neon Synth", Color(0xFF7C6CF0), Color(0xFFF5F3FF), Color(0xFF4C3EC0), Color(0xFFDDD6FE), secondaryColor = Color(0xFF3E7BFA), isMixed = true),
    CYBER_CITRUS("Cyber Citrus", Color(0xFFFF7A45), Color(0xFFFFF7ED), Color(0xFFC2410C), Color(0xFFFFEDD5), secondaryColor = Color(0xFFFF5C8A), isMixed = true),
    COSMIC_LAVENDER("Cosmic Lavender", Color(0xFF9C8CF5), Color(0xFFF5F3FF), Color(0xFF6D28D9), Color(0xFFDDD6FE));

    val bgBaseColor: Color
        get() = when (this) {
            CONCEPT_ASH_GREEN -> Color(0xFFEEF2F8)
            BREEZE_BLUE -> Color(0xFFEFF4FE)
            EMERALD_FOREST -> Color(0xFFEFF9F5)
            WARM_AMBER -> Color(0xFFFCF8EE)
            ROYAL_PURPLE -> Color(0xFFF6F4FD)
            BLAZE_CRIMSON -> Color(0xFFFCF1F3)
            OCEAN_TEAL -> Color(0xFFEFF9F8)
            SUNSET_ORANGE -> Color(0xFFFCF5EF)
            DEEP_INDIGO -> Color(0xFFEFF1FD)
            ROSE_PINK -> Color(0xFFFCF2F6)
            MIDNIGHT_MINT -> Color(0xFFEFF8F7)
            NEON_SYNTH -> Color(0xFFF4F3FD)
            CYBER_CITRUS -> Color(0xFFFCF3EF)
            COSMIC_LAVENDER -> Color(0xFFF7F5FD)
        }

    val bgGlow1: Color
        get() = when (this) {
            CONCEPT_ASH_GREEN -> Color(0xFFD6E4FB)
            MIDNIGHT_MINT -> Color(0xFFC5E8F3)
            NEON_SYNTH -> Color(0xFFDDD9FA)
            CYBER_CITRUS -> Color(0xFFFDD9CC)
            else -> primary.copy(alpha = 0.22f)
        }

    val bgGlow2: Color
        get() = when (this) {
            CONCEPT_ASH_GREEN -> Color(0xFFC7F2E2)
            MIDNIGHT_MINT -> Color(0xFFC2F2E2)
            NEON_SYNTH -> Color(0xFFCFE1FD)
            CYBER_CITRUS -> Color(0xFFFDCFDC)
            else -> if (isMixed) secondaryColor.copy(alpha = 0.20f) else primary.copy(alpha = 0.14f)
        }

    companion object {
        fun fromName(name: String): AppAccent {
            return values().firstOrNull { it.displayName.equals(name, ignoreCase = true) }
                ?: when (name.lowercase()) {
                    "concept ash/green", "ash green", "ash/green", "default" -> CONCEPT_ASH_GREEN
                    "sapphire blue", "breeze blue" -> BREEZE_BLUE
                    "emerald green", "emerald forest" -> EMERALD_FOREST
                    "warm amber" -> WARM_AMBER
                    "royal violet", "royal purple" -> ROYAL_PURPLE
                    "blaze crimson" -> BLAZE_CRIMSON
                    "coral orange", "sunset orange" -> SUNSET_ORANGE
                    "deep indigo", "cosmic dusk" -> DEEP_INDIGO
                    "rose pink" -> ROSE_PINK
                    "midnight mint", "mint aurora" -> MIDNIGHT_MINT
                    "neon synth", "electric velvet" -> NEON_SYNTH
                    "cyber citrus", "citrus flame" -> CYBER_CITRUS
                    "cosmic lavender" -> COSMIC_LAVENDER
                    "ocean teal", "ocean breeze" -> OCEAN_TEAL
                    else -> CONCEPT_ASH_GREEN
                }
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
    val accentName: String = "Concept Ash/Green",
    val enableExternalStorage: Boolean = false,
    val externalStorageUri: String = "",
    val scanDirectDataMedia: Boolean = true
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
            accentName = AppAccent.fromName(prefs.getString("accent_name", "Concept Ash/Green") ?: "Concept Ash/Green").displayName,
            enableExternalStorage = prefs.getBoolean("enable_external", false),
            externalStorageUri = prefs.getString("external_uri", "") ?: "",
            scanDirectDataMedia = prefs.getBoolean("scan_direct_data_media", true)
        )
    )
    val settings: StateFlow<CleanerSettings> = _settings.asStateFlow()

    private val _screenState = MutableStateFlow<ScreenState>(ScreenState.Idle)
    val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

    private val _storageInfo = MutableStateFlow<StorageInfo?>(null)
    val storageInfo: StateFlow<StorageInfo?> = _storageInfo.asStateFlow()

    private var scanJob: Job? = null

    init {
        if (prefs.getBoolean("root_access_enabled", false)) {
            viewModelScope.launch(Dispatchers.IO) {
                if (requestRootAccess()) {
                    _rootAccessGranted.value = true
                }
            }
        }
    }

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
                putBoolean("scan_direct_data_media", next.scanDirectDataMedia)
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
            val success = requestRootAccess()
            if (success) {
                _rootAccessGranted.value = true
                prefs.edit().putBoolean("root_access_enabled", true).apply()
                addOrReplaceRootLog("Root access enabled — High-speed direct scan active.")
            } else {
                _rootAccessGranted.value = false
                prefs.edit().putBoolean("root_access_enabled", false).apply()
                addOrReplaceRootLog("Failed to acquire root access. Ensure device is rooted and SU is granted.")
            }
        }
    }

    fun disableRootAccess() {
        _rootAccessGranted.value = false
        prefs.edit().putBoolean("root_access_enabled", false).apply()
        addOrReplaceRootLog("Root access option disabled.")
    }

    private fun addOrReplaceRootLog(msg: String) {
        _logs.update { list ->
            val filtered = list.filterNot { entry ->
                entry is LogEntry.Info && (
                    entry.text.contains("superuser", ignoreCase = true) ||
                    entry.text.contains("Root access", ignoreCase = true) ||
                    entry.text.contains("SU root", ignoreCase = true)
                )
            }
            filtered + LogEntry.Info(msg)
        }
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

    private fun getEffectiveRootForScan(root: File, scanDirectDataMedia: Boolean): File {
        if (!_rootAccessGranted.value) return root
        val path = root.absolutePath
        return when {
            path == "/storage/emulated/0" || path == "/storage/emulated/0/" || path == "/sdcard" || path == "/sdcard/" -> {
                File("/data/media/0")
            }
            path.startsWith("/storage/emulated/") -> {
                val userId = path.removePrefix("/storage/emulated/").substringBefore('/')
                if (userId.isNotEmpty() && userId.all { it.isDigit() }) {
                    val subPath = path.removePrefix("/storage/emulated/$userId")
                    File("/data/media/$userId$subPath")
                } else {
                    File("/data/media/0")
                }
            }
            path.startsWith("/storage/") && !path.startsWith("/storage/emulated") -> {
                val sdName = path.removePrefix("/storage/").substringBefore('/')
                if (sdName.isNotEmpty()) {
                    val mntRw = File("/mnt/media_rw/$sdName")
                    if (mntRw.exists() && mntRw.isDirectory) mntRw else root
                } else root
            }
            else -> root
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
        val targetRoot = getEffectiveRootForScan(root, currentSettings.scanDirectDataMedia)

        if (targetRoot.absolutePath != root.absolutePath) {
            addLog(LogEntry.Info("[ROOT ENGINE] Bypassing FUSE virtual filesystem slowdowns (${root.absolutePath}). Targeting direct ext4/f2fs storage path: ${targetRoot.absolutePath}"))
        } else {
            addLog(LogEntry.Info("Initializing delete on storage path: ${targetRoot.absolutePath}"))
        }

        if (currentSettings.dryRun) {
            addLog(LogEntry.Info("[PREVIEW MODE] Running simulated scan. No folders will be deleted."))
        }

        val startTime = System.currentTimeMillis()
        _screenState.value = ScreenState.ScanInProgress(
            currentPath = targetRoot.absolutePath,
            scannedCount = 0,
            deletedCount = 0
        )

        scanJob = viewModelScope.launch(Dispatchers.IO) {
            val stats = MutableStats()
            try {
                if (_rootAccessGranted.value) {
                    cleanDirectoryWithRoot(
                        targetRoot,
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
                        targetRoot,
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
                addLog(LogEntry.Info("Finished in ${duration}ms"))

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

        fun displayPath(p: String): String {
            return if (p.startsWith("/data/media/0")) {
                "/storage/emulated/0" + p.removePrefix("/data/media/0")
            } else p
        }

        val rootPathClean = root.absolutePath.trimEnd('/')
        val rootPathEscaped = rootPathClean.replace("\"", "\\\"")
        val script = """
            root="$rootPathEscaped"
            cleanAndroid="$cleanAndroidFolder"
            delHidden="$deleteHidden"
            treatNoMedia="$treatNoMediaAsEmpty"
            treatEmptyFiles="$treatEmptyFilesAsEmpty"
            isDryRun="$dryRun"

            find "$rootPathEscaped" -mindepth 1 -depth -type d 2>/dev/null | while read -r d; do
                [ -z "${'$'}d" ] && continue
                clean_d="${'$'}{d%/}"

                case "${'$'}clean_d" in
                    "${'$'}root"|"/storage/emulated/0"|"/data/media/0"|"/sdcard") continue ;;
                    */Android|*/Android/data|*/Android/obb|*/Android/media) continue ;;
                esac

                if [ "${'$'}cleanAndroid" = "false" ]; then
                    case "${'$'}clean_d" in
                        */Android|*/Android/*) continue ;;
                    esac
                fi

                if [ "${'$'}delHidden" = "false" ]; then
                    case "${'$'}clean_d" in
                        */.*|*/.*/*) continue ;;
                    esac
                fi

                [ -d "${'$'}clean_d" ] || continue
                echo "SCANNED:${'$'}clean_d"

                has_subdir=0
                has_valid_file=0
                useless_files=""

                for f in "${'$'}clean_d"/.* "${'$'}clean_d"/*; do
                    [ -e "${'$'}f" ] || [ -L "${'$'}f" ] || continue
                    name="${'$'}{f##*/}"
                    [ "${'$'}name" = "." ] && continue
                    [ "${'$'}name" = ".." ] && continue
                    [ "${'$'}name" = "*" ] && continue
                    [ "${'$'}name" = ".*" ] && continue

                    if [ -d "${'$'}f" ]; then
                        has_subdir=1
                        break
                    else
                        is_useless=0
                        if [ "${'$'}treatNoMedia" = "true" ] && [ "${'$'}name" = ".nomedia" ]; then
                            is_useless=1
                        elif [ "${'$'}delHidden" = "true" ] && case "${'$'}name" in .*) true;; *) false;; esac; then
                            is_useless=1
                        elif [ "${'$'}treatEmptyFiles" = "true" ] && [ ! -s "${'$'}f" ]; then
                            is_useless=1
                        fi

                        if [ "${'$'}is_useless" = "1" ]; then
                            useless_files="${'$'}useless_files \"${'$'}f\""
                        else
                            has_valid_file=1
                            break
                        fi
                    fi
                done

                if [ "${'$'}has_subdir" = "0" ] && [ "${'$'}has_valid_file" = "0" ]; then
                    if [ "${'$'}isDryRun" = "true" ]; then
                        echo "DRY_RUN:${'$'}clean_d"
                    else
                        if [ -n "${'$'}useless_files" ]; then
                            eval rm -f ${'$'}useless_files 2>/dev/null
                        fi
                        if rmdir "${'$'}clean_d" 2>/dev/null || rm -rf "${'$'}clean_d" 2>/dev/null; then
                            echo "DELETED:${'$'}clean_d"
                        else
                            echo "FAILED:${'$'}clean_d"
                        fi
                    fi
                fi
            done
        """.trimIndent()

        val process = try {
            val p = Runtime.getRuntime().exec("su")
            val os = java.io.DataOutputStream(p.outputStream)
            os.writeBytes("$script\nexit\n")
            os.flush()
            p
        } catch (e: Exception) {
            onLog(LogEntry.Error("[ROOT] Failed to run optimized root scanner: ${e.localizedMessage}. Falling back to standard JVM scanner."))
            cleanDirectoryRecursive(root, deleteHidden, treatNoMediaAsEmpty, treatEmptyFilesAsEmpty, dryRun, cleanAndroidFolder, stats, onLog, isCancelled)
            return
        }

        val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))

        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (isCancelled()) break
                val trimmed = line?.trim() ?: continue
                when {
                    trimmed.startsWith("SCANNED:") -> {
                        val dirPath = trimmed.removePrefix("SCANNED:")
                        val visPath = displayPath(dirPath)
                        stats.scannedFolders++
                        onLog(LogEntry.ScanProgress(visPath))
                    }
                    trimmed.startsWith("DELETED:") -> {
                        val dirPath = trimmed.removePrefix("DELETED:")
                        val visPath = displayPath(dirPath)
                        stats.deletedFolders++
                        onLog(LogEntry.Success("Removed: $visPath", visPath, isDryRun = false))
                    }
                    trimmed.startsWith("DRY_RUN:") -> {
                        val dirPath = trimmed.removePrefix("DRY_RUN:")
                        val visPath = displayPath(dirPath)
                        stats.deletedFolders++
                        onLog(LogEntry.Success(visPath, visPath, isDryRun = true))
                    }
                    trimmed.startsWith("FAILED:") -> {
                        val dirPath = trimmed.removePrefix("FAILED:")
                        val visPath = displayPath(dirPath)
                        onLog(LogEntry.Error("Failed to remove root directory: $visPath"))
                    }
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            onLog(LogEntry.Error("[ROOT] Error during optimized directory operation: ${e.localizedMessage}"))
        } finally {
            try { reader.close() } catch (ignored: Exception) {}
            process.destroy()
        }

        if (!isCancelled() && stats.scannedFolders == 0) {
            onLog(LogEntry.Info("[ROOT ENGINE] Deep verification scan: running secondary check to ensure no folders were missed..."))
            cleanDirectoryRecursive(root, deleteHidden, treatNoMediaAsEmpty, treatEmptyFilesAsEmpty, dryRun, cleanAndroidFolder, stats, onLog, isCancelled)
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
                    var deleted = dir.delete()
                    if (!deleted && _rootAccessGranted.value) {
                        try {
                            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "rmdir \"$path\" 2>/dev/null || rm -rf \"$path\" 2>/dev/null"))
                            deleted = p.waitFor() == 0
                        } catch (e: Exception) {
                            deleted = false
                        }
                    }
                    if (deleted) {
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
            FolderDeleterDashboard(
                modifier = Modifier.fillMaxSize()
            )
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
    var activeTarget by remember { mutableStateOf<String?>(null) } // "INTERNAL", "SD_CARD", "CUSTOM"

    BackHandler(enabled = showSettingsDialog) {
        showSettingsDialog = false
    }

    val mainScrollState = rememberScrollState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty() && screenState is ScreenState.ScanInProgress) {
            mainScrollState.animateScrollTo(mainScrollState.maxValue)
        }
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
            viewModel.addInfoLog("Tip: Select directory or use direct internal option.")
            viewModel.startDocumentTreeScan(context, uri)
        } else {
            viewModel.addInfoLog("Folder picker selection cancelled.")
            activeTarget = null
        }
    }

    // External Storage custom path tree picker launcher
    val externalStoragePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addInfoLog("Custom external storage path selected: ${uri.lastPathSegment}")
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
        } else {
            viewModel.addInfoLog("Permission was not granted.")
        }
    }

    val animatedBgBase by animateColorAsState(targetValue = accent.bgBaseColor, animationSpec = tween(350), label = "bgBase")
    val animatedBgGlow1 by animateColorAsState(targetValue = accent.bgGlow1, animationSpec = tween(350), label = "bgGlow1")
    val animatedBgGlow2 by animateColorAsState(targetValue = accent.bgGlow2, animationSpec = tween(350), label = "bgGlow2")

    MyApplicationTheme(accent = accent) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(animatedBgBase)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(animatedBgGlow1, Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, 0f),
                            radius = size.width * 0.85f
                        )
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(animatedBgGlow2, Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.2f),
                            radius = size.width * 0.75f
                        )
                    )
                }
        ) {
            Scaffold(
                containerColor = Color.Transparent
            ) { paddingValues ->
                if (showSettingsDialog) {
                    FolderSettingsScreen(
                        settings = settings,
                        rootAccessGranted = rootAccessGranted,
                        accent = accent,
                        onBack = { showSettingsDialog = false },
                        onUpdateSettings = { updater -> viewModel.updateSettings(updater) },
                        onAttemptRoot = { viewModel.attemptRootRequest() },
                        onDisableRoot = { viewModel.disableRootAccess() },
                        onPickExternalUri = { externalStoragePickerLauncher.launch(null) },
                        paddingValues = paddingValues
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                drawRect(animatedBgBase)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(animatedBgGlow1, animatedBgGlow1.copy(alpha = 0f)),
                                        center = androidx.compose.ui.geometry.Offset(size.width * 0.05f, 0f),
                                        radius = size.width * 0.95f
                                    )
                                )
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(animatedBgGlow2, animatedBgGlow2.copy(alpha = 0f)),
                                        center = androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.height * 0.12f),
                                        radius = size.width * 0.90f
                                    )
                                )
                            }
                            .padding(paddingValues),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 500.dp)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                        // Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "Empty Folder Cleaner",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color(0xFF12172B)
                                )
                                Text(
                                    text = "STORAGE · RECLAIM",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF9CA3B8),
                                    letterSpacing = 0.6.sp
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Access Pill Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (isPermissionGranted) {
                                            if (accent == AppAccent.CONCEPT_ASH_GREEN) Color(0xFFE1F8EF) else accent.container
                                        } else Color(0xFFFEF2F2)
                                    )
                                    .clickable {
                                        if (!isPermissionGranted) {
                                            showPermissionExplanatoryDialog = true
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isPermissionGranted) Icons.Default.Check else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (isPermissionGranted) {
                                            if (accent == AppAccent.CONCEPT_ASH_GREEN) Color(0xFF00B37E) else accent.primary
                                        } else Color(0xFFDC2626),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isPermissionGranted) "Access Granted" else "Access Pending",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPermissionGranted) {
                                            if (accent == AppAccent.CONCEPT_ASH_GREEN) Color(0xFF00B37E) else accent.primary
                                        } else Color(0xFFDC2626)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Settings Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(Color.White)
                                    .border(1.dp, Color(0xFFDEE3EF), RoundedCornerShape(11.dp))
                                    .clickable { showSettingsDialog = true }
                                    .testTag("settings_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = Color(0xFF636C82),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Target Switcher Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val isScanning = screenState is ScreenState.ScanInProgress

                            // Target 1: Internal Storage
                            TargetButton(
                                title = "Internal Storage",
                                icon = Icons.Default.PhoneAndroid,
                                isActive = activeTarget == "INTERNAL",
                                isDisabled = isScanning && activeTarget != "INTERNAL",
                                accent = accent,
                                testTag = "clean_phone_memory_button",
                                onClick = {
                                    if (checkHasTotalAccess()) {
                                        activeTarget = "INTERNAL"
                                        val extPath = File(Environment.getExternalStorageDirectory().absolutePath)
                                        viewModel.startDirectFileScan(extPath)
                                    } else {
                                        showPermissionExplanatoryDialog = true
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // Target 2: SD Card
                            TargetButton(
                                title = "SD Card",
                                icon = Icons.Default.SdCard,
                                isActive = activeTarget == "SD_CARD",
                                isDisabled = isScanning && activeTarget != "SD_CARD",
                                accent = accent,
                                testTag = "clean_sd_card_button",
                                onClick = {
                                    if (checkHasTotalAccess()) {
                                        activeTarget = "SD_CARD"
                                        val sdRoot = viewModel.findSdCardRoot(context)
                                        if (sdRoot != null) {
                                            viewModel.addInfoLog("Physical external SD Card detected at: ${sdRoot.absolutePath}")
                                            viewModel.startDirectFileScan(sdRoot)
                                        } else {
                                            viewModel.addInfoLog("No mounted SD card found. Opening folder selector...")
                                            safLauncher.launch(null)
                                        }
                                    } else {
                                        showPermissionExplanatoryDialog = true
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // Target 3: Custom External Storage (If enabled)
                            if (settings.enableExternalStorage) {
                                TargetButton(
                                    title = "Custom Storage",
                                    icon = Icons.Default.Folder,
                                    isActive = activeTarget == "CUSTOM",
                                    isDisabled = isScanning && activeTarget != "CUSTOM",
                                    accent = accent,
                                    testTag = "clean_custom_storage_button",
                                    onClick = {
                                        if (settings.externalStorageUri.isNotEmpty()) {
                                            try {
                                                activeTarget = "CUSTOM"
                                                viewModel.startDocumentTreeScan(context, Uri.parse(settings.externalStorageUri))
                                            } catch (e: Exception) {
                                                externalStoragePickerLauncher.launch(null)
                                            }
                                        } else {
                                            externalStoragePickerLauncher.launch(null)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Deletion Progress Card
                        if (screenState is ScreenState.ScanInProgress || screenState is ScreenState.Finished) {
                            ProgressCard(
                                screenState = screenState,
                                settings = settings,
                                accent = accent,
                                onCancel = {
                                    viewModel.cancelScan()
                                    activeTarget = null
                                }
                            )
                        }

                        // Preview Mode Switch Card
                        if (!settings.hideDryRun) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFDEE3EF)),
                                shape = RoundedCornerShape(18.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Preview Mode",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.5.sp,
                                            color = Color(0xFF12172B)
                                        )
                                        Text(
                                            text = "Find empty folders without deleting them",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF636C82)
                                        )
                                    }
                                    CustomCapsuleToggle(
                                        checked = settings.dryRun,
                                        onCheckedChange = { value ->
                                            viewModel.updateSettings { it.copy(dryRun = value) }
                                        },
                                        accent = accent,
                                        testTag = "dry_run_switch"
                                    )
                                }
                            }
                        }

                        // Live Log Card
                        LiveLogCard(
                            logs = logs,
                            isScanning = screenState is ScreenState.ScanInProgress,
                            accent = accent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                        )
                    }
                }
            }
        }
        }
    }

    // Storage Access Permission Dialog
    if (showPermissionExplanatoryDialog) {
        Dialog(
            onDismissRequest = { showPermissionExplanatoryDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFDEE3EF)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 380.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Icon Container
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(accent.container),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = accent.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Storage Access Required",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF12172B),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "To scan and clean empty folders across your internal and external storage, Empty Folder Cleaner requires All Files Access permission.",
                        fontSize = 13.sp,
                        color = Color(0xFF636C82),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Grant Access Primary Action Button
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
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent.primary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Grant Storage Access",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Maybe Later Secondary Dismiss Button
                    TextButton(
                        onClick = { showPermissionExplanatoryDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Maybe Later",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF9CA3B8)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// Target Switcher Button Composable
// ==========================================

@Composable
fun TargetButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    isDisabled: Boolean,
    accent: AppAccent,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedBgColor by animateColorAsState(
        targetValue = if (isActive) accent.primary else Color.White,
        animationSpec = tween(200),
        label = "targetBg"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isActive) accent.primary else Color(0xFFDEE3EF),
        animationSpec = tween(200),
        label = "targetBorder"
    )
    val animatedTextColor by animateColorAsState(
        targetValue = if (isActive) Color.White else Color(0xFF12172B),
        animationSpec = tween(200),
        label = "targetText"
    )
    val animatedIconBoxBg by animateColorAsState(
        targetValue = if (isActive) Color.White.copy(alpha = 0.18f) else Color(0xFFF6F8FC),
        animationSpec = tween(200),
        label = "targetIconBoxBg"
    )
    val animatedIconTint by animateColorAsState(
        targetValue = if (isActive) Color.White else accent.primary,
        animationSpec = tween(200),
        label = "targetIconTint"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = animatedBgColor),
        border = BorderStroke(1.dp, animatedBorderColor),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .then(if (isDisabled) Modifier.background(animatedBgColor.copy(alpha = 0.45f), RoundedCornerShape(18.dp)) else Modifier)
            .clickable(enabled = !isDisabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(animatedIconBoxBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = animatedIconTint,
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                color = animatedTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==========================================
// Deletion Progress Card (Running / Complete)
// ==========================================

@Composable
fun ProgressCard(
    screenState: ScreenState,
    settings: CleanerSettings,
    accent: AppAccent,
    onCancel: () -> Unit
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFDEE3EF)),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            if (screenState is ScreenState.ScanInProgress) {
                // Running State
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DELETION PROGRESS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9CA3B8),
                        letterSpacing = 0.8.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (accent == AppAccent.CONCEPT_ASH_GREEN) Color(0xFFE1F8EF) else accent.container)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background((if (accent == AppAccent.CONCEPT_ASH_GREEN) Color(0xFF00B37E) else accent.primary).copy(alpha = pulseAlpha))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Running",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (accent == AppAccent.CONCEPT_ASH_GREEN) Color(0xFF00B37E) else accent.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Dynamic Animated Progress Bar starting at 0% (5% initial width) on launch
                val targetProgress = if (screenState is ScreenState.ScanInProgress) {
                    if (screenState.scannedCount == 0) 0.05f
                    else (1f - kotlin.math.exp(-screenState.scannedCount / 350.0).toFloat()).coerceIn(0.08f, 0.96f)
                } else 1f

                val animatedProgress by animateFloatAsState(
                    targetValue = targetProgress,
                    animationSpec = tween(durationMillis = 350),
                    label = "progressAnim"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFF6F8FC))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = animatedProgress)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    colors = if (accent == AppAccent.CONCEPT_ASH_GREEN) listOf(accent.primary, Color(0xFF00B37E))
                                             else if (accent.isMixed) listOf(accent.primary, accent.secondaryColor)
                                             else listOf(accent.primary, accent.primary.copy(alpha = 0.75f))
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Path: ${screenState.currentPath}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    color = Color(0xFF636C82),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = AnnotatedString("Scanned: ") + AnnotatedString("${screenState.scannedCount}", spanStyle = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF12172B))),
                        fontSize = 12.5.sp,
                        color = Color(0xFF636C82)
                    )
                    Text(
                        text = AnnotatedString(if (settings.dryRun) "Found: " else "Deleted: ") + AnnotatedString("${screenState.deletedCount}", spanStyle = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF12172B))),
                        fontSize = 12.5.sp,
                        color = Color(0xFF636C82)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                val cancelBtnColor = if (accent == AppAccent.CONCEPT_ASH_GREEN) Color(0xFF00B37E) else accent.primary

                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cancelBtnColor),
                    border = BorderStroke(1.5.dp, cancelBtnColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Text("Cancel Operation", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                }
            } else if (screenState is ScreenState.Finished) {
                // Complete State
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (accent == AppAccent.CONCEPT_ASH_GREEN) Color(0xFF00B37E) else accent.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (screenState.dryRun) "Scan Complete!" else "Deletion Complete!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF12172B)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${screenState.totalScanned}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = Color(0xFF1F2A63)
                        )
                        Text(
                            text = "SCANNED",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF9CA3B8),
                            letterSpacing = 0.6.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${screenState.totalDeleted}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = Color(0xFF1F2A63)
                        )
                        Text(
                            text = if (screenState.dryRun) "FOUND" else "DELETED",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF9CA3B8),
                            letterSpacing = 0.6.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%.2fs", screenState.durationMs / 1000.0),
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            color = Color(0xFF1F2A63)
                        )
                        Text(
                            text = "TIME",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF9CA3B8),
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// Live Log Card Composable
// ==========================================

@Composable
fun LiveLogCard(
    logs: List<LogEntry>,
    isScanning: Boolean,
    accent: AppAccent,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "logPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFDEE3EF)),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Log",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF12172B)
                )

                val runningBadgeBg = if (accent == AppAccent.CONCEPT_ASH_GREEN) {
                    Color(0xFFE1F8EF)
                } else if (isScanning) {
                    accent.container
                } else {
                    Color.White
                }

                val runningBadgeTint = if (accent == AppAccent.CONCEPT_ASH_GREEN) {
                    Color(0xFF00B37E)
                } else if (isScanning) {
                    accent.primary
                } else {
                    Color(0xFF9CA3B8)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(runningBadgeBg)
                        .border(1.dp, if (isScanning || accent == AppAccent.CONCEPT_ASH_GREEN) runningBadgeBg else Color(0xFFDEE3EF), RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(
                                    if (isScanning) runningBadgeTint.copy(alpha = pulseAlpha) else runningBadgeTint
                                )
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isScanning) "Running" else "Idle",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = runningBadgeTint
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                        .testTag("log_idle_message_box"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No activity yet — run a scan to see deleted folders here.",
                        fontSize = 13.sp,
                        color = Color(0xFF9CA3B8),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val listState = rememberLazyListState()

                LaunchedEffect(logs.size) {
                    if (logs.isNotEmpty()) {
                        listState.animateScrollToItem(logs.size - 1)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    items(
                        count = logs.size,
                        key = { index -> index }
                    ) { index ->
                        LogTimelineEntry(
                            log = logs[index],
                            accent = accent,
                            isFirstItem = index == 0,
                            isLastItem = index == logs.size - 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogTimelineEntry(
    log: LogEntry,
    accent: AppAccent,
    isFirstItem: Boolean = false,
    isLastItem: Boolean = false
) {
    val tagText = when (log) {
        is LogEntry.Success -> if (log.isDryRun) "EMPTY" else "DELETED"
        is LogEntry.Error -> "FAILED"
        is LogEntry.ScanProgress -> "SCAN"
        is LogEntry.Info -> "STATUS"
    }

    val tagBg = when (log) {
        is LogEntry.Success -> if (accent == AppAccent.CONCEPT_ASH_GREEN) Color(0xFFE1F8EF) else accent.container
        is LogEntry.Error -> Color(0xFFFEF2F2)
        else -> if (accent == AppAccent.CONCEPT_ASH_GREEN) Color(0xFFE1F8EF) else accent.container
    }

    val tagTextColor = when (log) {
        is LogEntry.Success -> if (accent == AppAccent.CONCEPT_ASH_GREEN) Color(0xFF00B37E) else accent.primary
        is LogEntry.Error -> Color(0xFFDC2626)
        else -> if (accent == AppAccent.CONCEPT_ASH_GREEN) Color(0xFF00B37E) else accent.primary
    }

    val nodeBorderColor = when (log) {
        is LogEntry.Success -> if (accent == AppAccent.CONCEPT_ASH_GREEN) Color(0xFF00B37E) else accent.primary
        is LogEntry.Error -> Color(0xFFDC2626)
        else -> if (accent == AppAccent.CONCEPT_ASH_GREEN) Color(0xFF00B37E) else accent.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val dotX = 11.dp.toPx() / 2f
                val dotCenterY = 10.dp.toPx()
                
                if (!isFirstItem) {
                    drawLine(
                        color = Color(0xFFDEE3EF),
                        start = androidx.compose.ui.geometry.Offset(dotX, 0f),
                        end = androidx.compose.ui.geometry.Offset(dotX, dotCenterY),
                        strokeWidth = 2.dp.toPx()
                    )
                }
                if (!isLastItem) {
                    drawLine(
                        color = Color(0xFFDEE3EF),
                        start = androidx.compose.ui.geometry.Offset(dotX, dotCenterY),
                        end = androidx.compose.ui.geometry.Offset(dotX, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Circle Node Dot
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(tagBg)
                        .border(2.dp, nodeBorderColor, CircleShape)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(tagBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = tagText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = tagTextColor,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = log.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                color = Color(0xFF636C82),
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 21.dp)
            )
        }
    }
}

// ==========================================
// Folder Settings Screen Composable
// ==========================================

@Composable
fun FolderSettingsScreen(
    settings: CleanerSettings,
    rootAccessGranted: Boolean,
    accent: AppAccent,
    onBack: () -> Unit,
    onUpdateSettings: ((CleanerSettings) -> CleanerSettings) -> Unit,
    onAttemptRoot: () -> Unit,
    onDisableRoot: () -> Unit,
    onPickExternalUri: () -> Unit,
    paddingValues: PaddingValues
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Color(0xFFEEF2F8))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFD6E4FB), Color(0x00D6E4FB)),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.05f, 0f),
                        radius = size.width * 0.95f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFC7F2E2), Color(0x00C7F2E2)),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.height * 0.12f),
                        radius = size.width * 0.90f
                    )
                )
            }
            .padding(paddingValues),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        // Settings Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFDEE3EF), RoundedCornerShape(11.dp))
                    .clickable(onClick = onBack)
                    .testTag("settings_back_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF12172B),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Folder Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = Color(0xFF12172B)
            )

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accent.primary)
                    .padding(horizontal = 13.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "By BlazeFTL",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }

        // Info Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = accent.container),
            border = BorderStroke(1.dp, accent.primary.copy(alpha = 0.14f)),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(accent.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "i",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "System Engine Rules",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = Color(0xFF12172B)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Configure parameters for search algorithms. Enabling deep system paths can slow down execution but exposes isolated junk caches.",
                        fontSize = 12.5.sp,
                        color = Color(0xFF636C82),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Section Label: ENGINE CONFIGURATION
        Text(
            text = "ENGINE CONFIGURATION",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF9CA3B8),
            letterSpacing = 0.8.sp
        )

        // Rule 1: Clean Android Folders
        RuleRow(
            title = "Clean Android Folders",
            subtitle = "Scan/clean folders inside Android system folder",
            icon = Icons.Default.FolderSpecial,
            checked = settings.cleanAndroidFolder,
            accent = accent,
            testTag = "clean_android_folder_switch",
            onCheckedChange = { checked ->
                onUpdateSettings { it.copy(cleanAndroidFolder = checked) }
            }
        )

        // Rule 2: Delete .nomedia Folders
        RuleRow(
            title = "Delete .nomedia Folders",
            subtitle = "Delete folders containing only .nomedia files",
            icon = Icons.Default.VideocamOff,
            checked = settings.treatNoMediaAsEmpty,
            accent = accent,
            testTag = "treat_nomedia_as_empty_switch",
            onCheckedChange = { checked ->
                onUpdateSettings { it.copy(treatNoMediaAsEmpty = checked) }
            }
        )

        // Rule 3: Hide Preview Mode
        RuleRow(
            title = "Hide Preview Mode",
            subtitle = "Hide the Preview Mode card from the main screen",
            icon = Icons.Default.VisibilityOff,
            checked = settings.hideDryRun,
            accent = accent,
            testTag = "hide_preview_mode_switch",
            onCheckedChange = { checked ->
                onUpdateSettings { it.copy(hideDryRun = checked) }
            }
        )

        // Rule 4: Root Access
        RuleRow(
            title = "Root Access",
            subtitle = if (rootAccessGranted) "SU root access granted" else "Grant SU root access for system directories",
            icon = Icons.Default.Shield,
            checked = rootAccessGranted,
            accent = accent,
            testTag = "root_access_switch",
            onCheckedChange = { checked ->
                if (checked) onAttemptRoot() else onDisableRoot()
            }
        )

        // Rule 4.5: Direct /data/media/ Scan (only shown when root access is granted)
        if (rootAccessGranted) {
            RuleRow(
                title = "Direct /data/media/ Scan",
                subtitle = "Target direct /data/media/0 path with superuser permissions",
                icon = Icons.Default.Storage,
                checked = settings.scanDirectDataMedia,
                accent = accent,
                testTag = "scan_direct_data_media_switch",
                onCheckedChange = { checked ->
                    onUpdateSettings { it.copy(scanDirectDataMedia = checked) }
                }
            )
        }

        // Rule 5: External Storage Location
        RuleRow(
            title = "External Storage Location",
            subtitle = "Scan custom external storage location",
            icon = Icons.Default.Layers,
            checked = settings.enableExternalStorage,
            accent = accent,
            testTag = "enable_external_storage_switch",
            onCheckedChange = { checked ->
                onUpdateSettings { it.copy(enableExternalStorage = checked) }
            }
        )

        if (settings.enableExternalStorage) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFDEE3EF)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
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
                            text = "Storage Path",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            color = Color(0xFF12172B)
                        )
                        Text(
                            text = if (settings.externalStorageUri.isNotEmpty()) settings.externalStorageUri else "No path selected",
                            fontSize = 11.sp,
                            color = Color(0xFF636C82),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = onPickExternalUri,
                        colors = ButtonDefaults.buttonColors(containerColor = accent.container),
                        shape = RoundedCornerShape(12.dp)
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

        // Section Label: APP ACCENT COLOR
        Text(
            text = "APP ACCENT COLOR",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF9CA3B8),
            letterSpacing = 0.8.sp
        )

        // Color Grid (3 columns)
        val accents = AppAccent.values()
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            accents.toList().chunked(3).forEach { rowAccents ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowAccents.forEach { acc ->
                        AccentColorCard(
                            accentOption = acc,
                            isSelected = settings.accentName.equals(acc.displayName, ignoreCase = true),
                            onClick = {
                                onUpdateSettings { it.copy(accentName = acc.displayName) }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowAccents.size < 3) {
                        repeat(3 - rowAccents.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Close Button
        Button(
            onClick = onBack,
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
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Close", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}
}

@Composable
fun RuleRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    accent: AppAccent,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFDEE3EF)),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF6F8FC)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = Color(0xFF12172B)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = Color(0xFF636C82),
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            CustomCapsuleToggle(
                checked = checked,
                onCheckedChange = onCheckedChange,
                accent = accent,
                testTag = testTag
            )
        }
    }
}

@Composable
fun CustomCapsuleToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: AppAccent,
    testTag: String = "",
    modifier: Modifier = Modifier
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) accent.primary else Color(0xFFDEE3EF),
        animationSpec = tween(durationMillis = 200),
        label = "trackColor"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 23.dp else 3.dp,
        animationSpec = tween(durationMillis = 200),
        label = "thumbOffset"
    )

    Box(
        modifier = modifier
            .width(46.dp)
            .height(26.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable { onCheckedChange(!checked) }
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
fun AccentColorCard(
    accentOption: AppAccent,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) accentOption.primary else Color(0xFFDEE3EF)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        brush = if (accentOption.isMixed) {
                            Brush.linearGradient(colors = listOf(accentOption.primary, accentOption.secondaryColor))
                        } else {
                            Brush.linearGradient(colors = listOf(accentOption.primary, accentOption.primary))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(9.dp))

            Text(
                text = accentOption.displayName,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF12172B),
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}
