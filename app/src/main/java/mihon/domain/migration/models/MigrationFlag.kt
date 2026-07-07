package mihon.domain.migration.models

enum class MigrationFlag(val flag: Int) {
    CHAPTER(0b00001),
    CATEGORY(0b00010),

    // 0b00100 was used for manga trackers
    // 0b01000 was used for custom covers
    // 0b10000 was used for deleting local downloads
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
