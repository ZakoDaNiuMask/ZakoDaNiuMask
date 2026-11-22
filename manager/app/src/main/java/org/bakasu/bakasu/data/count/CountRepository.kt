package org.bakasu.bakasu.data.count

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.data.shell.KsuCliRepository

data class CountState(
    val superuserCount: Int = 0,
    val moduleCount: Int = 0,
)

class CountRepository(
    private val ksuCliRepository: KsuCliRepository,
) {
    private companion object {
        const val TAG = "CountRepository"
    }

    private val mutableState = MutableStateFlow(CountState())
    val state: StateFlow<CountState> = mutableState.asStateFlow()

    suspend fun refresh() {
        val ksudAvailable = ksuCliRepository.isKsudAvailable()
        val (superuserCount, moduleCount) = withContext(Dispatchers.IO) {
            val superuser = runCatching { ksuCliRepository.getSuperuserCount() }
                .onFailure { Log.w(TAG, "superuser count unavailable", it) }
                .getOrDefault(0)
            val modules = if (ksudAvailable) {
                runCatching { ksuCliRepository.getModuleCount() }
                    .onFailure { Log.w(TAG, "module count unavailable", it) }
                    .getOrDefault(0)
            } else {
                Log.i(TAG, "libksud.so missing; module count defaults to 0")
                0
            }
            superuser to modules
        }
        mutableState.update {
            it.copy(superuserCount = superuserCount, moduleCount = moduleCount)
        }
    }
}
