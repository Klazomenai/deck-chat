package dev.klazomenai.deckchat

/**
 * A crew member's profile — voice model, display name, and default verbosity.
 *
 * @param name lowercase key used in Matrix body-prefix: `[name:verbosity]`
 * @param displayName spoken announcement prefix: "Maren", "Crest"
 * @param voiceDir assets/tts/ subdirectory: "vits-piper-en_GB-cori-high"
 * @param defaultVerbosity MVP default: "dispatch". See issue #49 for verbosity spike.
 */
data class CrewMember(
    val name: String,
    val displayName: String,
    val voiceDir: String,
    val defaultVerbosity: String,
)

/**
 * Single source of truth for crew member data.
 *
 * Maps lowercase crew name to [CrewMember]. Unknown names fall back to
 * the default crew member (Maren) — never crashes on unknown input.
 *
 * Used by [SherpaOnnxTtsEngine] for voice model selection and announcement
 * prefix, and by the Matrix message pipeline for crew identification.
 *
 * Contract with klazomenai/bridge: crew_member tag values are lowercase strings.
 */
object CrewRegistry {

    private val members = mapOf(
        "maren" to CrewMember(
            name = "maren",
            displayName = "Maren",
            voiceDir = "vits-piper-en_GB-cori-high",
            defaultVerbosity = "dispatch",
        ),
        "crest" to CrewMember(
            name = "crest",
            displayName = "Crest",
            voiceDir = "vits-piper-en_US-lessac-high",
            defaultVerbosity = "dispatch",
        ),
    )

    private val default = members.getValue("maren")

    /**
     * Looks up a crew member by name (case-insensitive).
     * Returns the default crew member (Maren) if the name is unknown.
     */
    fun lookup(crewName: String): CrewMember =
        members[crewName.lowercase(java.util.Locale.ROOT)] ?: default

    /** Returns true if the crew name is registered (case-insensitive). */
    fun isKnown(crewName: String): Boolean =
        members.containsKey(crewName.lowercase(java.util.Locale.ROOT))

    /** Returns all registered crew names (lowercase). */
    fun allNames(): Set<String> = members.keys

    /** Returns the default crew member (Maren). */
    fun default(): CrewMember = default
}
