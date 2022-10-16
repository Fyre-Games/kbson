package com.github.jershell.kbson.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class WithJsonObject(
    @Contextual
    val data: JsonObject
)