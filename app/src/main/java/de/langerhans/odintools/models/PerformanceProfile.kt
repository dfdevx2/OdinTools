package de.langerhans.odintools.models

data class PerformanceProfile(
    val id: String,
    val name: String,
    val type: ProfileType,
    val tdpWatts: Float?, // Nulo se for modo Stock
    val perfClockKHz: Long?, // Teto para núcleos Perf
    val primeClockKHz: Long?, // Teto para núcleos Prime
    val gpuClockHz: Long?, // Teto para GPU Adreno
    val isEditable: Boolean
) {
    enum class ProfileType {
        POWER_SAVE,
        BALANCED,
        TRIPLE_A,
        STOCK,
        CUSTOM
    }

    companion object {
        val DEFAULTS = listOf(
            PerformanceProfile(
                id = "default_powersave",
                name = "Power Save (5W)",
                type = ProfileType.POWER_SAVE,
                tdpWatts = 5f,
                perfClockKHz = 2000000L,
                primeClockKHz = 2400000L,
                gpuClockHz = 500000000L,
                isEditable = false
            ),
            PerformanceProfile(
                id = "default_balanced",
                name = "Balanced (11W)",
                type = ProfileType.BALANCED,
                tdpWatts = 11f,
                perfClockKHz = 2800000L,
                primeClockKHz = 3400000L,
                gpuClockHz = 750000000L,
                isEditable = false
            ),
            PerformanceProfile(
                id = "default_triple_a",
                name = "Triple A (14.5W)",
                type = ProfileType.TRIPLE_A,
                tdpWatts = 14.5f,
                perfClockKHz = 3530000L,
                primeClockKHz = 4320000L,
                gpuClockHz = 1100000000L,
                isEditable = false
            ),
            PerformanceProfile(
                id = "default_stock",
                name = "Stock (Padrão AYN)",
                type = ProfileType.STOCK,
                tdpWatts = null,
                perfClockKHz = null,
                primeClockKHz = null,
                gpuClockHz = null,
                isEditable = false
            )
        )
    }
}