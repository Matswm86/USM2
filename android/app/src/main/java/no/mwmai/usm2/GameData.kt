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
import no.mwmai.usm2.engine.Career
import no.mwmai.usm2.engine.CareerFactory
import no.mwmai.usm2.engine.TransferLimits
import no.mwmai.usm2.engine.Valuation
import java.io.File

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
        // The 18 real formations (base shape) decoded from FORM.DAT: each is 11
        // [x, y] pairs normalised to [0,1] on the pitch (GK ~bottom, attack ~top).
        val formations = runCatching {
            app.assets.open("data/formations.json").use {
                json.decodeFromString<List<List<List<Double>>>>(it.readBytes().decodeToString())
            }
        }.getOrDefault(emptyList())
        // The playable-pitch trapezoid for the match view (img/match/pitch.png).
        val pitchQuad = runCatching {
            app.assets.open("data/pitch_quad.json").use {
                json.decodeFromString<PitchQuad>(it.readBytes().decodeToString())
            }
        }.getOrDefault(PitchQuad.DEFAULT)
        return GameData(clubs, players, formations, pitchQuad)
    }
}

/** Persists the in-progress career to the app's private storage as JSON. Uses
 * the explicit-serializer [Json] member forms (no reified-extension import). */
object CareerStore {
    private val json = Json { ignoreUnknownKeys = true }
    private fun file(app: Application) = File(app.filesDir, "career.json")

    fun load(app: Application): Career? = runCatching {
        file(app).takeIf { it.exists() }?.readText()?.let {
            json.decodeFromString(Career.serializer(), it)
        }
    }.getOrNull()

    fun save(app: Application, career: Career) {
        runCatching { file(app).writeText(json.encodeToString(Career.serializer(), career)) }
    }

    fun clear(app: Application) {
        runCatching { file(app).delete() }
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

    private val _career = MutableStateFlow<Career?>(null)
    /** The active management career, or null when none is in progress. */
    val career: StateFlow<Career?> = _career.asStateFlow()

    init {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val loaded = withContext(Dispatchers.IO) {
                val data = runCatching { GameRepository.load(app) }
                val saved = data.getOrNull()?.let { CareerStore.load(app) }
                data to saved
            }
            _state.value = loaded.first
                .fold({ LoadState.Ready(it) }, { LoadState.Failed(it.message ?: "load error") })
            _career.value = loaded.second
        }
    }

    private fun readyData(): GameData? = (_state.value as? LoadState.Ready)?.data

    /** Begins a new career managing [clubId]; replaces any existing one. */
    fun startCareer(clubId: String) {
        val data = readyData() ?: return
        val seed = System.nanoTime()
        val career = CareerFactory.start(data, clubId, seed) ?: return
        _career.value = career
        persist(career)
    }

    /** Simulates the next matchday across the whole division. */
    fun playNextRound() {
        val current = _career.value ?: return
        if (current.seasonComplete) return
        val advanced = current.playNextRound()
        _career.value = advanced
        persist(advanced)
    }

    /** Promotes/relegates across the pyramid and starts the next season. */
    fun rolloverSeason() {
        val current = _career.value ?: return
        if (!current.seasonComplete) return
        val next = current.rolloverSeason()
        _career.value = next
        persist(next)
    }

    /** Signs a player into the managed club if affordable and the squad has room. */
    fun signPlayer(playerIndex: Int) {
        val data = readyData() ?: return
        val c = _career.value ?: return
        val p = data.players.getOrNull(playerIndex) ?: return
        if (c.currentClubOf(playerIndex, p.club) == c.managedClubId) return
        if (c.managedSquad(data).size >= TransferLimits.MAX_SQUAD) return
        val fee = Valuation.buyPriceK(p)
        if (c.budget < fee) return
        val next = c.signPlayer(data, playerIndex, fee)
        _career.value = next
        persist(next)
    }

    /** Sells a player out of the managed club, keeping the squad above its floor. */
    fun sellPlayer(playerIndex: Int) {
        val data = readyData() ?: return
        val c = _career.value ?: return
        val p = data.players.getOrNull(playerIndex) ?: return
        if (c.currentClubOf(playerIndex, p.club) != c.managedClubId) return
        if (c.managedSquad(data).size <= TransferLimits.MIN_SQUAD) return
        val fee = Valuation.sellPriceK(p)
        val next = c.sellPlayer(data, playerIndex, fee)
        _career.value = next
        persist(next)
    }

    /** Commits a manual starting XI (global player indices); empty reverts to auto. */
    fun setLineup(xi: List<Int>) {
        val data = readyData() ?: return
        val c = _career.value ?: return
        val next = c.setXI(data, xi)
        _career.value = next
        persist(next)
    }

    /** Abandons the current career and deletes the save. */
    fun quitCareer() {
        _career.value = null
        CareerStore.clear(getApplication())
    }

    private fun persist(career: Career) {
        viewModelScope.launch(Dispatchers.IO) { CareerStore.save(getApplication(), career) }
    }
}
