
package com.eljaviluki.grindrplus

import android.content.Intent
import com.eljaviluki.grindrplus.Hooker.Companion.sharedPref
import com.eljaviluki.grindrplus.Hooks.chatMessageManager
import com.eljaviluki.grindrplus.Hooks.ownProfileId
import com.eljaviluki.grindrplus.decorated.persistence.model.ChatMessage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.callMethod
import de.robv.android.xposed.XposedHelpers.findClass
import java.io.File
import java.io.IOException

import java.text.SimpleDateFormat
import java.util.*

object Utils {
    fun toReadableDate(timestamp: Long): String = SimpleDateFormat.getDateTimeInstance().format(Date(timestamp))

    /**
     * Open a profile by its ID.
     * Based on yukkerike's work.
     *
     * @param id The profile ID.
     */
    fun openProfile(id: String) {
        val generalDeepLinksClass = findClass("com.grindrapp.android.deeplink.GeneralDeepLinks", Hooker.pkgParam.classLoader)
        val profilesActivityClass = findClass("com.grindrapp.android.ui.profileV2.ProfilesActivity", Hooker.pkgParam.classLoader)
        val profilesActivityInstance = profilesActivityClass.getField("u0").get(null)
        val referrerTypeClass = findClass("com.grindrapp.android.base.model.profile.ReferrerType", Hooker.pkgParam.classLoader)
        val referrerType = referrerTypeClass.getField("NOTIFICATION").get(null)
        val profilesActivityInnerClass = findClass("com.grindrapp.android.ui.profileV2.ProfilesActivity\$a", Hooker.pkgParam.classLoader)

        var intent: Intent? = null
        for (method in profilesActivityInnerClass.declaredMethods) {
            if (method.parameterTypes.size == 3 && method.parameterTypes[2] == referrerTypeClass) {
                intent = method.invoke(
                    profilesActivityInstance,
                    Hooker.appContext, id, referrerType
                ) as Intent
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                break
            }
        }

        if (intent != null) {
            for (method in generalDeepLinksClass.declaredMethods) {
                if (method.name == "safedk_Context_startActivity_97cb3195734cf5c9cc3418feeafa6dd6") {
                    method.invoke(null, Hooker.appContext, intent)
                    return
                }
            }
        }
    }

    /**
     * Logs a chat message.
     *
     * @param text The message text.
     * @param from The profile ID of the sender.
     * @param sender The profile ID of the sender. If null, the own profile ID is used.
     */
    fun logChatMessage(text: String, from: String, sender: String? = null) {
        val chatMessage = ChatMessage()
        chatMessage.messageId = UUID.randomUUID().toString()
        chatMessage.sender = sender ?: ownProfileId ?: return
        chatMessage.recipient = from
        chatMessage.stanzaId = from
        chatMessage.conversationId = from
        chatMessage.timestamp = System.currentTimeMillis()
        chatMessage.type = "text"
        chatMessage.body = text

        callMethod(
            chatMessageManager,
            Obfuscation.GApp.xmpp.ChatMessageManager_.handleIncomingChatMessage,
            chatMessage.instance,
            false,
            false
        )
    }

    /**
     * Returns whether the profile redesign is enabled.
     */
    fun isProfileRedesignEnabled(): Boolean {
        val value = sharedPref.getString("profile_redesign", "true") ?: "true"
        return value.toBoolean()
    }

    /**
     * Updates the profile redesign value.
     */
    fun updateProfileRedesign(value: Boolean) {
        sharedPref.edit().putString("profile_redesign", value.toString()).apply()
    }
}