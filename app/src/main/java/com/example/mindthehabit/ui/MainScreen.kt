package com.example.mindthehabit.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.mindthehabit.data.local.entity.SpendingEntryEntity
import com.example.mindthehabit.ui.dashboard.DashboardScreen
import com.example.mindthehabit.ui.diagnostics.DiagnosticsScreen
import com.example.mindthehabit.ui.expense.AddExpenseScreen
import com.example.mindthehabit.ui.prediction.PredictionScreen
import com.example.mindthehabit.ui.theme.*
import com.example.mindthehabit.ui.timeline.TimelineScreen
import com.example.mindthehabit.ui.viewmodel.MainViewModel

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Timeline : Screen("timeline", "Timeline", Icons.AutoMirrored.Filled.List)
    object Prediction : Screen("prediction", "Predict", Icons.AutoMirrored.Filled.TrendingUp)
    object Settings : Screen("settings", "Diagnostics", Icons.Default.Settings)
    object AddExpense : Screen("addExpense", "Add Expense", Icons.Default.Home)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val navController = rememberNavController()
    var expenseToEdit by remember { mutableStateOf<SpendingEntryEntity?>(null) }

    val items = listOf(
        Screen.Home,
        Screen.Timeline,
        Screen.Prediction,
        Screen.Settings
    )

    Scaffold(
        topBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            
            if (currentRoute != Screen.AddExpense.route) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(style = SpanStyle(color = TextPrimary)) {
                                    append("hA")
                                }
                                withStyle(style = SpanStyle(color = YouthPurple, fontWeight = FontWeight.Bold)) {
                                    append("/")
                                }
                                withStyle(style = SpanStyle(color = TextPrimary)) {
                                    append("Bits")
                                }
                            },
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-1).sp
                            )
                        )
                    },
                    actions = {
                        IconButton(onClick = { /* TODO: Notifications */ }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = TextPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = CreamBackground
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = YouthPurple,
                            selectedTextColor = YouthPurple,
                            unselectedIconColor = Color.LightGray,
                            unselectedTextColor = Color.LightGray,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController,
            startDestination = Screen.Home.route,
            Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                DashboardScreen(
                    viewModel = viewModel,
                    onAddExpense = {
                        expenseToEdit = null
                        navController.navigate(Screen.AddExpense.route)
                    }
                )
            }
            composable(Screen.Timeline.route) {
                TimelineScreen(
                    viewModel = viewModel,
                    onEditExpense = { expense ->
                        expenseToEdit = expense
                        navController.navigate(Screen.AddExpense.route)
                    }
                )
            }
            composable(Screen.Prediction.route) { PredictionScreen(viewModel) }
            composable(Screen.Settings.route) { DiagnosticsScreen(viewModel) }
            composable(Screen.AddExpense.route) {
                AddExpenseScreen(
                    viewModel = viewModel,
                    existingExpense = expenseToEdit,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
