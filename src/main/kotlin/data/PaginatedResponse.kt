package org.delcom.data

import kotlinx.serialization.Serializable

@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,
    val page: Int,
    val perPage: Int,
    val total: Long,
    val totalPages: Int,
    val hasNextPage: Boolean,
)