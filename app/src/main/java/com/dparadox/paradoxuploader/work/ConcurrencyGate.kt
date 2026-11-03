package com.dparadox.paradoxuploader.work

import com.dparadox.paradoxuploader.domain.model.UploadOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Limits how many uploads run at once (setting: 1-3). The limit is read on every acquire. */
@Singleton
class ConcurrencyGate @Inject constructor() {
    private val active = MutableStateFlow(0)
    private val knownItems = ConcurrentHashMap.newKeySet<String>()
    private val itemLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withSlot(limit: Int, block: suspend () -> T): T {
        while (true) {
            val current = active.value
            if (current < limit && active.compareAndSet(current, current + 1)) break
            active.first { it < limit }
        }
        try {
            return block()
        } finally {
            active.update { it - 1 }
        }
    }

    /**
     * Several files going into one new archive.org item must not all try to create it at the
     * same time. Until one upload to [identifier] has succeeded, uploads to it run one at a time;
     * after that they run in parallel as usual.
     */
    suspend fun withItemCreation(identifier: String, block: suspend () -> UploadOutcome): UploadOutcome {
        if (identifier in knownItems) return block()
        val mutex = itemLocks.getOrPut(identifier) { Mutex() }
        return mutex.withLock {
            val outcome = block()
            if (outcome == UploadOutcome.Done) knownItems.add(identifier)
            outcome
        }
    }
}
