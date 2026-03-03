package org.delcom.data

import kotlinx.serialization.Serializable

@Serializable
data class TodoStats(
    val total: Long,
    val done: Long,
    val notDone: Long,
)