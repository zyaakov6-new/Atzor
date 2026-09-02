package app.atzor.data

import androidx.annotation.StringRes
import app.atzor.R

/**
 * Known packages for one-tap category blocking.
 * Only packages actually installed on the device are applied.
 */
object AppCategories {
    data class Category(val id: String, @StringRes val titleRes: Int, val packages: Set<String>)

    val all: List<Category> = listOf(
        Category(
            id = "social",
            titleRes = R.string.cat_social,
            packages = setOf(
                "com.instagram.android",
                "com.facebook.katana",
                "com.facebook.orca",
                "com.twitter.android",
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                "com.snapchat.android",
                "com.linkedin.android",
                "com.reddit.frontpage",
                "org.telegram.messenger",
                "com.whatsapp",
                "com.discord",
            ),
        ),
        Category(
            id = "video",
            titleRes = R.string.cat_video,
            packages = setOf(
                "com.google.android.youtube",
                "com.netflix.mediaclient",
                "com.amazon.avod.thirdpartyclient",
                "com.disney.disneyplus",
                "tv.twitch.android.app",
                "com.google.android.apps.youtube.music",
                "com.spotify.music",
            ),
        ),
        Category(
            id = "games",
            titleRes = R.string.cat_games,
            packages = setOf(
                "com.king.candycrushsaga",
                "com.supercell.clashofclans",
                "com.supercell.clashroyale",
                "com.roblox.client",
                "com.mojang.minecraftpe",
                "com.activision.callofduty.shooter",
                "com.epicgames.fortnite",
            ),
        ),
        Category(
            id = "news",
            titleRes = R.string.cat_news,
            packages = setOf(
                "com.android.chrome",
                "org.mozilla.firefox",
                "com.microsoft.emmx",
                "com.sec.android.app.sbrowser",
                "com.opera.browser",
                "flipboard.app",
            ),
        ),
    )
}
