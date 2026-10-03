package com.senecapp.data

data class FreeNowEvent(
    val id: Int,
    val title: String,
    val groupName: String,
    val description: String?,
    val startsAt: String,
    val buildingName: String?,
    val walkingMinutes: Int?,
    val reasons: List<String>,
)

data class FreeNowSuggestion(
    val requestId: String,
    val freeStartsAt: String?,
    val freeEndsAt: String?,
    val freeMinutes: Int?,
    val scheduleKnown: Boolean,
    val locationName: String?,
    val events: List<FreeNowEvent>,
    val message: String?,
    val locationSource: String = "none",
)

data class EventCoordinates(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}
