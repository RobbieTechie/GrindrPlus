package com.eljaviluki.grindrplus

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.Window
import android.view.WindowManager
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.children
import com.eljaviluki.grindrplus.Constants.Returns.RETURN_FALSE
import com.eljaviluki.grindrplus.Constants.Returns.RETURN_INTEGER_MAX_VALUE
import com.eljaviluki.grindrplus.Constants.Returns.RETURN_LONG_MAX_VALUE
import com.eljaviluki.grindrplus.Constants.Returns.RETURN_TRUE
import com.eljaviluki.grindrplus.Constants.Returns.RETURN_ZERO
import com.eljaviluki.grindrplus.Constants.Returns.RETURN_UNIT
import com.eljaviluki.grindrplus.Constants.Returns.RETURN_ONE
import com.eljaviluki.grindrplus.Obfuscation.GApp
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge.hookMethod
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers.*
import java.io.File
import java.lang.reflect.Proxy
import java.util.*
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.roundToInt
import kotlin.time.Duration

object Hooks {

    var ownProfileId: String? = null
    var chatMessageManager: Any? = null
    /**
     * Allow screenshots in all the views of the application (including expiring photos, albums, etc.)
     *
     * Inspired in the project https://github.com/veeti/DisableFlagSecure
     * Credit and thanks to @veeti!
     */
    fun preventUpdates() {
        findAndHookMethod(
            "com.google.android.play.core.appupdate.AppUpdateInfo",
            Hooker.pkgParam.classLoader,
            "updateAvailability",
            RETURN_ONE
        )

        findAndHookConstructor(
            "com.grindrapp.android.base.config.AppConfiguration",
            Hooker.pkgParam.classLoader,
            findClass("com.grindrapp.android.base.config.AppConfiguration.b", Hooker.pkgParam.classLoader),
            findClass("com.grindrapp.android.base.config.AppConfiguration.f", Hooker.pkgParam.classLoader),
            findClass("com.grindrapp.android.base.config.AppConfiguration.d", Hooker.pkgParam.classLoader),
            findClass("com.grindrapp.android.base.config.AppConfiguration.e", Hooker.pkgParam.classLoader),
            findClass("com.grindrapp.android.base.config.AppConfiguration.c", Hooker.pkgParam.classLoader),
            findClass("com.grindrapp.android.base.config.AppConfiguration.a", Hooker.pkgParam.classLoader),
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    setObjectField(param.thisObject, "a", "9.18.1")
                    setObjectField(param.thisObject, "b", 119580 )
                    setObjectField(param.thisObject, "u", "9.18.1.119580")
                }
            }
        )
    }

    fun allowScreenshotsHook() {
        findAndHookMethod(
            Window::class.java,
            "setFlags",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    var flags = param.args[0] as Int
                    flags = flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                    param.args[0] = flags
                }
            })
    }

    /**
     * Add extra profile fields with more information:
     * - Profile ID
     * - Last seen (exact date and time)
     */
    fun addExtraProfileFields() {
        val class_ProfileFieldsView = findClass(
            GApp.ui.profileV2.ProfileFieldsView,
            Hooker.pkgParam.classLoader
        )

        val class_Profile = findClass(
            GApp.persistence.model.Profile,
            Hooker.pkgParam.classLoader
        )

        val class_ExtendedProfileFieldView = findClass(
            GApp.view.ExtendedProfileFieldView,
            Hooker.pkgParam.classLoader
        )

        val class_R_color = findClass(
            GApp.R.color,
            Hooker.pkgParam.classLoader
        )

        val class_Continuation = findClass(
            "kotlin.coroutines.Continuation",
            Hooker.pkgParam.classLoader
        ) //I tried using Continuation::class.java, but that only gives a different Class instance (does not work)


        val class_Intrinsics = findClass(
            "kotlin.jvm.internal.Intrinsics",
            Hooker.pkgParam.classLoader
        )

        val checkNotNullParameterMethod = findMethodExact(
            class_Intrinsics,
            "checkNotNullParameter",
            Object::class.java,
            String::class.java
        )

        findAndHookMethod(
            class_ProfileFieldsView,
            GApp.ui.profileV2.ProfileFieldsView_.setProfile,
            GApp.ui.profileV2.model.Profile,
            object : XC_MethodHook() {
                var fieldsViewInstance: Any? = null
                val context: Any? by lazy {
                    callMethod(
                        fieldsViewInstance,
                        "getContext"
                    )
                }

                val labelColorRgb = ContextCompat.getColor(
                    Hooker.appContext!!,
                    getStaticIntField(
                        class_R_color,

                        //Original color for vanilla labels: grindr_gray_2
                        //to differentiate a normal field from a special one, the name of the special one will be golden.
                        GApp.R.color_.grindr_gold_star_gay
                    )
                )

                val valueColorId = getStaticIntField(
                    class_R_color,
                    GApp.R.color_.grindr_pure_white
                ) //R.color.grindr_pure_white

                override fun afterHookedMethod(param: MethodHookParam) {
                        fieldsViewInstance = param.thisObject

                        val profileId = callMethod(
                            param.args[0],
                            GApp.ui.profileV2.model.Profile_.getProfileId
                        ) as String

                        param.args[0]?.let {
                        //val profile = Profile(it)
                        addProfileFieldUi("Profile ID", profileId, 0).also { view ->
                            view.setOnLongClickListener {
                                val clipboard =
                                    Hooker.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Profile ID", profileId)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(
                                    Hooker.appContext,
                                    "Profile ID copied to clipboard",
                                    Toast.LENGTH_SHORT
                                ).show()
                                true
                            }
                        }

                        /*addProfileFieldUi(
                            "Last Seen",
                            if (profile.seen != 0L) Utils.toReadableDate(profile.seen) else "N/A",
                            1
                        )

                        if (profile.weight != 0.0 && profile.height != 0.0)
                            addProfileFieldUi(
                                "Body Mass Index",
                                Utils.getBmiDescription(profile.weight, profile.height),
                                2
                            )*/
                    }

                    //.setVisibility() of param.thisObject to always VISIBLE (otherwise if the profile has no fields, the additional ones will not be shown)
                    callMethod(fieldsViewInstance, "setVisibility", View.VISIBLE)
                }

                //By default, the views are added to the end of the list.
                private fun addProfileFieldUi(
                    label: CharSequence,
                    value: CharSequence,
                    where: Int = -1
                ): FrameLayout {
                    val hooked = XposedBridge.hookMethod(
                        checkNotNullParameterMethod,
                        XC_MethodReplacement.DO_NOTHING
                    )
                    val extendedProfileFieldView =
                        newInstance(class_ExtendedProfileFieldView, context, null as AttributeSet?)
                    hooked.unhook()

                    callMethod(
                        extendedProfileFieldView,
                        GApp.view.ExtendedProfileFieldView_.setLabel,
                        label,
                        labelColorRgb
                    )

                    callMethod(
                        extendedProfileFieldView,
                        GApp.view.ExtendedProfileFieldView_.setValue,
                        value,
                        valueColorId
                    )

                    //From View.setContentDescription(...)
                    callMethod(
                        extendedProfileFieldView,
                        "setContentDescription",
                        value
                    )

                    //(ProfileFieldsView).addView(Landroid/view/View;)V
                    callMethod(
                        fieldsViewInstance,
                        "addView",
                        extendedProfileFieldView,
                        where
                    )

                    return extendedProfileFieldView as FrameLayout
                }
            })
    }

    /**
     * Hook these methods in all the classes that implement IUserSession.
     * isFree()Z (return false)
     * isNoXtraUpsell()Z (return false)
     * isXtra()Z to give Xtra account features.
     * isUnlimited()Z to give Unlimited account features.
     */
    fun hookUserSessionImpl() {
        val class_Feature = findClass(
            GApp.model.Feature,
            Hooker.pkgParam.classLoader
        )

        listOf(
            findClass(
                GApp.storage.UserSession,
                Hooker.pkgParam.classLoader
            ),

            /*findClass(
                GApp.storage.UserSession2,
                Hooker.pkgParam.classLoader
            )*/
        ).forEach { userSessionImpl ->
            findAndHookMethod(
                userSessionImpl,
                GApp.storage.IUserSession_.hasFeature_feature,
                class_Feature,
                object :  XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        // For whatever reason, enabling this feature causes the "Send Video"
                        // button to disappear. This does not seem to affect screenshots so we
                        // just disable this feature.
                        if (param.args[0].toString() == "DisableScreenshot") {
                            return false
                        }
                        if (param.args[0].toString() == "Incognito") {
                            return false
                        }
                        return true
                    }
                }
            )

            findAndHookMethod(
                userSessionImpl,
                GApp.storage.IUserSession_.isFree,
                RETURN_FALSE
            )

            findAndHookMethod(
                userSessionImpl,
                GApp.storage.IUserSession_.isNoXtraUpsell,
                RETURN_TRUE
            ) //Not sure what is this for

            findAndHookMethod(
                userSessionImpl,
                GApp.storage.IUserSession_.isXtra,
                RETURN_FALSE
            )

            findAndHookMethod(
                userSessionImpl,
                GApp.storage.IUserSession_.isPlus,
                RETURN_FALSE
            )

            findAndHookMethod(
                userSessionImpl,
                GApp.storage.IUserSession_.isNoPlusUpsell,
                RETURN_TRUE
            )

            findAndHookMethod(
                userSessionImpl,
                GApp.storage.IUserSession_.isUnlimited,
                RETURN_TRUE
            )
        }
    }

    fun unlimitedExpiringPhotos() {
        val class_ExpiringPhotoStatusResponse = findClass(
            GApp.model.ExpiringPhotoStatusResponse,
            Hooker.pkgParam.classLoader
        )

        findAndHookMethod(
            class_ExpiringPhotoStatusResponse,
            GApp.model.ExpiringPhotoStatusResponse_.getTotal,
            RETURN_INTEGER_MAX_VALUE
        )

        findAndHookMethod(
            class_ExpiringPhotoStatusResponse,
            GApp.model.ExpiringPhotoStatusResponse_.getAvailable,
            RETURN_INTEGER_MAX_VALUE
        )
    }

    /**
     * Grant all the Grindr features (except disabling screenshots).
     * A few more changes may be needed to use all the features.
     */
    fun hookFeatureGranting() {
        val class_Feature = findClass(
            GApp.model.Feature,
            Hooker.pkgParam.classLoader
        )

        findAndHookMethod(
            class_Feature,
            GApp.model.Feature_.isGranted,
            RETURN_TRUE
        )

        /*val class_IUserSession = findClass(
            GApp.storage.IUserSession,
            Hooker.pkgParam.classLoader
        )

        findAndHookMethod(
            class_Feature,
            GApp.model.Feature_.isGranted,
            class_IUserSession,
            RETURN_TRUE
        )*/

        findAndHookMethod(
            "u5.g",
            Hooker.pkgParam.classLoader,
            "isEnabled",
            object :  XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any {
                    val feature = getObjectField(param.thisObject, "featureFlagName") as String
                    return when (feature) {
                        "profile-redesign-20230214" -> false
                        "offer" -> true
                        "notification-action-chat-20230206" -> true
                        "gender-updates" -> true
                        "gender-exclusion" -> true
                        "favorite-profile-notes-server" -> false
                        "verbose-ad-analytics" -> false
                        "calendar-ui" -> true
                        "vaccine-profile-field" -> true
                        "download-my-data" -> true
                        "upgrade-prompt-interval" -> false
                        "custom-dns" -> true
                        "cookie-tap" -> false
                        "takemehome-button" -> true
                        "canceled-screen" -> true
                        "tag-search" -> true
                        "approximate-distance" -> true
                        "store-default-product" -> false
                        "spectrum_solicitation_sex" -> true
                        "allow-mock-location" -> true
                        "spectrum-solicitation-of-drugs" -> true
                        "sift-kill-switch" -> true
                        "side-profile-link" -> true
                        "ads-quality-edu" -> false
                        "ad-identifier" -> false
                        "reporting-lag-time" -> true
                        "intro-offer-free-trial-20221222" -> true
                        "gender-filter" -> true
                        "chat-interstitial" -> false
                        "ad-backfill" -> false
                        "face-auth-android" -> true
                        else -> XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                    }
                }
            }
        )

        //Once you uncheck "precise", the option will disappear normally (not sure if that's a bug). This fix prevents that.
        findAndHookConstructor(
            "com.grindrapp.android.ui.settings.distance.SettingDistanceVisibilityViewModel\$e",
            Hooker.pkgParam.classLoader,
            Int::class.java,
            Boolean::class.java,
            Boolean::class.java,
            Boolean::class.java,
            Boolean::class.java,
            Set::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    param?.args?.set(4, false)
                }
            }
        )

        val class_UpsellsV8 = findClass(
            GApp.model.UpsellsV8,
            Hooker.pkgParam.classLoader
        )

        findAndHookMethod(
            class_UpsellsV8,
            GApp.model.UpsellsV8_.getMpuFree,
            RETURN_INTEGER_MAX_VALUE
        )

        findAndHookMethod(
            class_UpsellsV8,
            GApp.model.UpsellsV8_.getMpuXtra,
            RETURN_ZERO
        )

        val class_Inserts = findClass(
            GApp.model.Inserts,
            Hooker.pkgParam.classLoader
        )

        findAndHookMethod(
            class_Inserts,
            GApp.model.Inserts_.getMpuFree,
            RETURN_INTEGER_MAX_VALUE
        )

        findAndHookMethod(
            class_Inserts,
            GApp.model.Inserts_.getMpuXtra,
            RETURN_ZERO
        )
    }

    fun unlimitedProfiles() {
        //Enforce usage of InaccessibleProfileManager...
        findAndHookMethod(
            "com.grindrapp.android.profile.experiments.InaccessibleProfileManager",
            Hooker.pkgParam.classLoader,
            "a",
            RETURN_TRUE
        )

        /*//...and then just never ask for upsells
        findAndHookMethod(
            "com.grindrapp.android.profile.experiments.InaccessibleProfileManager",
            Hooker.pkgParam.classLoader,
            "b",
            Int::class.javaPrimitiveType,
            Int::class.javaObjectType,
            Int::class.javaObjectType,
            GApp.storage.IUserSession,
            "com.grindrapp.android.base.model.profile.ReferrerType",
            RETURN_FALSE
        )*/

        //Remove all ads and upsells from the cascade
        findAndHookMethod(
            "com.grindrapp.android.persistence.model.serverdrivencascade.ServerDrivenCascadeCacheState",
            Hooker.pkgParam.classLoader,
            "getItems",
            object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any {
                    val items = XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args) as List<*>
                    return items.filterNotNull().filter {
                        it.javaClass.name == "com.grindrapp.android.persistence.model.serverdrivencascade.ServerDrivenCascadeCachedProfile"
                    }
                }

            }
        )
    }

    /**
     * Allow to use SOME (not all of them) hidden features that Grindr developers have not yet made public
     * or they are just testing.
     */
    fun allowSomeExperiments() {
        val class_Experiments = findClass(
            GApp.experiment.Experiments,
            Hooker.pkgParam.classLoader
        )

        val class_IExperimentsManager = findClass(
            GApp.base.Experiment.IExperimentsManager,
            Hooker.pkgParam.classLoader
        )

        findAndHookMethod(
            class_Experiments,
            GApp.experiment.Experiments_.uncheckedIsEnabled_expMgr,
            class_IExperimentsManager,
            RETURN_TRUE
        )
    }

    /**
     * Allow videocalls on empty chats: Grindr checks that both users have chatted with each other
     * (both must have sent at least one message to the other) in order to allow videocalls.
     *
     * This hook allows the user to bypass this restriction.
     */
    fun allowVideocallsOnEmptyChats() {
        val class_Continuation = findClass(
            "kotlin.coroutines.Continuation",
            Hooker.pkgParam.classLoader
        ) //I tried using Continuation::class.java, but that only gives a different Class instance (does not work)

        val class_ChatRepo = findClass(
            GApp.persistence.repository.ChatRepo,
            Hooker.pkgParam.classLoader
        )

        findAndHookMethod(
            class_ChatRepo,
            GApp.persistence.repository.ChatRepo_.checkMessageForVideoCall,
            String::class.java,
            class_Continuation,
            RETURN_TRUE
        )
    }

    /**
     * Allow Fake GPS in order to fake location.
     *
     * WARNING: Abusing this feature may result in a permanent ban on your Grindr account.
     */
    fun allowMockProvider() {
        val class_Location = findClass(
            "android.location.Location",
            Hooker.pkgParam.classLoader
        )

        findAndHookMethod(
            class_Location,
            "isFromMockProvider",
            RETURN_FALSE
        )

        findAndHookMethod(
            class_Location,
            "getLatitude",
            object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any {
                    val locationFile = File(Hooker.appContext.filesDir, "location.txt")
                    if (!locationFile.exists()) {
                        locationFile.createNewFile()
                    }
                    val content = locationFile.readText()
                    val result = regex.find(content)
                   return if (result == null) {
                        XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                    } else {
                        result.groups[1]!!.value.toDouble()
                    }
                }
            }
        )

        findAndHookMethod(
            class_Location,
            "getLongitude",
            object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any {
                    val locationFile = File(Hooker.appContext.filesDir, "location.txt")
                    if (!locationFile.exists()) {
                        locationFile.createNewFile()
                    }
                    val content = locationFile.readText()
                    val result = regex.find(content)
                    return if (result == null) {
                        XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                    } else {
                        result.groups[2]!!.value.toDouble()
                    }
                }
            }
        )

        if (Build.VERSION.SDK_INT >= 31) {
            findAndHookMethod(
                class_Location,
                "isMock",
                RETURN_FALSE
            )
        }
    }


    /**
     * Hook online indicator duration:
     *
     * "After closing the app, the profile remains online for 10 minutes. It is misleading. People think that you are rude for not answering, when in reality you are not online."
     *
     * Now, you can limit the Online indicator (green dot) for a custom duration.
     *
     * Inspired in the suggestion made at:
     * https://grindr.uservoice.com/forums/912631-grindr-feedback/suggestions/34555780-more-accurate-online-status-go-offline-when-clos
     *
     * @param duration Duration in milliseconds.
     *
     * @see Duration
     * @see Duration.inWholeMilliseconds
     *
     * @author ElJaviLuki
     */
    fun hookOnlineIndicatorDuration(duration: Duration) {
        val class_ProfileUtils = findClass(GApp.utils.ProfileUtils, Hooker.pkgParam.classLoader)
        setStaticLongField(
            class_ProfileUtils,
            GApp.utils.ProfileUtils_.onlineIndicatorDuration,
            duration.inWholeMilliseconds
        )
    }

    /**
     * Allow unlimited taps on profiles.
     *
     * @author ElJaviLuki
     */
    fun unlimitedTaps() {
        val class_TapsAnimLayout = findClass(GApp.view.TapsAnimLayout, Hooker.pkgParam.classLoader)
        val class_ChatMessage =
            findClass(GApp.persistence.model.ChatMessage, Hooker.pkgParam.classLoader)

        val tapTypeToHook = getStaticObjectField(
            class_ChatMessage,
            GApp.persistence.model.ChatMessage_.TAP_TYPE_NONE
        )

        //Reset the tap value to allow multitapping.
        findAndHookMethod(
            class_TapsAnimLayout,
            GApp.view.TapsAnimLayout_.setTapType,
            String::class.java,
            Boolean::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    setObjectField(
                        param.thisObject,
                        GApp.view.TapsAnimLayout_.tapType,
                        tapTypeToHook
                    )
                }
            }
        )

        //Reset taps on long press (allows using tap variants)
        findAndHookMethod(
            class_TapsAnimLayout,
            GApp.view.TapsAnimLayout_.getCanSelectVariants,
            RETURN_TRUE
        )

        findAndHookMethod(
            class_TapsAnimLayout,
            GApp.view.TapsAnimLayout_.getDisableVariantSelection,
            RETURN_FALSE
        )

        findAndHookMethod(
            "com.grindrapp.android.ui.profileV2.ProfileQuickbarView",
            Hooker.pkgParam.classLoader,
            "u",
            Boolean::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.args[0] = true
                }
            }
        )
    }

    /**
     * Hook the method that returns the duration of the expiring photos.
     * This way, the photos will not expire and you will be able to see them any time you want.
     *
     * @author ElJaviLuki
     */
    fun removeExpirationOnExpiringPhotos() {
        val class_ExpiringImageBody =
            findClass(GApp.model.ExpiringImageBody, Hooker.pkgParam.classLoader)
        findAndHookMethod(
            class_ExpiringImageBody,
            GApp.model.ExpiringImageBody_.getDuration,
            RETURN_LONG_MAX_VALUE
        )
    }

    fun preventRecordProfileViews() {

        val ProfileRestServiceClass = findClass(
            GApp.api.ProfileRestService, Hooker.pkgParam.classLoader)

        val createSuccessResultConstructor = findConstructorExact(
            "j7.a\$b", Hooker.pkgParam.classLoader, Any::class.java)

        findAndHookMethod(
            "retrofit2.Retrofit",
            Hooker.pkgParam.classLoader,
            "create",
            Class::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val service = param.result
                    param.result = when {
                        ProfileRestServiceClass.isInstance(service) -> {
                            val invocationHandler = Proxy.getInvocationHandler(service)
                            Proxy.newProxyInstance(Hooker.pkgParam.classLoader,
                                arrayOf(ProfileRestServiceClass)) { proxy, method, args ->
                                if (method.name in arrayOf(GApp.api.ProfileRestService_.logView, GApp.api.ProfileRestService_.logViews)) {
                                    createSuccessResultConstructor.newInstance(Unit)
                                } else {
                                    invocationHandler.invoke(proxy, method, args)
                                }
                            }
                        }
                        else -> service
                    }
                }
            }

        )

        findAndHookMethod(
            GApp.persistence.repository.ProfileRepo,
            Hooker.pkgParam.classLoader,
            GApp.persistence.repository.ProfileRepo_.recordProfileView,
            String::class.java,
            "kotlin.coroutines.Continuation",
            RETURN_UNIT
        )
    }

    fun makeMessagesAlwaysRemovable() {
        val class_ChatBaseFragmentV2 = findClass(
            GApp.ui.chat.ChatBaseFragmentV2,
            Hooker.pkgParam.classLoader
        )

        val class_ChatMessage =
            findClass(GApp.persistence.model.ChatMessage, Hooker.pkgParam.classLoader)
        findAndHookMethod(
            class_ChatBaseFragmentV2,
            GApp.ui.chat.ChatBaseFragmentV2_._canBeUnsent,
            class_ChatMessage,
            RETURN_FALSE
        )
    }

    /*
    fun notifyBlockStatusViaToast() {
        val class_BlockedByHelper = findClass(
            GApp.persistence.cache.BlockedByHelper,
            Hooker.pkgParam.classLoader
        )

        val class_Continuation = findClass(
            "kotlin.coroutines.Continuation",
            Hooker.pkgParam.classLoader
        )

        findAndHookMethod(class_BlockedByHelper, GApp.persistence.cache.BlockByHelper_.addBlockByProfile, String::class.java, class_Continuation, object : XC_MethodHook(){
            override fun beforeHookedMethod(param: MethodHookParam?) {
                val profileId: String = param!!.args[0] as String
                ContextCompat.getMainExecutor(Hooker.appContext).execute {
                    Toast.makeText(Hooker.appContext, "Profile [ID: $profileId] has blocked your profile.", Toast.LENGTH_LONG).show()
                }
            }
        })

        findAndHookMethod(class_BlockedByHelper, GApp.persistence.cache.BlockByHelper_.removeBlockByProfile, String::class.java, class_Continuation, object : XC_MethodHook(){
            override fun beforeHookedMethod(param: MethodHookParam?) {
                val profileId: String = param!!.args[0] as String
                ContextCompat.getMainExecutor(Hooker.appContext).execute {
                    Toast.makeText(Hooker.appContext, "Profile [ID: $profileId] has unblocked your profile.", Toast.LENGTH_LONG).show()
                }
            }
        })
    }
    */

    fun storeChatMessageManager() {
        XposedBridge.hookAllConstructors(
            findClass(
                GApp.xmpp.ChatMessageManager,
                Hooker.pkgParam.classLoader
            ),
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    chatMessageManager = param.thisObject
                }
            }
        )
    }

    fun showBlocksInChat() {
        val receiveChatMessage = findMethodExact(
            GApp.xmpp.ChatMessageManager,
            Hooker.pkgParam.classLoader,
            GApp.xmpp.ChatMessageManager_.handleIncomingChatMessage,
            GApp.persistence.model.ChatMessage,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
        )

        XposedBridge.hookMethod(receiveChatMessage,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val chatMessage = param.args[0]
                    val type = callMethod(
                        chatMessage,
                        GApp.persistence.model.ChatMessage_.getType
                    ) as String
                    val syntheticMessage = when (type) {
                        "block" -> "[You have been blocked this profile]"
                        "unblock" -> "[You have been unblocked.]"
                        else -> null
                    }
                    if (syntheticMessage != null) {
                        val clone =
                            callMethod(chatMessage, GApp.persistence.model.ChatMessage_.clone)
                        callMethod(clone, GApp.persistence.model.ChatMessage_.setType, "text")
                        callMethod(
                            clone,
                            GApp.persistence.model.ChatMessage_.setBody,
                            syntheticMessage
                        )
                        receiveChatMessage.invoke(
                            param.thisObject,
                            clone,
                            param.args[1],
                            param.args[2]
                        )
                    }
                }
            })


        val Constructor_ChatMessage = findConstructorExact(
            GApp.persistence.model.ChatMessage,
            Hooker.pkgParam.classLoader
        )

        findAndHookMethod(
            GApp.storage.UserSession,
            Hooker.pkgParam.classLoader,
            GApp.storage.IUserSession_.getProfileId,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    ownProfileId = param.result as String
                }
            }
        )

        fun logChatMessage(from: String, text: String) {
            val chatMessage = Constructor_ChatMessage.newInstance()
            callMethod(
                chatMessage,
                GApp.persistence.model.ChatMessage_.setMessageId,
                UUID.randomUUID().toString()
            )
            callMethod(chatMessage, GApp.persistence.model.ChatMessage_.setSender, ownProfileId)
            callMethod(chatMessage, GApp.persistence.model.ChatMessage_.setRecipient, from)
            callMethod(chatMessage, GApp.persistence.model.ChatMessage_.setStanzaId, from)
            callMethod(chatMessage, GApp.persistence.model.ChatMessage_.setConversationId, from)
            callMethod(
                chatMessage,
                GApp.persistence.model.ChatMessage_.setTimestamp,
                System.currentTimeMillis()
            )
            callMethod(chatMessage, GApp.persistence.model.ChatMessage_.setType, "text")
            callMethod(chatMessage, GApp.persistence.model.ChatMessage_.setBody, text)
            callMethod(
                chatMessageManager,
                GApp.xmpp.ChatMessageManager_.handleIncomingChatMessage,
                chatMessage,
                false,
                false
            )
        }

        findAndHookMethod(
            GApp.persistence.repository.BlockRepo,
            Hooker.pkgParam.classLoader,
            GApp.persistence.repository.BlockRepo_.add,
            GApp.persistence.model.BlockedProfile,
            "kotlin.coroutines.Continuation",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val otherProfileId = callMethod(
                        param.args[0],
                        GApp.persistence.model.BlockedProfile_.getProfileId
                    ) as String
                    logChatMessage(otherProfileId, "[You have blocked this profile.]")
                }
            }
        )

        findAndHookMethod(
            GApp.persistence.repository.BlockRepo,
            Hooker.pkgParam.classLoader,
            GApp.persistence.repository.BlockRepo_.delete,
            String::class.java,
            "kotlin.coroutines.Continuation",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val otherProfileId = param.args[0] as? String
                    if (otherProfileId != null) {
                        logChatMessage(otherProfileId, "[You have unblocked this profile.]")
                    }
                }
            }
        )
    }

    fun keepChatsOfBlockedProfiles() {
        val ignoreIfBlockInteractor = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                // We still want to allow deleting chats etc.,
                // so only ignore if BlockInteractor is calling
                val isBlockInteractor =
                    Thread.currentThread().stackTrace.any { stackTraceElement ->
                        GApp.manager.BlockInteractor.any {
                            stackTraceElement.className.contains(it)
                        } || stackTraceElement.className.contains(GApp.ui.chat.BlockViewModel)
                    }
                if (isBlockInteractor) {
                    param.result = null
                }
            }
        }

        findAndHookMethod(
            GApp.persistence.repository.ProfileRepo,
            Hooker.pkgParam.classLoader,
            GApp.persistence.repository.ProfileRepo_.delete,
            String::class.java,
            "kotlin.coroutines.Continuation",
            ignoreIfBlockInteractor
        )

        findAndHookMethod(
            GApp.persistence.repository.ProfileRepo,
            Hooker.pkgParam.classLoader,
            GApp.persistence.repository.ProfileRepo_.delete,
            List::class.java,
            "kotlin.coroutines.Continuation",
            ignoreIfBlockInteractor
        )

        findAndHookMethod(
            GApp.manager.persistence.ChatPersistenceManager,
            Hooker.pkgParam.classLoader,
            GApp.manager.persistence.ChatPersistenceManager_.deleteConversationsByProfileIds,
            List::class.java,
            "kotlin.coroutines.Continuation",
            ignoreIfBlockInteractor
        )

        // We just remove the "AND blocks.profileId is NULL" part to allow blocked profiles
        val queries = mapOf(
            "\n" +
                    "        SELECT * FROM conversation \n" +
                    "        LEFT JOIN blocks ON blocks.profileId = conversation_id\n" +
                    "        LEFT JOIN banned ON banned.profileId = conversation_id\n" +
                    "        WHERE blocks.profileId is NULL AND banned.profileId is NULL\n" +
                    "        ORDER BY conversation.pin DESC, conversation.last_message_timestamp DESC, conversation.conversation_id DESC\n" +
                    "        "
                    to "\n" +
                    "        SELECT * FROM conversation \n" +
                    "        LEFT JOIN blocks ON blocks.profileId = conversation_id\n" +
                    "        LEFT JOIN banned ON banned.profileId = conversation_id\n" +
                    "        WHERE banned.profileId is NULL\n" +
                    "        ORDER BY conversation.pin DESC, conversation.last_message_timestamp DESC, conversation.conversation_id DESC\n" +
                    "        ",
            "\n" +
                    "        SELECT * FROM conversation\n" +
                    "        LEFT JOIN profile ON profile.profile_id = conversation.conversation_id\n" +
                    "        LEFT JOIN blocks ON blocks.profileId = conversation_id\n" +
                    "        LEFT JOIN banned ON banned.profileId = conversation_id\n" +
                    "        WHERE blocks.profileId is NULL AND banned.profileId is NULL AND unread >= :minUnreadCount AND is_group_chat in (:isGroupChat)\n" +
                    "            AND (:minLastSeen = 0 OR seen > :minLastSeen)\n" +
                    "            AND (1 IN (:isFavorite) AND 0 IN (:isFavorite) OR is_favorite in (:isFavorite))\n" +
                    "        ORDER BY conversation.pin DESC, conversation.last_message_timestamp DESC, conversation.conversation_id DESC\n" +
                    "        "
                    to "\n" +
                    "        SELECT * FROM conversation\n" +
                    "        LEFT JOIN profile ON profile.profile_id = conversation.conversation_id\n" +
                    "        LEFT JOIN blocks ON blocks.profileId = conversation_id\n" +
                    "        LEFT JOIN banned ON banned.profileId = conversation_id\n" +
                    "        WHERE banned.profileId is NULL AND unread >= :minUnreadCount AND is_group_chat in (:isGroupChat)\n" +
                    "            AND (:minLastSeen = 0 OR seen > :minLastSeen)\n" +
                    "            AND (1 IN (:isFavorite) AND 0 IN (:isFavorite) OR is_favorite in (:isFavorite))\n" +
                    "        ORDER BY conversation.pin DESC, conversation.last_message_timestamp DESC, conversation.conversation_id DESC\n" +
                    "        "
        )

        findAndHookMethod("androidx.room.RoomSQLiteQuery",
            Hooker.pkgParam.classLoader,
            "acquire",
            String::class.java,
            Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val query = param.args[0]
                    param.args[0] = queries.getOrDefault(query, query)
                }
            })
    }

    fun localSavedPhrases() {
        val class_ChatRestService =
            findClass(GApp.api.ChatRestService, Hooker.pkgParam.classLoader)

        val class_PhrasesRestService =
            findClass(GApp.api.PhrasesRestService, Hooker.pkgParam.classLoader)

        val createSuccessResult = findConstructorExact(
            "j7.a\$b", Hooker.pkgParam.classLoader, Any::class.java)

        val constructor_AddSavedPhraseResponse = findConstructorExact(
            GApp.model.AddSavedPhraseResponse,
            Hooker.pkgParam.classLoader,
            String::class.java
        )

        val constructor_PhrasesResponse = findConstructorExact(
            GApp.model.PhrasesResponse,
            Hooker.pkgParam.classLoader,
            Map::class.java
        )

        val constructor_Phrase = findConstructorExact(
            GApp.persistence.model.Phrase,
            Hooker.pkgParam.classLoader,
            String::class.java,
            String::class.java,
            Long::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )

        fun hookChatRestService(service: Any): Any {
            val invocationHandler = Proxy.getInvocationHandler(service)
            return Proxy.newProxyInstance(
                Hooker.pkgParam.classLoader,
                arrayOf(class_ChatRestService)
            ) { proxy, method, args ->
                when (method.name) {
                    GApp.api.ChatRestService_.addSavedPhrase -> {
                        val phrase =
                            getObjectField(args[0], "phrase") as String
                        val id = Hooker.sharedPref.getInt("id_counter", 0) + 1
                        val currentPhrases =
                            Hooker.sharedPref.getStringSet("phrases", emptySet())!!
                        Hooker.sharedPref.edit()
                            .putInt("id_counter", id)
                            .putStringSet("phrases", currentPhrases + id.toString())
                            .putString("phrase_${id}_text", phrase)
                            .putInt("phrase_${id}_frequency", 0)
                            .putLong("phrase_${id}_timestamp", 0)
                            .apply()
                        val response =
                            constructor_AddSavedPhraseResponse.newInstance(id.toString())
                        createSuccessResult.newInstance(response)
                    }
                    GApp.api.ChatRestService_.deleteSavedPhrase -> {
                        val id = args[0] as String
                        val currentPhrases =
                            Hooker.sharedPref.getStringSet("phrases", emptySet())!!
                        Hooker.sharedPref.edit()
                            .putStringSet("phrases", currentPhrases - id)
                            .remove("phrase_${id}_text")
                            .remove("phrase_${id}_frequency")
                            .remove("phrase_${id}_timestamp")
                            .apply()
                        createSuccessResult.newInstance(Unit)
                    }
                    GApp.api.ChatRestService_.increaseSavedPhraseClickCount -> {
                        val id = args[0] as String
                        val currentFrequency =
                            Hooker.sharedPref.getInt("phrase_${id}_frequency", 0)
                        Hooker.sharedPref.edit()
                            .putInt("phrase_${id}_frequency", currentFrequency + 1)
                            .apply()
                        createSuccessResult.newInstance(Unit)
                    }
                    else -> invocationHandler.invoke(proxy, method, args)
                }
            }
        }

        fun hookPhrasesRestService(service: Any): Any {
            val invocationHandler = Proxy.getInvocationHandler(service)
            return Proxy.newProxyInstance(
                Hooker.pkgParam.classLoader,
                arrayOf(class_PhrasesRestService)
            ) { proxy, method, args ->
                when (method.name) {
                    GApp.api.PhrasesRestService_.getSavedPhrases -> {
                        val phrases =
                            Hooker.sharedPref.getStringSet("phrases", emptySet())!!
                                .map { id ->
                                    val text = Hooker.sharedPref.getString(
                                        "phrase_${id}_text",
                                        ""
                                    )
                                    val timestamp = Hooker.sharedPref.getLong(
                                        "phrase_${id}_timestamp",
                                        0
                                    )
                                    val frequency = Hooker.sharedPref.getInt(
                                        "phrase_${id}_frequency",
                                        0
                                    )
                                    id to constructor_Phrase.newInstance(
                                        id,
                                        text,
                                        timestamp,
                                        frequency
                                    )
                                }
                                .toMap()
                        val phrasesResponse =
                            constructor_PhrasesResponse.newInstance(phrases)
                        createSuccessResult.newInstance(phrasesResponse)
                    }
                    else -> invocationHandler.invoke(proxy, method, args)
                }
            }
        }

        findAndHookMethod(
            "retrofit2.Retrofit",
            Hooker.pkgParam.classLoader,
            "create",
            Class::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val service = param.result
                    param.result = when {
                        class_ChatRestService.isInstance(service) -> hookChatRestService(service)
                        class_PhrasesRestService.isInstance(service) -> hookPhrasesRestService(
                            service
                        )
                        else -> service
                    }
                }
            }
        )
    }

    fun disableAnalytics() {
        val class_AnalyticsRestService =
            findClass(GApp.api.AnalyticsRestService, Hooker.pkgParam.classLoader)

        val constructor_createSuccessResult = findConstructorExact(
            "j7.a\$b",
            Hooker.pkgParam.classLoader,
            Any::class.java
        )

        findAndHookMethod(
            "retrofit2.Retrofit",
            Hooker.pkgParam.classLoader,
            "create",
            Class::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val service = param.result
                    param.result = when {
                        class_AnalyticsRestService.isInstance(service) -> {
                            Proxy.newProxyInstance(
                                Hooker.pkgParam.classLoader,
                                arrayOf(class_AnalyticsRestService)
                            ) { proxy, method, args ->
                                //Just block all methods for now,
                                //in the future we might need to differentiate if they change the service interface.
                                constructor_createSuccessResult.newInstance(Unit)
                            }
                        }
                        else -> service
                    }
                }
            }
        )
    }

    fun useThreeColumnLayoutForFavorites() {
        val recyclerViewId = Hooker.appContext.resources.getIdentifier(
            "fragment_favorite_recycler_view",
            "id",
            Hooker.pkgParam.packageName
        )

        val profileDistanceId = Hooker.appContext.resources.getIdentifier(
            "profile_distance",
            "id",
            Hooker.pkgParam.packageName
        )

        val profileOnlineNowIconId = Hooker.appContext.resources.getIdentifier(
            "profile_online_now_icon",
            "id",
            Hooker.pkgParam.packageName
        )

        val profileLastSeenId = Hooker.appContext.resources.getIdentifier(
            "profile_last_seen",
            "id",
            Hooker.pkgParam.packageName
        )

        val profileNoteIconId = Hooker.appContext.resources.getIdentifier(
            "profile_note_icon",
            "id",
            Hooker.pkgParam.packageName
        )

        val profileDisplayNameId = Hooker.appContext.resources.getIdentifier(
            "profile_display_name",
            "id",
            Hooker.pkgParam.packageName
        )

        val Constructor_LayoutParamsRecyclerView = findConstructorExact(
            "androidx.recyclerview.widget.RecyclerView\$LayoutParams",
            Hooker.pkgParam.classLoader,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )

        findAndHookMethod(
            GApp.favorites.FavoritesFragment,
            Hooker.pkgParam.classLoader,
            "onViewCreated",
            View::class.java,
            Bundle::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.args[0] as View
                    val recyclerView = view.findViewById<View>(recyclerViewId)
                    val gridLayoutManager = callMethod(recyclerView, "getLayoutManager")
                    val NUMBER_OF_COLS = 3
                    callMethod(gridLayoutManager, "setSpanCount", NUMBER_OF_COLS)

                    val adapter = callMethod(recyclerView, "getAdapter")

                    findAndHookMethod(
                        adapter::class.java,
                        "onBindViewHolder",
                        "androidx.recyclerview.widget.RecyclerView\$ViewHolder",
                        Int::class.javaPrimitiveType,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                //Adjust grid item size
                                val size =
                                    Hooker.appContext.resources.displayMetrics.widthPixels / NUMBER_OF_COLS
                                val rootLayoutParams =
                                    Constructor_LayoutParamsRecyclerView.newInstance(
                                        size,
                                        size
                                    ) as LayoutParams

                                val viewHolder = param.args[0]
                                val itemView = getObjectField(viewHolder, "itemView") as View

                                itemView.layoutParams = rootLayoutParams
                                val distanceTextView =
                                    itemView.findViewById<TextView>(profileDistanceId)

                                //Make online status and distance appear below each other
                                //because theres not enough space anymore to show them in a single row
                                val linearLayout = distanceTextView.parent as LinearLayout
                                linearLayout.orientation = LinearLayout.VERTICAL

                                //Adjust layout params because of different orientation of LinearLayout
                                linearLayout.children.forEach { child ->
                                    child.layoutParams = LinearLayout.LayoutParams(
                                        LayoutParams.MATCH_PARENT,
                                        LayoutParams.WRAP_CONTENT
                                    )
                                }

                                //Align distance TextView left now that it's displayed in its own row
                                distanceTextView.gravity = Gravity.START

                                //Remove ugly margin before last seen text when online indicator is invisible

                                val profileOnlineNowIcon =
                                    itemView.findViewById<ImageView>(profileOnlineNowIconId)
                                val profileLastSeen =
                                    itemView.findViewById<TextView>(profileLastSeenId)
                                val lastSeenLayoutParams =
                                    profileLastSeen.layoutParams as LinearLayout.LayoutParams
                                if (profileOnlineNowIcon.visibility == View.GONE) {
                                    lastSeenLayoutParams.marginStart = 0
                                } else {
                                    lastSeenLayoutParams.marginStart = TypedValue.applyDimension(
                                        TypedValue.COMPLEX_UNIT_DIP,
                                        5f,
                                        profileLastSeen.resources.displayMetrics
                                    ).roundToInt()
                                }
                                profileLastSeen.layoutParams = lastSeenLayoutParams

                                //Remove ugly margin before display name when note icon is invisible

                                val profileNoteIcon =
                                    itemView.findViewById<ImageView>(profileNoteIconId)
                                val profileDisplayName =
                                    itemView.findViewById<TextView>(profileDisplayNameId)
                                val displayNameLayoutParams =
                                    profileDisplayName.layoutParams as LinearLayout.LayoutParams
                                if (profileNoteIcon.visibility == View.GONE) {
                                    displayNameLayoutParams.marginStart = 0
                                } else {
                                    displayNameLayoutParams.marginStart = TypedValue.applyDimension(
                                        TypedValue.COMPLEX_UNIT_DIP,
                                        4f,
                                        profileLastSeen.resources.displayMetrics
                                    ).roundToInt()
                                }
                                profileDisplayName.layoutParams = displayNameLayoutParams
                            }
                        }
                    )
                }
            }
        )
    }

    fun disableAutomaticMessageDeletion() {
        findAndHookMethod(
            GApp.persistence.repository.ChatRepo,
            Hooker.pkgParam.classLoader,
            GApp.persistence.repository.ChatRepo_.deleteChatMessageFromLessThanOrEqualToTimestamp,
            Long::class.java,
            "kotlin.coroutines.Continuation",
            RETURN_UNIT
        )
    }

    fun dontSendChatMarkers() {
        findAndHookMethod(
            "org.jivesoftware.smack.AbstractXMPPConnection",
            Hooker.pkgParam.classLoader,
            "sendStanza",
            "org.jivesoftware.smack.packet.Stanza",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val stanza = param.args[0]
                    val hasReceivedExtension = callMethod(stanza, "hasExtension", "received", "urn:xmpp:chat-markers:0") as Boolean
                    val hasDisplayedExtension = callMethod(stanza, "hasExtension", "displayed", "urn:xmpp:chat-markers:0") as Boolean
                    if (hasReceivedExtension || hasDisplayedExtension) {
                        param.result = null
                    }
                }
            })
    }

    fun dontSendTypingIndicator() {
        findAndHookMethod(
            "org.jivesoftware.smackx.chatstates.ChatStateManager",
            Hooker.pkgParam.classLoader,
            "setCurrentState",
            "org.jivesoftware.smackx.chatstates.ChatState",
            "org.jivesoftware.smack.chat2.Chat",
            XC_MethodReplacement.DO_NOTHING
        )
    }

    /**
     * Creates a local terminal which can be used to execute commands
     * in any chat by using the '/' prefix.
     */
    fun createChatTerminal() {
        val sendChatMessage = findMethodExact(
            GApp.xmpp.ChatMessageManager,
            Hooker.pkgParam.classLoader,
            GApp.xmpp.ChatMessageManager_.handleOutgoingChatMessage,
            findClass("hc.p0", Hooker.pkgParam.classLoader), // ChatWrapper
        )

        hookMethod(sendChatMessage, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val chatMessage = getObjectField(param.args[0], "a")
                val text = getObjectField(chatMessage, "body") as String
                val recipient = getObjectField(chatMessage, "recipient") as String

                if (text.startsWith("/")) {
                    param.result = null // Prevents the command from being sent as a message
                    val commandHandler = CommandHandler(recipient)
                    commandHandler.handleCommand(text.substring(1))
                }
            }
        })

    }

    val regex = Regex("([0-9]+\\.[0-9]+),([0-9]+\\.[0-9]+)")

    /*
    DO NOT ENABLE THIS IN PRODUCTION BUILDS!
    This methods disables OkHttp's certificate pinning trusts all certificate.
    Useful for analyzing network traffic with tools like mitmproxy.
    THIS COMPLETELY MITIGATES ANY SECURITY AND SHOULD NOT BE USED IN PRODUCTION
     */
    fun trustAllCerts() {
        XposedHelpers.findAndHookConstructor(
            "okhttp3.OkHttpClient\$Builder",
            Hooker.pkgParam.classLoader,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val trustAlLCerts = arrayOf<TrustManager>(object : X509TrustManager {
                        override fun checkClientTrusted(
                            chain: Array<out X509Certificate>?,
                            authType: String?
                        ) {
                        }

                        override fun checkServerTrusted(
                            chain: Array<out X509Certificate>?,
                            authType: String?
                        ) {
                        }

                        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

                    })
                    val sslContext = SSLContext.getInstance("TLSv1.3")
                    sslContext.init(null, trustAlLCerts, SecureRandom())
                    callMethod(param.thisObject, "sslSocketFactory", sslContext.socketFactory, trustAlLCerts.first() as X509TrustManager)
                    callMethod(param.thisObject, "hostnameVerifier", object : HostnameVerifier {
                        override fun verify(hostname: String?, session: SSLSession?): Boolean = true
                    })
                }
            })

        findAndHookMethod(
            "okhttp3.OkHttpClient\$Builder",
            Hooker.pkgParam.classLoader,
            "certificatePinner",
            "okhttp3.CertificatePinner",
            XC_MethodReplacement.DO_NOTHING)
    }

}