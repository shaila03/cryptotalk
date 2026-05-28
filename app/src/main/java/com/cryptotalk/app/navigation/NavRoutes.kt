package com.cryptotalk.app.navigation

/**
 * NavRoutes is simply a list of all the different screens in the app.
 * It's used to easily travel between screens without hardcoding the names everywhere.
 */
object NavRoutes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val CONVERSATION_LIST = "conversation_list"
    const val CHAT = "chat/{conversationId}"
    const val NEW_CONVERSATION = "new_conversation"
    const val SETTINGS = "settings"
    const val SECURITY_DASHBOARD = "security_dashboard"
    const val PRIVACY_POLICY = "privacy_policy"
    const val PANIC_PIN_SETUP = "panic_pin_setup"
    const val APP_DISGUISE = "app_disguise"
    const val GROUP_CHAT = "group_chat/{groupId}"
    const val GROUP_INFO = "group_info/{groupId}"

    fun chat(conversationId: String) = "chat/$conversationId"
    fun groupChat(groupId: String) = "group_chat/$groupId"
    fun groupInfo(groupId: String) = "group_info/$groupId"
}
