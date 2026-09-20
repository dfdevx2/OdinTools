package de.langerhans.odintools.models

import java.util.UUID

data class CustomTdpProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val watts: Float
) {
    fun serialize(): String = "$id|$name|$watts"

    companion object {
        fun deserialize(str: String): CustomTdpProfile? {
            return runCatching {
                val parts = str.split("|")
                CustomTdpProfile(
                    id = parts[0],
                    name = parts[1],
                    watts = parts[2].toFloat()
                )
            }.getOrNull()
        }
    }
}