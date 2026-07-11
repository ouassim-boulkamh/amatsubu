package eu.kanade.domain.chapter.service

import eu.kanade.domain.testing.FuzzTestConfig
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ChapterRecognitionPropertyTest {

    @Test
    fun `generated chapter recognition output is deterministic and finite`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), generatedChapterNameArb) { sample ->
            val first = ChapterRecognition.parseChapterNumber(
                mangaTitle = sample.mangaTitle,
                chapterName = sample.chapterName,
                chapterNumber = sample.knownChapterNumber,
            )
            val second = ChapterRecognition.parseChapterNumber(
                mangaTitle = sample.mangaTitle,
                chapterName = sample.chapterName,
                chapterNumber = sample.knownChapterNumber,
            )

            first shouldBe second
            first.isFinite() shouldBe true
        }
    }

    @Test
    fun `known chapter markers remain parseable with harmless punctuation and spacing`() = runTest {
        checkAll(FuzzTestConfig.caseCount(), markedChapterArb) { sample ->
            val parsed = ChapterRecognition.parseChapterNumber(sample.mangaTitle, sample.chapterName)

            parsed shouldBe sample.expectedChapterNumber
        }
    }

    private companion object {
        val safeTextArb = Arb.string(0..32).map { value ->
            value
                .filter { it.isLetter() || it.isWhitespace() }
                .ifBlank { "Manga" }
        }

        val knownChapterNumberArb = arbitrary<Double?> { random ->
            when (random.random.nextInt(5)) {
                0 -> null
                1 -> -2.0
                2 -> -1.0
                else -> Arb.double(0.0..10_000.0).bind()
            }
        }

        val generatedChapterNameArb: Arb<GeneratedChapterName> = Arb.bind(
            safeTextArb,
            safeTextArb,
            Arb.int(0..10_000),
            knownChapterNumberArb,
        ) { mangaTitle, chapterWords, chapterNumber, knownChapterNumber ->
            GeneratedChapterName(
                mangaTitle = mangaTitle,
                chapterName = "$mangaTitle Vol. ${chapterNumber % 20} Ch. $chapterNumber $chapterWords",
                knownChapterNumber = knownChapterNumber,
            )
        }

        val markedChapterArb = Arb.bind(
            safeTextArb,
            Arb.int(1..10_000),
            Arb.int(0..5),
        ) { mangaTitle, chapterNumber, shape ->
            val chapterName = when (shape) {
                0 -> "$mangaTitle Ch.$chapterNumber"
                1 -> "$mangaTitle Ch. $chapterNumber"
                2 -> "$mangaTitle Vol. 1 Ch.$chapterNumber: title"
                3 -> "$mangaTitle Vol.1 Ch.$chapterNumber"
                4 -> "$mangaTitle - Ch-$chapterNumber"
                else -> "$mangaTitle $chapterNumber"
            }
            MarkedChapter(mangaTitle, chapterName, chapterNumber.toDouble())
        }

        data class GeneratedChapterName(
            val mangaTitle: String,
            val chapterName: String,
            val knownChapterNumber: Double?,
        )

        data class MarkedChapter(
            val mangaTitle: String,
            val chapterName: String,
            val expectedChapterNumber: Double,
        )
    }
}
