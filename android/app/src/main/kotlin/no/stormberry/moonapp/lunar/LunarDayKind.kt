package no.stormberry.moonapp.lunar

/**
 * Whether the moon rises and sets on a given day at a given place, or does neither.
 *
 * Mirrors SunApp's DayKind. The distinction matters at Norwegian latitudes, where the moon
 * regularly stays up or down for a whole day, and where telling a user "does not occur today"
 * for both cases hides which of two opposite things is happening.
 */
enum class LunarDayKind { NORMAL, ALWAYS_UP, ALWAYS_DOWN }
