package com.uniandes.senecapp_kotlin

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

data class Rso(
    val id: Int,
    val name: String,
    val category: String,
    val members: Int,
    val description: String,
    val tags: List<String>,
    @ColorInt val color: Int,
    @DrawableRes val image: Int,
    val nextEvent: String,
    val verified: Boolean,
)

data class SenecEvent(
    val id: Int,
    val rsoId: Int,
    val organization: String,
    val title: String,
    val date: String,
    val time: String,
    val location: String,
    @ColorInt val color: Int,
)

object SenecData {
    val organizations = listOf(
        Rso(1, "Tennis Uniandes", "Sports", 142,
            "Competitive and recreational tennis for all levels. Weekly matches, tournaments, and coaching sessions on our campus courts.",
            listOf("Sports", "Competitive", "Outdoor"), 0xFFA50104.toInt(), R.drawable.tennis_uniandes,
            "Sat, Aug 22 - 8:00 AM", true),
        Rso(2, "Emprendedores Uniandes", "Business", 318,
            "Where future founders meet. Pitch nights, mentorship from alumni VCs, and startup incubation resources for Uniandes students.",
            listOf("Business", "Startups", "Networking"), 0xFFFF6B35.toInt(), R.drawable.emprendedores_uniandes,
            "Thu, Aug 20 - 6:00 PM", true),
        Rso(3, "Viajeros Uniandes", "Travel", 207,
            "Explore Colombia and beyond. We organize group trips, weekend getaways, and cultural immersion experiences every semester.",
            listOf("Travel", "Adventure", "Culture"), 0xFF00C9A7.toInt(), R.drawable.viajeros_uniandes,
            "Fri, Aug 28 - 7:00 AM", false),
        Rso(4, "Auto Enthusiasts", "Cars", 89,
            "Monthly car meets, track days at Tocancipa, and deep dives into automotive culture. All makes welcome, from classics to hypercars.",
            listOf("Cars", "Community", "Events"), 0xFFA78BFA.toInt(), R.drawable.auto_enthusiasts,
            "Sun, Aug 30 - 10:00 AM", false),
        Rso(5, "AI & Machine Learning", "Technology", 256,
            "Research papers, Kaggle competitions, and build sessions. We push the frontier of AI/ML at Uniandes with weekly workshops.",
            listOf("Technology", "Research", "AI"), 0xFF3B82F6.toInt(), R.drawable.ai_machine_learning,
            "Wed, Aug 19 - 5:00 PM", true),
        Rso(6, "Teatro Los Andes", "Arts", 173,
            "From improv to full theatrical productions. Auditions are open to everyone: no experience required, just passion for storytelling.",
            listOf("Arts", "Performance", "Creative"), 0xFFEC4899.toInt(), R.drawable.teatro_los_andes,
            "Tue, Aug 25 - 7:00 PM", true),
        Rso(7, "Finance Society", "Business", 195,
            "CFA prep, Bloomberg terminal access, case competitions, and connections to top finance firms in Bogota and beyond.",
            listOf("Finance", "Professional", "Learning"), 0xFFF59E0B.toInt(), R.drawable.finance_society,
            "Mon, Aug 18 - 5:30 PM", false),
        Rso(8, "Fotografia Uniandes", "Arts", 134,
            "Darkroom access, photowalks around Bogota, and exhibitions. Film and digital are both welcome.",
            listOf("Photography", "Creative", "Arts"), 0xFF6366F1.toInt(), R.drawable.fotografia_uniandes,
            "Sat, Aug 22 - 2:00 PM", false),
    )

    val events = listOf(
        SenecEvent(1, 1, "Tennis Uniandes", "Round Robin Tournament", "Sat, Aug 22", "8:00 AM", "Campus Courts", 0xFFA50104.toInt()),
        SenecEvent(2, 5, "AI & ML Group", "LLM Workshop: Build Your Own Agent", "Wed, Aug 19", "5:00 PM", "Edificio SD, Sala 302", 0xFF3B82F6.toInt()),
        SenecEvent(3, 2, "Emprendedores Uniandes", "Pitch Night #14", "Thu, Aug 20", "6:00 PM", "Auditorio Mario Laserna", 0xFFFF6B35.toInt()),
        SenecEvent(4, 3, "Viajeros Uniandes", "Trip to Salento & Coffee Region", "Fri, Aug 28", "7:00 AM", "Entrada Principal", 0xFF00C9A7.toInt()),
        SenecEvent(5, 4, "Auto Enthusiasts", "Monthly Car Meet - Agosto", "Sun, Aug 30", "10:00 AM", "Parking Lot P6", 0xFFA78BFA.toInt()),
        SenecEvent(6, 6, "Teatro Los Andes", "Open Auditions: Obra de Semestre", "Tue, Aug 25", "7:00 PM", "Teatro Opera", 0xFFEC4899.toInt()),
    )
}
