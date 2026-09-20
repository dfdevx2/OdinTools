package de.langerhans.odintools.models

import java.util.UUID

data class CustomClockProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val perfClockKHz: Long,
    val primeClockKHz: Long,
    val gpuClockHz: Long
) {
    fun serialize(): String = "$id|$name|$perfClockKHz|$primeClockKHz|$gpuClockHz"

    companion object {
        fun deserialize(str: String): CustomClockProfile? {
            return runCatching {
                val parts = str.split("|")
                CustomClockProfile(
                    id = parts[0],
                    name = parts[1],
                    perfClockKHz = parts[2].toLong(),
                    primeClockKHz = parts[3].toLong(),
                    gpuClockHz = parts[4].toLong()
                )
            }.getOrNull()
        }
    }
}