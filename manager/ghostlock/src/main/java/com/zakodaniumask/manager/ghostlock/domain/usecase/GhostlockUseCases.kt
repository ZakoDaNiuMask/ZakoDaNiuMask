package com.zakodaniumask.manager.ghostlock.domain.usecase

import com.zakodaniumask.manager.ghostlock.domain.model.CpuPair
import com.zakodaniumask.manager.ghostlock.domain.repository.GhostlockRepository

class LoadKernelSnapshotUseCase(private val repository: GhostlockRepository) {
    suspend operator fun invoke() = repository.snapshot()
}

class SelectCpuPairUseCase(private val repository: GhostlockRepository) {
    operator fun invoke(index: Int) = repository.selectCpuPair(index)
}

class ImportOffsetsUseCase(private val repository: GhostlockRepository) {
    suspend operator fun invoke(documents: Map<String, String>) = repository.importOffsets(documents)
    suspend fun overwrite(documents: Map<String, String>) = repository.confirmImport(documents)
}

class ParseSourceUseCase(private val repository: GhostlockRepository) {
    suspend operator fun invoke(
        input: String,
        xblPath: String? = null,
        uefiPath: String? = null,
        overwrite: Boolean = false,
        onLog: (String) -> Unit = {},
    ) = repository.parseSource(input, xblPath, uefiPath, overwrite, onLog)
}

class RunExploitUseCase(private val repository: GhostlockRepository) {
    suspend operator fun invoke(pair: CpuPair, useRootless: Boolean, onLog: (String) -> Unit) =
        if (useRootless) repository.runExploitRootless(pair, onLog)
        else repository.runExploit(pair, onLog)
}

class ReadDocumentUseCase(private val repository: GhostlockRepository) {
    suspend operator fun invoke(uri: String) = repository.readDocument(uri)
    suspend fun cache(uri: String, fileName: String) = repository.cacheDocument(uri, fileName)
}
