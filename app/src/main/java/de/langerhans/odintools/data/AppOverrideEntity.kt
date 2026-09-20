package de.langerhans.odintools.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "appoverride")
data class AppOverrideEntity(
    @PrimaryKey
    val packageName: String,
    val tdpProfile: String?,   // Ex: "Balanced (11W)", "Power Save (5W)", ou o nome do perfil customizado
    val clockProfile: String?, // Ex: "Triple A", "Stock", ou o nome customizado
    val fanProfile: String?    // Ex: "Smart", "Sport", "Silent"
)
