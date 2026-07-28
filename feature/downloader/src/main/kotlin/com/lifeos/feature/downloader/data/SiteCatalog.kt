package com.lifeos.feature.downloader.data

/** One entry in the browsable source list (§Module Downloader). */
data class SupportedSite(
    val name: String,
    val host: String,
    val category: String,
    val note: String,
)

/**
 * Curated catalogue of sites the on-device extractor handles, grouped so the
 * module can show what is worth pasting. Everything here is reached with the
 * generic extractor plus the site helpers in [MediaExtractor] - no external
 * service, no API keys, no accounts.
 *
 * Sites needing a logged-in session (private posts, paid courses) are listed
 * with that caveat instead of being silently omitted.
 */
object SiteCatalog {

    val sites: List<SupportedSite> = listOf(
        // ---- Video platforms ------------------------------------------------
        SupportedSite("YouTube", "youtube.com", "Video", "Public videos and Shorts"),
        SupportedSite("YouTube Music", "music.youtube.com", "Video", "Audio streams"),
        SupportedSite("Vimeo", "vimeo.com", "Video", "Progressive files via the player config"),
        SupportedSite("Dailymotion", "dailymotion.com", "Video", "HLS master playlists"),
        SupportedSite("Streamable", "streamable.com", "Video", "Direct mp4"),
        SupportedSite("Rumble", "rumble.com", "Video", "Direct mp4"),
        SupportedSite("Odysee", "odysee.com", "Video", "LBRY streams"),
        SupportedSite("BitChute", "bitchute.com", "Video", "Direct mp4"),
        SupportedSite("PeerTube", "joinpeertube.org", "Video", "Any PeerTube instance"),
        SupportedSite("Twitch", "twitch.tv", "Video", "Clips and VOD HLS"),
        SupportedSite("Kick", "kick.com", "Video", "Clips and VOD HLS"),
        SupportedSite("Loom", "loom.com", "Video", "Public recordings"),
        SupportedSite("Bilibili", "bilibili.com", "Video", "Public videos"),
        SupportedSite("Niconico", "nicovideo.jp", "Video", "Public videos"),
        SupportedSite("VK Video", "vk.com", "Video", "Public videos"),
        SupportedSite("OK.ru", "ok.ru", "Video", "Public videos"),
        SupportedSite("TED", "ted.com", "Video", "Talk downloads"),
        SupportedSite("Archive.org", "archive.org", "Video", "Films, audio, software"),
        SupportedSite("Wikimedia Commons", "commons.wikimedia.org", "Video", "Freely licensed media"),

        // ---- Social ---------------------------------------------------------
        SupportedSite("TikTok", "tiktok.com", "Social", "Watermark-free when the page exposes it"),
        SupportedSite("Instagram", "instagram.com", "Social", "Public posts and reels via the embed page"),
        SupportedSite("Threads", "threads.net", "Social", "Public posts via OpenGraph"),
        SupportedSite("Facebook", "facebook.com", "Social", "Public videos and reels"),
        SupportedSite("X", "x.com", "Social", "Public posts; also twitter.com links"),
        SupportedSite("LinkedIn", "linkedin.com", "Social", "Public post videos (progressive streams)"),
        SupportedSite("Reddit", "reddit.com", "Social", "v.redd.it audio+video, gifs, gallery items"),
        SupportedSite("Tumblr", "tumblr.com", "Social", "Public posts"),
        SupportedSite("Pinterest", "pinterest.com", "Social", "Pin videos"),
        SupportedSite("Snapchat", "snapchat.com", "Social", "Spotlight clips"),
        SupportedSite("Mastodon", "mastodon.social", "Social", "Any instance, public posts"),
        SupportedSite("Bluesky", "bsky.app", "Social", "Public posts (HLS)"),
        SupportedSite("Imgur", "imgur.com", "Social", "Direct mp4/gifv"),
        SupportedSite("9GAG", "9gag.com", "Social", "Direct mp4"),
        SupportedSite("Weibo", "weibo.com", "Social", "Public videos"),
        SupportedSite("Douyin", "douyin.com", "Social", "Public videos"),

        // ---- Audio ----------------------------------------------------------
        SupportedSite("SoundCloud", "soundcloud.com", "Audio", "Public tracks and HLS"),
        SupportedSite("Bandcamp", "bandcamp.com", "Audio", "Streamable mp3 for public tracks"),
        SupportedSite("Mixcloud", "mixcloud.com", "Audio", "Public shows"),
        SupportedSite("Audiomack", "audiomack.com", "Audio", "Public tracks"),
        SupportedSite("Podbean", "podbean.com", "Audio", "Episode mp3"),
        SupportedSite("Apple Podcasts", "podcasts.apple.com", "Audio", "Episode mp3 from the feed link"),
        SupportedSite("Any RSS podcast", "rss", "Audio", "Paste the episode enclosure URL"),

        // ---- Broadcast and news --------------------------------------------
        SupportedSite("ARD Mediathek", "ardmediathek.de", "Broadcast", "HLS/mp4"),
        SupportedSite("ZDF Mediathek", "zdf.de", "Broadcast", "HLS/mp4"),
        SupportedSite("Arte", "arte.tv", "Broadcast", "HLS"),
        SupportedSite("Deutsche Welle", "dw.com", "Broadcast", "Direct mp4"),
        SupportedSite("BBC", "bbc.co.uk", "Broadcast", "News clips (iPlayer is DRM-protected)"),
        SupportedSite("SRF", "srf.ch", "Broadcast", "HLS"),
        SupportedSite("ORF", "orf.at", "Broadcast", "HLS"),
        SupportedSite("NPR", "npr.org", "Broadcast", "Story audio"),

        // ---- File and video hosts ------------------------------------------
        SupportedSite("Streamtape", "streamtape.com", "File host", "Direct mp4 token link"),
        SupportedSite("Doodstream", "dood.to", "File host", "Direct mp4 token link"),
        SupportedSite("Mixdrop", "mixdrop.co", "File host", "Direct mp4"),
        SupportedSite("Filemoon", "filemoon.sx", "File host", "HLS"),
        SupportedSite("Vidoza", "vidoza.net", "File host", "Direct mp4"),
        SupportedSite("VOE", "voe.sx", "File host", "HLS"),
        SupportedSite("Upstream", "upstream.to", "File host", "HLS"),

        // ---- Adult (kt_player / KVS family and friends) ---------------------
        SupportedSite("ThisVid", "thisvid.com", "Adult", "KVS player links decoded on-device"),
        SupportedSite("xHamster", "xhamster.com", "Adult", "Direct mp4/HLS"),
        SupportedSite("XVideos", "xvideos.com", "Adult", "Direct mp4/HLS"),
        SupportedSite("Pornhub", "pornhub.com", "Adult", "Direct mp4"),
        SupportedSite("YouPorn", "youporn.com", "Adult", "Direct mp4"),
        SupportedSite("SpankBang", "spankbang.com", "Adult", "Direct mp4"),
        SupportedSite("Eporner", "eporner.com", "Adult", "Direct mp4"),
        SupportedSite("Redgifs", "redgifs.com", "Adult", "Direct mp4"),
        SupportedSite("Motherless", "motherless.com", "Adult", "Direct mp4"),
        SupportedSite("KVS tube sites", "kt_player", "Adult", "Any site using the kt_player script"),
    )

    val categories: List<String> = sites.map { it.category }.distinct()

    fun search(query: String): List<SupportedSite> {
        if (query.isBlank()) return sites
        val needle = query.trim().lowercase()
        return sites.filter {
            it.name.lowercase().contains(needle) ||
                it.host.lowercase().contains(needle) ||
                it.category.lowercase().contains(needle)
        }
    }
}
