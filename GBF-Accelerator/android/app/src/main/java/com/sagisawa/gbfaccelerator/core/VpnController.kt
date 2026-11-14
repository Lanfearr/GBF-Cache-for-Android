package com.sagisawa.gbfaccelerator.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

object VpnController {
    enum class State {
        STOPPED,
        STARTING,
        RUNNING,
        ERROR
    }

    @Volatile
    var state: State = State.STOPPED
        private set

    @Volatile
    var lastError: String? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(State, String?) -> Unit>()

    fun addListener(listener: (State, String?) -> Unit) {
        listeners.add(listener)
        mainHandler.post { listener(state, lastError) }
    }

    fun removeListener(listener: (State, String?) -> Unit) {
        listeners.remove(listener)
    }

    fun update(newState: State, error: String? = null) {
        state = newState
        lastError = error
        mainHandler.post {
            for (listener in listeners) {
                listener(newState, error)
            }
        }
    }
}
