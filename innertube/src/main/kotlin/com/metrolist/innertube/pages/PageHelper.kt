package com.metrolist.innertube.pages

import com.metrolist.innertube.models.Menu
import com.metrolist.innertube.models.MusicResponsiveListItemRenderer.FlexColumn
import com.metrolist.innertube.models.Run

data class LibraryTokens(
    val addToken: String? = null,
    val removeToken: String? = null
)

object PageHelper {
    /**
     * Extract library tokens from menu items.
     * Note: Currently returns empty tokens as the full playlist edit endpoint
     * parsing is not implemented. Episodes will still play but save/unsave
     * features require additional model support.
     */
    fun extractLibraryTokensFromMenuItems(items: List<Menu.MenuRenderer.Item>?): LibraryTokens {
        // Simplified implementation - return empty tokens for now
        // Full implementation would require adding playlistEditEndpoint to the models
        return LibraryTokens(null, null)
    }

    fun extractRuns(columns: List<FlexColumn>, typeLike: String): List<Run> {
        val filteredRuns = mutableListOf<Run>()
        for (column in columns) {
            val runs = column.musicResponsiveListItemFlexColumnRenderer.text?.runs
                ?: continue

            for (run in runs) {
                val typeStr = run.navigationEndpoint?.watchEndpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
                    ?: run.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
                    ?: continue

                if (typeLike in typeStr) {
                    filteredRuns.add(run)
                }
            }
        }
        return filteredRuns
    }

    fun extractFeedbackToken(menu: Menu.MenuRenderer.Item.ToggleMenuServiceRenderer?, type: String): String? {
        if (menu == null) return null
        val defaultToken = menu.defaultServiceEndpoint.feedbackEndpoint?.feedbackToken
        val toggledToken = menu.toggledServiceEndpoint?.feedbackEndpoint?.feedbackToken

        return if (menu.defaultIcon.iconType == type) {
            defaultToken
        } else {
            toggledToken
        }
    }
}