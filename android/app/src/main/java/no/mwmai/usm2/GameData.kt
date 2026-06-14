package no.mwmai.usm2

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Reads the staged JSON bundle out of the APK's assets directory. */
object GameRepository {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(app: Application): GameData {
        val clubs = app.assets.open("data/clubs.json").use {
            json.decodeFromString<List<Club>>(it.readBytes().decodeToString())
        }
        val players = app.assets.open("data/players.json").use {
            json.decodeFromString<List<Player>>(it.readBytes().decodeToString())
        }
        return GameData(clubs, players)
    }
}

sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val data: GameData) : LoadState
    data class Failed(val message: String) : LoadState
}

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<LoadState>(LoadState.Loading)
    val state: StateFlow<LoadState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) {
                runCatching { GameRepository.load(getApplication()) }
                    .fold({ LoadState.Ready(it) }, { LoadState.Failed(it.message ?: "load error") })
            }
        }
    }
}
