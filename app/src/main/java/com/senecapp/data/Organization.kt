package com.senecapp.data

data class Organization(
    val id: Int,
    val name: String,
    val category: String,
    val categorySlug: String,
    val members: Int,
    val color: String?,
    val hasUpcomingEvent: Boolean,
)
