package eu.kanade.domain.testing

object FuzzTestConfig {
    private const val DEFAULT_CASES = 32
    private const val STRESS_CASES = 1_000

    val isStress: Boolean
        get() = System.getProperty("amatsubu.fuzz.stress")?.toBooleanStrictOrNull() == true ||
            System.getenv("AMATSUBU_FUZZ_STRESS")?.toBooleanStrictOrNull() == true

    fun caseCount(defaultCases: Int = DEFAULT_CASES, stressCases: Int = STRESS_CASES): Int {
        val configured = System.getProperty("amatsubu.fuzz.cases")
            ?: System.getenv("AMATSUBU_FUZZ_CASES")
        return configured?.toIntOrNull()?.coerceAtLeast(1)
            ?: if (isStress) stressCases else defaultCases
    }
}
