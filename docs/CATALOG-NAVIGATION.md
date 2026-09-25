# Catalog card navigation

2026-09-24: New/Main and New/Explore retain full server link destinations. Relative links normalize to vk.com. Catalog cards use native navigation only: no ACTION_VIEW, browser, VK application, or WebView fallback. Native playlist/album/artist metadata takes precedence over a generic URL.

- Direct artist/album/playlist/audio/owner links use VkLinkResolver.handleInApp. Unsupported destinations and API errors are displayed inside the client.
- Explicit opaque section IDs use catalog.getSection; curator links use catalog.getAudioCurator.
- Music collection/landing URLs use catalog.getAudio(url=full URL, need_blocks=true), then load the returned default section if required. Root section names such as general/explore are not opaque section IDs. catalog/section query parameters are kept, so an owner collection is not silently replaced with the full library. A z= shared playlist/audio target takes precedence over the containing tab.
- Returned blocks use the existing native NewCatalogBlock renderer and section sheet. Pagination uses the section ID actually returned by VK; retries keep the original URL. Unsupported external/editorial article URLs currently show an in-app explanation; no article reader is claimed.
- Only recognized VK music URLs are sent to catalog.getAudio; arbitrary foreign domains and nonmusic pages are rejected.

Catalog section IDs survive HomeCacheManager serialization; schema 9 discards earlier incomplete snapshots. Circular shortcuts include their labels in the click area. Main mix and recommendation containers respond across the card; nested playback/settings actions remain distinct. An actionless card explains the missing destination and offers refresh. Rapid card taps cancel previous link resolutions.

Local primary-source reference: /root/vkmusic_8.37_jadx/sources/com/vk/music/link/processor/x0.java passes the complete /audio?catalog= URL as key_url; /root/VKMUSIC-8.37-ANALYSIS/04-catalog.md sections 1.1/3.1 trace key_url into catalog.getAudio.url. VkMusicNavigationTab.java defines root section names. No WebView is involved in this API flow.

Validation: CatalogNavigationTest covers collection URLs, query precedence, root tabs vs opaque IDs, foreign host rejection and playlist shortcut discrimination. Device interaction and actual server responses for the reported card still require verification. APK assembly is deferred at the user's request.
