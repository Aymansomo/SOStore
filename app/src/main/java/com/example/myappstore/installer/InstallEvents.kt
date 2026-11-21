package com.example.myappstore.installer

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class InstallStatus { SUCCESS, FAILURE, PENDING }

/** Result of a PackageInstaller session, delivered by [InstallResultReceiver]. */
data class InstallOutcome(
    val packageName: String,
    val status: InstallStatus,
    val message: String? = null
)

/**
 * Process-wide bus between the manifest receiver (which has no ViewModel reference)
 * and the UI layer. The receiver and the app always live in the same process.
 */
object InstallEventBus {

    private val _events = MutableSharedFlow<InstallOutcome>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<InstallOutcome> = _events.asSharedFlow()

    fun emit(outcome: InstallOutcome) {
        _events.tryEmit(outcome)
    }
}
