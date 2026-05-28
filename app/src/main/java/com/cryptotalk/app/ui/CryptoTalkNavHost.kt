package com.cryptotalk.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cryptotalk.app.CryptoTalkApplication
import com.cryptotalk.app.ui.chat.ChatScreen
import com.cryptotalk.app.ui.chat.ChatViewModel
import com.cryptotalk.app.ui.conversationlist.ConversationListScreen
import com.cryptotalk.app.ui.conversationlist.ConversationListViewModel
import com.cryptotalk.app.ui.login.LoginScreen
import com.cryptotalk.app.ui.login.LoginViewModel
import com.cryptotalk.app.ui.newconversation.NewConversationScreen
import com.cryptotalk.app.ui.newconversation.NewConversationViewModel
import com.cryptotalk.app.ui.settings.PrivacyPolicyScreen
import com.cryptotalk.app.ui.settings.SettingsScreen
import com.cryptotalk.app.ui.settings.SettingsViewModel
import com.cryptotalk.app.ui.register.RegisterScreen
import com.cryptotalk.app.ui.register.RegisterViewModel
import com.cryptotalk.app.ui.splash.SplashScreen
import com.cryptotalk.app.ui.splash.SplashViewModel
import com.cryptotalk.app.ui.security.SecurityDashboardScreen
import com.cryptotalk.app.ui.disguise.DisguiseSettingsScreen
import com.cryptotalk.app.ui.disguise.DisguiseSettingsViewModel
import com.cryptotalk.app.ui.panic.PanicViewModel
import com.cryptotalk.app.ui.panic.PanicPinScreen
import com.cryptotalk.app.ui.chat.GroupChatViewModel
import com.cryptotalk.app.ui.chat.GroupChatScreen
import com.cryptotalk.app.ui.chat.GroupInfoViewModel
import com.cryptotalk.app.ui.chat.GroupInfoScreen
import com.cryptotalk.app.navigation.NavRoutes

@Composable
fun CryptoTalkNavHost(
    app: CryptoTalkApplication,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.SPLASH
    ) {
        composable(NavRoutes.SPLASH) {
            val viewModel: SplashViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return SplashViewModel(app.authRepository, app.userRepository) as T
                    }
                }
            )
            SplashScreen(viewModel = viewModel) { route ->
                navController.navigate(route) { popUpTo(NavRoutes.SPLASH) { inclusive = true } }
            }
        }

        composable(NavRoutes.LOGIN) {
            val viewModel: LoginViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return LoginViewModel(app.authRepository, app.userRepository) as T
                    }
                }
            )
            LoginScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    navController.navigate(route) { popUpTo(NavRoutes.LOGIN) { inclusive = true } }
                }
            )
        }

        composable(NavRoutes.REGISTER) {
            val viewModel: RegisterViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return RegisterViewModel(app.authRepository, app.userRepository) as T
                    }
                }
            )
            RegisterScreen(
                viewModel = viewModel,
                onNavigate = { route ->
                    navController.navigate(route) { popUpTo(NavRoutes.REGISTER) { inclusive = true } }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.CONVERSATION_LIST) {
            val viewModel: ConversationListViewModel = viewModel(
                factory = ConversationListViewModel.Factory(
                    app.authRepository,
                    app.conversationRepository,
                    app.userRepository,
                    app.settingsRepository,
                    app.applicationContext
                )
            )
            ConversationListScreen(
                viewModel = viewModel,
                onOpenChat = { id, isGroup ->
                    if (isGroup) {
                        navController.navigate(NavRoutes.groupChat(id))
                    } else {
                        navController.navigate(NavRoutes.chat(id))
                    }
                },
                onNewConversation = { navController.navigate(NavRoutes.NEW_CONVERSATION) },
                onSettings = { navController.navigate(NavRoutes.SETTINGS) }
            )
        }

        composable(NavRoutes.NEW_CONVERSATION) {
            val viewModel: NewConversationViewModel = viewModel(
                factory = NewConversationViewModel.Factory(
                    app.authRepository,
                    app.conversationRepository,
                    app.userRepository,
                    app.groupRepository
                )
            )
            NewConversationScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onConversationStarted = { id ->
                    navController.navigate(NavRoutes.chat(id)) {
                        popUpTo(NavRoutes.NEW_CONVERSATION) { inclusive = true }
                    }
                },
                onGroupStarted = { id ->
                    navController.navigate(NavRoutes.groupChat(id)) {
                        popUpTo(NavRoutes.NEW_CONVERSATION) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = NavRoutes.CHAT,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: return@composable
            val viewModel: ChatViewModel = viewModel(
                factory = ChatViewModel.Factory(
                    conversationId,
                    app.authRepository,
                    app.conversationRepository,
                    app.messageRepository,
                    app.userRepository,
                    app.settingsRepository,
                    app.applicationContext
                )
            )
            val chatState by viewModel.state.collectAsState()
            ChatScreen(
                viewModel = viewModel,
                otherUserName = chatState.otherUserName,
                onBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(app.settingsRepository, app.userRepository, app.authRepository)
            )
            val user = app.authRepository.currentUser
            var userName by remember { mutableStateOf("") }
            
            LaunchedEffect(user?.uid) {
                user?.uid?.let { uid ->
                    app.userRepository.getUser(uid)?.displayName?.let { name ->
                        userName = name
                    }
                }
            }

            SettingsScreen(
                viewModel = viewModel,
                userEmail = user?.email ?: "",
                userName = userName,
                onBack = { navController.popBackStack() },
                onNavigateToSecurity = { navController.navigate(NavRoutes.SECURITY_DASHBOARD) },
                onPrivacyPolicy = { navController.navigate(NavRoutes.PRIVACY_POLICY) },
                onSignOut = {
                    app.authRepository.signOut()
                    navController.navigate(NavRoutes.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            )
        }

        composable(NavRoutes.PANIC_PIN_SETUP) {
            val viewModel: PanicViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return PanicViewModel(app.settingsRepository) as T
                    }
                }
            )
            PanicPinScreen(navController, viewModel, isSetup = true)
        }

        composable(NavRoutes.SECURITY_DASHBOARD) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(app.settingsRepository, app.userRepository, app.authRepository)
            )
            SecurityDashboardScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToDisguise = { navController.navigate(NavRoutes.APP_DISGUISE) }
            )
        }

        composable(NavRoutes.PRIVACY_POLICY) {
            PrivacyPolicyScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.APP_DISGUISE) {
            val viewModel: DisguiseSettingsViewModel = viewModel(
                factory = DisguiseSettingsViewModel.Factory(app.settingsRepository)
            )
            DisguiseSettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.GROUP_CHAT) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val viewModel: GroupChatViewModel = viewModel(
                factory = GroupChatViewModel.Factory(
                    groupId,
                    app.authRepository,
                    app.groupRepository,
                    app.userRepository
                )
            )
            GroupChatScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateToSettings = { id -> navController.navigate(NavRoutes.groupInfo(id)) }
            )
        }

        composable(NavRoutes.GROUP_INFO) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val viewModel: GroupInfoViewModel = viewModel(
                factory = GroupInfoViewModel.Factory(
                    groupId,
                    app.authRepository,
                    app.groupRepository,
                    app.userRepository
                )
            )
            GroupInfoScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
