package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Result of a Smart Bubble navigation request.
 *
 * PAGE_COMPLETE is deliberately distinct from UNAVAILABLE: the viewer must turn the page
 * immediately instead of falling back to J2K's normal pan-first behaviour while zoomed in.
 */
enum class SmartBubbleNavigationResult {
    HANDLED,
    PAGE_COMPLETE,
    UNAVAILABLE,
}
