package com.zakodaniumask.manager.data.flash

import android.content.Context
import android.net.Uri
import android.util.Log
import com.zakodaniumask.manager.R
import com.zakodaniumask.manager.data.shell.KsuCliRepository
import com.zakodaniumask.manager.domain.model.FlashProgress
import com.zakodaniumask.manager.utils.AssetsUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * @author ShirkNeko
 * @date 2025/5/31.
 */
class HorizonKernelState {
    private val _state = MutableStateFlow(FlashProgress())
    private val fullLogs = ConcurrentLinkedQueue<String>()
    val state: StateFlow<FlashProgress> = _state.asStateFlow()

    fun updateProgress(progress: Float) {
        _state.update { it.copy(progress = progress) }
    }

    fun updateStep(step: String) {
        _state.update { it.copy(currentStep = step) }
    }

    fun addLog(log: String) {
        fullLogs.add(log)
        _state.update {
            it.copy(logs = it.logs + log)
        }
    }

    fun addConsoleLog(log: String) {
        fullLogs.add(log)
    }

    fun getFullLog(): String = fullLogs.joinToString("\n")

    fun setError(error: String) {
        _state.update { it.copy(isFlashing = false, error = error) }
    }

    fun startFlashing() {
        fullLogs.clear()
        _state.update {
            it.copy(
                isFlashing = true,
                isCompleted = false,
                progress = 0f,
                currentStep = "",
                logs = emptyList(),
                error = ""
            )
        }
    }

    fun completeFlashing() {
        _state.update {
            it.copy(
                isFlashing = false,
                isCompleted = true,
                progress = 1f
            )
        }
    }

    fun reset() {
        fullLogs.clear()
        _state.value = FlashProgress()
    }
}

