package com.bloodmoon.app.data.model

data class AppExclusion(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false,
    val isExcluded: Boolean = false
)

/**
 * What an app's exclusion turns off while it is in the foreground: - FILTER: the color filter
 * (overlay); - GRAYSCALE: Grayscale (the app shows in color, e.g. camera and gallery). The System
 * Filter (Root) is the same "filter", just drawn by the system instead of the overlay - so the
 * FILTER exclusion already applies to it (there is no separate list).
 */
enum class ExclusionKind { FILTER, GRAYSCALE }
