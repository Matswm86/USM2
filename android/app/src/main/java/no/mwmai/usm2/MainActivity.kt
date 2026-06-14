package no.mwmai.usm2

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import no.mwmai.usm2.engine.CareerFactory
import no.mwmai.usm2.ui.CareerScreen
import no.mwmai.usm2.ui.ClubScreen
import no.mwmai.usm2.ui.ErrorScreen
import no.mwmai.usm2.ui.FixturesScreen
import no.mwmai.usm2.ui.GroupScreen
import no.mwmai.usm2.ui.GroupsScreen
import no.mwmai.usm2.ui.LoadingScreen
import no.mwmai.usm2.ui.OfficeScreen
import no.mwmai.usm2.ui.PlayerScreen
import no.mwmai.usm2.ui.TableScreen
import no.mwmai.usm2.ui.TransfersScreen

private val UsmColors = darkColorScheme(
    primary = Color(0xFF4CAF7D),
    background = Color(0xFF06281A),
    surface = Color(0xFF0E3320),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = UsmColors) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun App(vm: GameViewModel = viewModel()) {
    when (val s = vm.state.collectAsStateWithLifecycle().value) {
        is LoadState.Loading -> LoadingScreen()
        is LoadState.Failed -> ErrorScreen(s.message)
        is LoadState.Ready -> Nav(s.data, vm)
    }
}

@Composable
private fun Nav(data: GameData, vm: GameViewModel) {
    val nav = rememberNavController()
    val career by vm.career.collectAsStateWithLifecycle()
    NavHost(nav, startDestination = "office") {
        composable("office") {
            OfficeScreen(
                data,
                careerClub = career?.let { data.clubsById[it.managedClubId]?.name },
                onCareer = { nav.navigate("career") },
                onSquads = { nav.navigate("groups") },
                onTransfers = { nav.navigate("transfers") },
            )
        }
        composable("groups") {
            GroupsScreen(data, onGroup = { nav.navigate("group/${Uri.encode(it)}") }, onBack = { nav.popBackStack() })
        }
        composable(
            "group/{group}",
            arguments = listOf(navArgument("group") { type = NavType.StringType }),
        ) { entry ->
            val group = Uri.decode(entry.arguments?.getString("group").orEmpty())
            GroupScreen(
                data,
                group = group,
                onClub = { nav.navigate("club/${Uri.encode(it)}") },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "club/{clubId}",
            arguments = listOf(navArgument("clubId") { type = NavType.StringType }),
        ) { entry ->
            val clubId = Uri.decode(entry.arguments?.getString("clubId").orEmpty())
            val manageable = data.clubsById[clubId]?.let { CareerFactory.isManageable(data, it) } ?: false
            ClubScreen(
                data,
                clubId = clubId,
                manageable = manageable,
                isManaged = career?.managedClubId == clubId,
                onTakeCharge = {
                    if (career?.managedClubId != clubId) vm.startCareer(clubId)
                    nav.navigate("career") { popUpTo("office") }
                },
                onPlayer = { nav.navigate("player/$it") },
                onBack = { nav.popBackStack() },
            )
        }
        composable("career") {
            val c = career
            if (c == null) {
                LaunchedEffect(Unit) { nav.popBackStack("office", inclusive = false) }
            } else {
                CareerScreen(
                    data,
                    c,
                    onPlayRound = { vm.playNextRound() },
                    onTable = { nav.navigate("table") },
                    onFixtures = { nav.navigate("fixtures") },
                    onSquad = { nav.navigate("club/${Uri.encode(it)}") },
                    onQuit = {
                        vm.quitCareer()
                        nav.popBackStack("office", inclusive = false)
                    },
                    onBack = { nav.popBackStack() },
                )
            }
        }
        composable("table") {
            val c = career
            if (c == null) LaunchedEffect(Unit) { nav.popBackStack("office", inclusive = false) }
            else TableScreen(data, c, onBack = { nav.popBackStack() })
        }
        composable("fixtures") {
            val c = career
            if (c == null) LaunchedEffect(Unit) { nav.popBackStack("office", inclusive = false) }
            else FixturesScreen(data, c, onBack = { nav.popBackStack() })
        }
        composable("transfers") {
            TransfersScreen(data, onPlayer = { nav.navigate("player/$it") }, onBack = { nav.popBackStack() })
        }
        composable(
            "player/{idx}",
            arguments = listOf(navArgument("idx") { type = NavType.IntType }),
        ) { entry ->
            PlayerScreen(data, idx = entry.arguments?.getInt("idx") ?: -1, onBack = { nav.popBackStack() })
        }
    }
}