class HorizonKernelWorker(
    private val context: Context,
    private val state: HorizonKernelState,
    private val ksuCliRepository: KsuCliRepository,
    private val slot: String? = null,
    private val skipKsud: Boolean = false,
    private val kpmPatchEnabled: Boolean = false,
    private val kpmUndoPatch: Boolean = false,
) : Thread() {
    var uri: Uri? = null

    override fun run() {
        state.startFlashing()
        state.updateStep(context.getString(R.string.horizon_preparing))

        val zipFile = File(context.cacheDir, "anykernel3.zip")
        try {
            if (!ksuCliRepository.rootAvailable()) {
                state.setError(context.getString(R.string.root_required))
                return
            }

            state.updateStep(context.getString(R.string.horizon_copying_files))
            state.updateProgress(0.2f)
            copyToCache(zipFile)

            if (kpmPatchEnabled || kpmUndoPatch) {
                state.updateStep(context.getString(R.string.kpm_preparing_tools))
                state.updateProgress(0.35f)
                performKpmPatch(zipFile)
            }

            state.updateStep(context.getString(R.string.horizon_flashing))
            state.updateProgress(0.7f)
            val succeeded = ksuCliRepository.flashAnyKernel(
                zipFile = zipFile,
                slot = slot,
                onStdout = ::handleOutput,
                onStderr = ::handleConsoleOutput
            )
            if (!succeeded) {
                state.setError(context.getString(R.string.flash_failed_message))
                return
            }

            if (!skipKsud) {
                runCatching { ksuCliRepository.install() }.onFailure { error ->
                    Log.w(TAG, "Failed to refresh ksud after a successful kernel flash", error)
                }
            }
            state.updateStep(context.getString(R.string.horizon_flash_complete_status))
            state.completeFlashing()
        } catch (error: Exception) {
            state.setError(
                error.message ?: context.getString(R.string.horizon_unknown_error)
            )
        } finally {
            if (zipFile.exists()) {
                zipFile.delete()
            }
        }
    }

    private fun copyToCache(zipFile: File) {
        zipFile.delete()
        val source = uri
            ?: throw IOException(context.getString(R.string.horizon_copy_failed))
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException(context.getString(R.string.horizon_copy_failed))
        input.use {
            zipFile.outputStream().use { output ->
                it.copyTo(output)
            }
        }
        if (!zipFile.isFile) {
            throw IOException(context.getString(R.string.horizon_copy_failed))
        }
    }

    private fun performKpmPatch(zipFile: File) {
        val workDir = File(context.cacheDir, "kpm")
        workDir.deleteRecursively()
        if (!workDir.mkdirs()) {
            throw IOException(context.getString(R.string.kpm_patch_operation_failed, ""))
        }

        val kptools = File(workDir, "kptools")
        val kpimg = File(workDir, "kpimg")
        AssetsUtil.exportFiles(context, "kptools", kptools.absolutePath)
        AssetsUtil.exportFiles(context, "kpimg", kpimg.absolutePath)
        if (!kptools.isFile || !kpimg.isFile) {
            throw IOException(context.getString(R.string.kpm_patch_operation_failed, ""))
        }
        kptools.setExecutable(true)

        val extractDir = File(workDir, "extracted")
        extractDir.mkdirs()
        unzip(zipFile, extractDir)

        val image = extractDir.walkTopDown()
            .firstOrNull { it.isFile && it.name.contains("Image") }
            ?: throw IOException(context.getString(R.string.kpm_image_file_not_found))

        state.addLog(context.getString(R.string.kpm_found_image_file, image.absolutePath))
        state.updateStep(
            context.getString(
                if (kpmUndoPatch) R.string.kpm_undoing_patch else R.string.kpm_applying_patch
            )
        )

        val flag = if (kpmUndoPatch) "-u" else "-p"
        val output = File(image.parentFile, "oImage")
        val command = buildString {
            append(quote(kptools.absolutePath))
            append(" ").append(flag).append(" -s 123")
            append(" -i ").append(quote(image.name))
            append(" -k ").append(quote(kpimg.absolutePath))
            append(" -o ").append(quote(output.name))
        }
        val exitCode = runInDir(image.parentFile, command)
        if (exitCode != 0 || !output.isFile) {
            throw IOException(
                context.getString(
                    if (kpmUndoPatch) R.string.kpm_undo_patch_failed else R.string.kpm_patch_failed
                )
            )
        }
        if (!output.renameTo(File(image.parentFile, image.name))) {
            output.copyTo(File(image.parentFile, image.name), overwrite = true)
            output.delete()
        }

        state.addLog(
            context.getString(
                if (kpmUndoPatch) R.string.kpm_undo_patch_success else R.string.kpm_patch_success
            )
        )

        val patched = File(workDir, "patched_${zipFile.name}")
        repackZip(extractDir, patched)
        if (!patched.renameTo(zipFile)) {
            patched.copyTo(zipFile, overwrite = true)
            patched.delete()
        }
        state.addLog(context.getString(R.string.kpm_file_repacked))
        workDir.deleteRecursively()
    }

    private fun runInDir(dir: File, command: String): Int {
        val shell = ksuCliRepository.getRootShell()
        val result = shell.newJob()
            .add("cd ${quote(dir.absolutePath)} && $command")
            .to(ArrayList(), ArrayList())
            .exec()
        return result.code
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun repackZip(sourceDir: File, zipFilePath: File) {
        val buffer = ByteArray(8192)
        FileOutputStream(zipFilePath).use { fos ->
            ZipOutputStream(fos).use { zos ->
                sourceDir.walkTopDown().forEach { file ->
                    if (!file.isFile) return@forEach
                    val relative = file.relativeTo(sourceDir).path
                    val entry = ZipEntry(relative)
                    entry.time = file.lastModified()
                    zos.putNextEntry(entry)
                    file.inputStream().use { fis ->
                        var length = fis.read(buffer)
                        while (length > 0) {
                            zos.write(buffer, 0, length)
                            length = fis.read(buffer)
                        }
                    }
                    zos.closeEntry()
                }
            }
        }
    }

    private fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun handleOutput(line: String) {
        Log.i(TAG, line)
        state.addLog(line)

        when {
            line.contains("extracting", ignoreCase = true) -> {
                state.updateProgress(0.75f)
            }

            line.contains("installing", ignoreCase = true) -> {
                state.updateProgress(0.85f)
            }

            line.contains("complete", ignoreCase = true) -> {
                state.updateProgress(0.95f)
            }
        }
    }

    private fun handleConsoleOutput(line: String) {
        Log.i(TAG, line)
        state.addConsoleLog(line)
    }

    private companion object {
        const val TAG = "HorizonKernelWorker"
    }
}
