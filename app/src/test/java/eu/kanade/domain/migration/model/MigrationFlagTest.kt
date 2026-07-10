package eu.kanade.domain.migration.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MigrationFlagTest {

    @Test
    fun `saved migration flag bits decode to Amatsubu migration flags`() {
        val savedBits = 0b100011

        assertEquals(
            setOf(MigrationFlag.CHAPTER, MigrationFlag.CATEGORY, MigrationFlag.NOTES),
            MigrationFlag.fromBit(savedBits),
        )
    }

    @Test
    fun `Amatsubu migration flags serialize with legacy-compatible bits`() {
        assertEquals(
            0b100010,
            MigrationFlag.toBit(setOf(MigrationFlag.CATEGORY, MigrationFlag.NOTES)),
        )
    }
}
