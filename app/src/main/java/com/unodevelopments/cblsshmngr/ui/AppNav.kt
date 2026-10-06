package com.unodevelopments.cblsshmngr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.unodevelopments.cblsshmngr.CabalApp
import com.unodevelopments.cblsshmngr.ui.about.AboutScreen
import com.unodevelopments.cblsshmngr.ui.connection.ConnectionFormScreen
import com.unodevelopments.cblsshmngr.ui.database.DatabaseFormScreen
import com.unodevelopments.cblsshmngr.ui.database.DatabaseHomeScreen
import com.unodevelopments.cblsshmngr.ui.database.DatabaseSessionScreen
import com.unodevelopments.cblsshmngr.ui.home.HomeScreen
import com.unodevelopments.cblsshmngr.ui.launcher.LauncherScreen
import com.unodevelopments.cblsshmngr.ui.session.SessionScreen
import com.unodevelopments.cblsshmngr.ui.setup.FirstRunScreen
import com.unodevelopments.cblsshmngr.ui.setup.TermsScreen
import com.unodevelopments.cblsshmngr.ui.theme.ThemeScreen

@Composable
fun AppNav() {
    val app = LocalContext.current.applicationContext as CabalApp
    val nav = rememberNavController()
    val start = remember { if (app.themeStore.onboardingDone.value) "launcher" else "setup" }
    NavHost(navController = nav, startDestination = start) {
        composable("setup") {
            FirstRunScreen(
                onFinished = {
                    nav.navigate("launcher") {
                        popUpTo("setup") { inclusive = true }
                    }
                },
            )
        }
        composable("launcher") {
            LauncherScreen(
                onSsh = { nav.navigate("home") },
                onDatabase = { nav.navigate("databases") },
                onThemes = { nav.navigate("themes") },
                onInfo = { nav.navigate("about") },
            )
        }
        composable("about") {
            AboutScreen(
                onBack = { nav.popBackStack() },
                onTerms = { nav.navigate("terms") },
            )
        }
        composable("terms") {
            TermsScreen(onBack = { nav.popBackStack() })
        }
        composable("themes") {
            ThemeScreen(onBack = { nav.popBackStack() })
        }
        composable("home") {
            HomeScreen(
                onBack = { nav.popBackStack() },
                onAdd = { nav.navigate("connection/new") },
                onEdit = { id -> nav.navigate("connection/$id/edit") },
                onConnect = { id -> nav.navigate("session/$id") },
            )
        }
        composable("connection/new") {
            ConnectionFormScreen(connectionId = null, onDone = { nav.popBackStack() })
        }
        composable(
            route = "connection/{id}/edit",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            ConnectionFormScreen(
                connectionId = entry.arguments?.getLong("id"),
                onDone = { nav.popBackStack() },
            )
        }
        composable(
            route = "session/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            SessionScreen(
                connectionId = entry.arguments?.getLong("id") ?: 0L,
                onDone = { nav.popBackStack() },
            )
        }
        composable("databases") {
            DatabaseHomeScreen(
                onBack = { nav.popBackStack() },
                onAdd = { nav.navigate("database/new") },
                onEdit = { id -> nav.navigate("database/$id/edit") },
                onConnect = { id -> nav.navigate("database/session/$id") },
            )
        }
        composable("database/new") {
            DatabaseFormScreen(connectionId = null, onDone = { nav.popBackStack() })
        }
        composable(
            route = "database/{id}/edit",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            DatabaseFormScreen(
                connectionId = entry.arguments?.getLong("id"),
                onDone = { nav.popBackStack() },
            )
        }
        composable(
            route = "database/session/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            DatabaseSessionScreen(
                connectionId = entry.arguments?.getLong("id") ?: 0L,
                onDone = { nav.popBackStack() },
            )
        }
    }
}
