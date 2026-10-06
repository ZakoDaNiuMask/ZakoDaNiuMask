package com.zakodaniumask.manager.data.webui

import com.zakodaniumask.manager.axeron.AxClient
import com.zakodaniumask.manager.data.privilege.PrivilegeManager
import com.zakodaniumask.manager.data.shell.KsuCliRepository
import com.zakodaniumask.manager.domain.model.PrivBackend
import com.zakodaniumask.manager.domain.model.WebUiCommandResult
import com.zakodaniumask.manager.domain.model.WebUiModuleInfo
import com.zakodaniumask.manager.domain.model.WebUiProcess
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.internal.UiThreadHandler
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileInputStream
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.runBlocking

class WebUiRepository(
    private val ksuCliRepository: KsuCliRepository,
    private val privilegeManager: PrivilegeManager,
) {
    fun execute(command: String, globalMnt: Boolean = true): WebUiCommandResult {
        val result = runBlocking { privilegeManager.exec(command, globalMnt) }
        return WebUiCommandResult(
            code = result.code,
            stdout = result.stdout,
            stderr = result.stderr,
        )
    }

    fun spawn(command: String, globalMnt: Boolean = true): WebUiProcess =
        when (privilegeManager.current()) {
            PrivBackend.ROOT -> ShellWebUiProcess(ksuCliRepository.createRootShell(globalMnt), command)
            PrivBackend.ROOTLESS -> RootlessWebUiProcess(command)
            PrivBackend.NONE -> UnavailableWebUiProcess()
        }

    fun listModules(): String = ksuCliRepository.listModules()

    fun getModuleInfo(moduleId: String): WebUiModuleInfo? =
        ksuCliRepository.withNewRootShell(globalMnt = true) {
            val rootShell = this
            readWebUiModuleInfo(
                moduleId = moduleId,
                resolveFile = { path -> SuFile(path).apply { shell = rootShell } },
                openFile = { file -> SuFileInputStream.open(file) },
            )
        }

    fun openFile(path: String) = runCatching {
        val file = SuFile(path).apply { shell = ksuCliRepository.createRootShell(true) }
        SuFileInputStream.open(file)
    }.getOrNull()

    private class ShellWebUiProcess(
        private val shell: Shell,
        private val command: String,
    ) : WebUiProcess {
        override fun start(
            onStdout: (String) -> Unit,
            onStderr: (String) -> Unit,
            onComplete: (WebUiCommandResult) -> Unit,
        ) {
            try {
                val stdout = object : CallbackList<String>(UiThreadHandler::runAndWait) {
                    override fun onAddElement(s: String) = onStdout(s)
                }
                val stderr = object : CallbackList<String>(UiThreadHandler::runAndWait) {
                    override fun onAddElement(s: String) = onStderr(s)
                }
                val future = shell.newJob().add(command).to(stdout, stderr).enqueue()
                CompletableFuture.supplyAsync { future.get() }
                    .thenAccept { result ->
                        onComplete(
                            WebUiCommandResult(
                                code = result.code,
                                stdout = result.out.joinToString("\n"),
                                stderr = result.err.joinToString("\n"),
                            )
                        )
                    }
                    .whenComplete { _, _ -> close() }
            } catch (error: Throwable) {
                onComplete(WebUiCommandResult(-1, "", error.message.orEmpty()))
                close()
            }
        }

        override fun close() {
            runCatching { shell.close() }
        }
    }

    private class RootlessWebUiProcess(
        private val command: String,
    ) : WebUiProcess {
        override fun start(
            onStdout: (String) -> Unit,
            onStderr: (String) -> Unit,
            onComplete: (WebUiCommandResult) -> Unit,
        ) {
            try {
                val output = AxClient.exec(command)
                if (output != null) onStdout(output)
                onComplete(
                    WebUiCommandResult(
                        code = if (output != null) 0 else -1,
                        stdout = output.orEmpty(),
                        stderr = "",
                    )
                )
            } catch (error: Throwable) {
                onComplete(WebUiCommandResult(-1, "", error.message.orEmpty()))
            }
        }

        override fun close() = Unit
    }

    private class UnavailableWebUiProcess : WebUiProcess {
        override fun start(
            onStdout: (String) -> Unit,
            onStderr: (String) -> Unit,
            onComplete: (WebUiCommandResult) -> Unit,
        ) {
            onComplete(
                WebUiCommandResult(
                    code = -1,
                    stdout = "",
                    stderr = "No privileged backend available",
                )
            )
        }

        override fun close() = Unit
    }
}
