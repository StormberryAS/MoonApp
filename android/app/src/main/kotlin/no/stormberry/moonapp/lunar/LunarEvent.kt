package no.stormberry.moonapp.lunar

/**
 * Enumeration of supported lunar astronomical events for alarm triggers.
 *
 * For entry-level programmers: An enum (enumeration) defines a fixed set of constants.
 * In MoonApp, each constant represents a specific event in the lunar cycle that can trigger an alarm.
 */
enum class LunarEvent(
    val displayName: String,
    val description: String
) {
    /** The moment when the moon rises above the local horizon. */
    MOONRISE("Moonrise", "When the moon rises above the horizon"),

    /** The moment when the moon sets below the local horizon. */
    MOONSET("Moonset", "When the moon sets below the horizon"),

    /** The moment when the moon passes the local meridian (reaches highest point in the sky). */
    LUNAR_TRANSIT("Lunar Transit (Peak)", "When the moon reaches its highest point in the sky"),

    /** The night of the Full Moon phase (100% illumination). */
    FULL_MOON("Full Moon Night", "On the night of the Full Moon"),

    /** The night of the New Moon phase (0% illumination). */
    NEW_MOON("New Moon Night", "On the night of the New Moon")
}
