package eu.kanade.domain.migration.model

enum class MigrationFlag(val flag: Int) {
    CHAPTER(0b00001),
    CATEGORY(0b00010),

    // Historical bits 0b00100, 0b01000, and 0b10000 belonged to Mihon-only
    // migration options that are not restored as Android-local authority.
    NOTES(0b100000),
    ;

    companion object {
        fun fromBit(bit: Int): Set<MigrationFlag> {
            return buildSet {
                entries.forEach { entry ->
                    if (bit and entry.flag != 0) add(entry)
                }
            }
        }

        fun toBit(flags: Set<MigrationFlag>): Int {
            return flags.map { it.flag }
                .reduceOrNull { acc, mask -> acc or mask }
                ?: 0
        }
    }
}
