package com.senecapp.data

data class GroupRecommendation(
    val id: Int,
    val name: String,
    val category: String,
    val description: String,
    val memberCount: Int,
    val reasons: List<String>,
    val isMember: Boolean,
    val color: String? = null,
    val imageUrl: String? = null,
    val verified: Boolean = false,
)

data class GroupRecommendations(
    val requestId: String,
    val items: List<GroupRecommendation>,
)
