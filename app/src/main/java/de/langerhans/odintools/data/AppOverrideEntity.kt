package de.langerhans.odintools.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "appoverride")
data class AppOverrideEntity(
    @PrimaryKey
    val packageName: String,

    // Performance & Hardware
    val tdpProfile: String?,
    val clockProfile: String?,
    val fanProfile: String?,

    // Lossless Scaling (Frame Generation)
    val lsfgEnabled: Boolean = false,
    val lsfgMultiplier: Int = 2,           // 2x, 3x, 4x
    val lsfgPerformanceMode: Boolean = false,
    val lsfgFramePacing: Boolean = true,
    val lsfgQuality: Float = 1.0f,          // 0.5f a 1.0f (50% a 100%)

    // Snapdragon Super Resolution (SGSR)
    val sgsrEnabled: Boolean = false,
    val sgsrMode: String = "Quality",      // Quality (0.77x), Balanced (0.66x), Performance (0.5x)
    val sgsrSharpness: Float = 0.5f,        // 0.0f a 1.0f

    // ReShade & Pós-processamento de Cor
    val reshadeProfile: String = "Nenhum", // Vibrante, Cinema, Retrô, HDR Boost, etc.
    val saturationOverride: Float = 1.0f,  // 0.0f a 2.0f
    val temperatureOverride: Float = 6500f // 4000K a 9000K
)