package com.example.myspeaker

import android.graphics.Color

/**
 * EQ Preset data class and definitions
 * 
 * Each preset defines bass, mid, treble levels (0-100)
 * where 50 is neutral/flat
 */
data class EqPreset(
    val id: Int,
    val name: String,
    val description: String,
    val bass: Int,      // 0-100, 50 = neutral
    val mid: Int,       // 0-100, 50 = neutral
    val treble: Int,    // 0-100, 50 = neutral
    val color: Int,     // Accent color for UI
    val icon: String    // Icon name/emoji
)

object EqPresets {
    
    // Preset definitions
    val BALANCED = EqPreset(
        id = 0,
        name = "Balanced",
        description = "Flat response, true to source",
        bass = 50,
        mid = 50,
        treble = 50,
        color = Color.parseColor("#00D4FF"),
        icon = "⚖️"
    )
    
    val DEEP_BASS = EqPreset(
        id = 1,
        name = "Deep Bass",
        description = "Enhanced low frequencies",
        bass = 80,
        mid = 45,
        treble = 40,
        color = Color.parseColor("#FF6B35"),
        icon = "🔊"
    )
    
    val CLEAR_VOCALS = EqPreset(
        id = 2,
        name = "Clear Vocals",
        description = "Enhanced clarity for speech and vocals",
        bass = 35,
        mid = 70,
        treble = 55,
        color = Color.parseColor("#9C27B0"),
        icon = "🎤"
    )
    
    val BRIGHT_CLEAR = EqPreset(
        id = 3,
        name = "Bright & Clear",
        description = "Crisp highs, detailed sound",
        bass = 40,
        mid = 55,
        treble = 75,
        color = Color.parseColor("#00E676"),
        icon = "✨"
    )
    
    val PUNCHY = EqPreset(
        id = 4,
        name = "Punchy",
        description = "Tight bass with presence",
        bass = 70,
        mid = 60,
        treble = 55,
        color = Color.parseColor("#FF5252"),
        icon = "💥"
    )
    
    val WARM = EqPreset(
        id = 5,
        name = "Warm",
        description = "Smooth and relaxed sound",
        bass = 60,
        mid = 50,
        treble = 35,
        color = Color.parseColor("#FFAB40"),
        icon = "☀️"
    )
    
    // Specialized presets
    val STUDIO = EqPreset(
        id = 6,
        name = "Studio",
        description = "Reference monitoring profile",
        bass = 48,
        mid = 52,
        treble = 50,
        color = Color.parseColor("#78909C"),
        icon = "🎚️"
    )
    
    val CLUB = EqPreset(
        id = 7,
        name = "Club",
        description = "Dance and electronic music",
        bass = 85,
        mid = 40,
        treble = 60,
        color = Color.parseColor("#E040FB"),
        icon = "🎵"
    )
    
    val CINEMA = EqPreset(
        id = 8,
        name = "Cinema",
        description = "Immersive movie experience",
        bass = 65,
        mid = 55,
        treble = 45,
        color = Color.parseColor("#FF7043"),
        icon = "🎬"
    )
    
    val PODCAST = EqPreset(
        id = 9,
        name = "Podcast",
        description = "Optimized for spoken content",
        bass = 30,
        mid = 75,
        treble = 50,
        color = Color.parseColor("#26A69A"),
        icon = "🎙️"
    )
    
    val GAMING = EqPreset(
        id = 10,
        name = "Gaming",
        description = "Enhanced spatial awareness",
        bass = 55,
        mid = 65,
        treble = 70,
        color = Color.parseColor("#7C4DFF"),
        icon = "🎮"
    )
    
    val LATE_NIGHT = EqPreset(
        id = 11,
        name = "Late Night",
        description = "Reduced bass for quiet listening",
        bass = 30,
        mid = 55,
        treble = 45,
        color = Color.parseColor("#5C6BC0"),
        icon = "🌙"
    )
    
    // All presets list - main presets first
    val allPresets = listOf(
        BALANCED,
        DEEP_BASS,
        CLEAR_VOCALS,
        BRIGHT_CLEAR,
        PUNCHY,
        WARM
    )
    
    // Extended presets for power users
    val extendedPresets = listOf(
        STUDIO,
        CLUB,
        CINEMA,
        PODCAST,
        GAMING,
        LATE_NIGHT
    )
    
    // Combined list
    val allPresetsIncludingExtended = allPresets + extendedPresets
    
    fun getPresetById(id: Int): EqPreset {
        return allPresetsIncludingExtended.find { it.id == id } ?: BALANCED
    }
    
    fun getPresetByName(name: String): EqPreset? {
        return allPresetsIncludingExtended.find { it.name.equals(name, ignoreCase = true) }
    }
}
