package com.uniandes.senecapp_kotlin

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private enum class Screen { DISCOVER, EVENTS, MY_RSOS, PROFILE, DETAIL, CREATE_RSO, NOTIFICATIONS }

    private val bg = Color.rgb(23, 26, 33)
    private val fg = Color.rgb(240, 226, 231)
    private val primary = Color.rgb(165, 1, 4)
    private val accent = Color.rgb(255, 186, 8)
    private val secondary = Color.rgb(29, 53, 87)
    private val card = Color.rgb(30, 38, 51)
    private val muted = Color.rgb(139, 148, 176)
    private lateinit var container: FrameLayout
    private lateinit var bottomNav: LinearLayout
    private var current = Screen.DISCOVER
    private var previous = Screen.DISCOVER
    private var selected: Rso? = null
    private val favorites = mutableSetOf<Int>()
    private val joined = mutableSetOf(1, 2, 5)
    private val nunito by lazy { ResourcesCompat.getFont(this, R.font.nunito) ?: Typeface.DEFAULT }
    private val bricolage by lazy { ResourcesCompat.getFont(this, R.font.bricolage_grotesque) ?: Typeface.DEFAULT_BOLD }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        container = findViewById(R.id.screen_container)
        bottomNav = findViewById(R.id.bottom_nav)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = when (current) {
                Screen.DETAIL, Screen.CREATE_RSO, Screen.NOTIFICATIONS -> show(previous)
                Screen.DISCOVER -> finish()
                else -> show(Screen.DISCOVER)
            }
        })
        show(Screen.DISCOVER)
    }

    private fun show(screen: Screen) {
        current = screen
        container.removeAllViews()
        val view = when (screen) {
            Screen.DISCOVER -> discoverScreen()
            Screen.EVENTS -> eventsScreen()
            Screen.MY_RSOS -> myRsosScreen()
            Screen.PROFILE -> profileScreen()
            Screen.DETAIL -> detailScreen(selected ?: SenecData.organizations.first())
            Screen.CREATE_RSO -> createRsoScreen()
            Screen.NOTIFICATIONS -> notificationsScreen()
        }
        container.addView(view, FrameLayout.LayoutParams(-1, -1))
        renderNav()
    }

    private fun renderNav() {
        bottomNav.removeAllViews()
        listOf(
            Triple(Screen.DISCOVER, R.drawable.ic_home, "Discover"),
            Triple(Screen.EVENTS, R.drawable.ic_event, "Events"),
            Triple(Screen.MY_RSOS, R.drawable.ic_group, "My RSOs"),
            Triple(Screen.PROFILE, R.drawable.ic_person, "Profile"),
        ).forEach { (screen, icon, title) ->
            val active = current == screen
            val item = vStack(Gravity.CENTER).apply {
                isClickable = true
                contentDescription = title
                setOnClickListener { show(screen) }
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(icon)
                    imageTintList = ColorStateList.valueOf(if (active) primary else muted)
                }, LinearLayout.LayoutParams(dp(24), dp(24)))
                addView(View(this@MainActivity).apply { background = round(if (active) primary else Color.TRANSPARENT, 2) }, LinearLayout.LayoutParams(dp(4), dp(4)))
                addView(text(title, 10f, if (active) primary else muted, true).apply { gravity = Gravity.CENTER })
            }
            bottomNav.addView(item, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun discoverScreen(): View {
        var category = "All"
        var query = ""
        val body = vStack()
        body.addView(hStack(Gravity.CENTER_VERTICAL).apply {
            setPadding(dp(24), dp(10), dp(24), dp(18))
            addView(photo(R.drawable.senecapp_logo, 50, 50))
            addView(vStack().apply {
                setPadding(dp(12), 0, 0, 0)
                addView(text("UNIANDES - BOGOTA", 11f, muted, true))
                addView(title("SENECApp", 25f))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(iconButton(R.drawable.ic_notifications) {
                previous = Screen.DISCOVER
                show(Screen.NOTIFICATIONS)
            })
        })
        val search = EditText(this).apply {
            hint = "Search organizations..."
            setHintTextColor(muted); setTextColor(fg); textSize = 14f; typeface = nunito; isSingleLine = true
            setCompoundDrawablesWithIntrinsicBounds(tinted(R.drawable.ic_search, muted), null, null, null)
            compoundDrawablePadding = dp(10); background = round(secondary, 16); setPadding(dp(16), 0, dp(16), 0)
        }
        body.addView(search, LinearLayout.LayoutParams(-1, dp(50)).horizontal(dp(24)).bottom(dp(18)))
        val featuredLabel = section("FEATURED")
        val featured = featuredOrganizations()
        body.addView(featuredLabel, LinearLayout.LayoutParams(-1, -2).horizontal(dp(24)).bottom(dp(8)))
        body.addView(featured, LinearLayout.LayoutParams(-1, dp(150)).bottom(dp(18)))
        val categoryRow = hStack().apply { setPadding(dp(24), 0, dp(24), 0) }
        body.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(categoryRow) }, LinearLayout.LayoutParams(-1, dp(48)).bottom(dp(12)))
        val listHeader = hStack(Gravity.CENTER_VERTICAL).apply { setPadding(dp(24), 0, dp(24), dp(10)) }
        val count = section("")
        listHeader.addView(count, LinearLayout.LayoutParams(0, -2, 1f))
        listHeader.addView(button("+  New RSO", accent, bg) { previous = Screen.DISCOVER; show(Screen.CREATE_RSO) })
        body.addView(listHeader)
        val list = vStack().apply { setPadding(dp(24), 0, dp(24), dp(26)) }
        body.addView(list)

        fun refreshList() {
            val filtered = SenecData.organizations.filter {
                (category == "All" || it.category == category) &&
                    (query.isBlank() || it.name.contains(query, true) || it.category.contains(query, true))
            }
            count.text = "${filtered.size} ORGANIZATIONS"
            val visible = if (query.isBlank() && category == "All") View.VISIBLE else View.GONE
            featuredLabel.visibility = visible; featured.visibility = visible
            list.removeAllViews()
            filtered.forEach { list.addView(organizationRow(it), LinearLayout.LayoutParams(-1, -2).bottom(dp(10))) }
            if (filtered.isEmpty()) list.addView(text("No organizations match your search.", 14f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(34), 0, dp(34)) })
        }
        val categories = listOf("All", "Sports", "Business", "Technology", "Arts", "Travel", "Cars", "Social", "International")
        fun refreshCategories() {
            categoryRow.removeAllViews()
            categories.forEach { name -> categoryRow.addView(pill(name, name == category) { category = name; refreshCategories(); refreshList() }, LinearLayout.LayoutParams(-2, dp(38)).end(dp(8))) }
        }
        search.addTextChangedListener(watcher { query = it.trim(); refreshList() })
        refreshCategories(); refreshList()
        return scroll(body)
    }

    private fun featuredOrganizations() = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(hStack().apply {
            setPadding(dp(24), 0, dp(12), 0)
            SenecData.organizations.take(3).forEach { addView(featuredCard(it), LinearLayout.LayoutParams(dp(220), dp(145)).end(dp(12))) }
        })
    }

    private fun featuredCard(rso: Rso) = MaterialCardView(this).apply {
        radius = dp(20).toFloat(); cardElevation = 0f; setCardBackgroundColor(card); isClickable = true; setOnClickListener { open(rso) }
        addView(FrameLayout(this@MainActivity).apply {
            addView(ImageView(this@MainActivity).apply { setImageResource(rso.image); scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1, -1))
            addView(View(this@MainActivity).apply { background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(alpha(rso.color, 225), Color.TRANSPARENT)) }, FrameLayout.LayoutParams(-1, -1))
            addView(vStack(Gravity.BOTTOM).apply {
                setPadding(dp(14), dp(8), dp(14), dp(12))
                addView(text(rso.category.uppercase(Locale.getDefault()), 10f, accent, true))
                addView(title(rso.name, 16f, Color.WHITE)); addView(text("${rso.members} members", 11f, 0xFFD7D8DE.toInt()))
            }, FrameLayout.LayoutParams(-1, -1))
            if (rso.verified) addView(text("\u2713 Official", 10f, bg, true).apply { background = round(accent, 10); setPadding(dp(8), dp(4), dp(8), dp(4)) }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).margins(dp(10)))
        })
    }

    private fun organizationRow(rso: Rso) = MaterialCardView(this).apply {
        radius = dp(16).toFloat(); cardElevation = 0f; setCardBackgroundColor(card); strokeColor = alpha(Color.WHITE, 16); strokeWidth = dp(1); isClickable = true; setOnClickListener { open(rso) }
        addView(hStack(Gravity.CENTER_VERTICAL).apply {
            setPadding(dp(14), dp(14), dp(12), dp(14)); addView(photo(rso.image, 62, 62))
            addView(vStack().apply {
                setPadding(dp(14), 0, dp(6), 0)
                addView(title(rso.name + if (rso.verified) "  \u2713" else "", 15f))
                addView(text("${rso.members} members - ${rso.category}", 12f, muted))
                addView(text(rso.nextEvent, 10f, rso.color, true).apply { background = round(alpha(rso.color, 30), 8); setPadding(dp(7), dp(3), dp(7), dp(3)) }, LinearLayout.LayoutParams(-2, -2).top(dp(5)))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(ImageButton(this@MainActivity).apply {
                setImageResource(R.drawable.ic_favorite); setPadding(dp(9), dp(9), dp(9), dp(9)); contentDescription = "Favorite ${rso.name}"
                fun paint() { imageTintList = ColorStateList.valueOf(if (rso.id in favorites) primary else muted); background = round(if (rso.id in favorites) alpha(primary, 34) else secondary, 20) }
                paint(); setOnClickListener { if (!favorites.add(rso.id)) favorites.remove(rso.id); paint() }
            }, LinearLayout.LayoutParams(dp(38), dp(38)))
        })
    }

    private fun eventsScreen(): View {
        val body = vStack().apply { setPadding(dp(24), dp(12), dp(24), dp(26)) }
        body.addView(text("UPCOMING", 11f, muted, true)); body.addView(title("SENECApp Events", 26f), LinearLayout.LayoutParams(-1, -2).bottom(dp(18)))
        body.addView(MaterialCardView(this).apply {
            radius = dp(20).toFloat(); cardElevation = 0f; background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(primary, 0xFFFF6B35.toInt())).apply { cornerRadius = dp(20).toFloat() }
            addView(vStack().apply { setPadding(dp(18), dp(16), dp(18), dp(16)); addView(text("THIS WEEK", 11f, accent, true)); addView(title("6 events from your\ncampus organizations", 20f, Color.WHITE)); addView(text("Aug 18 - Aug 24, 2025", 12f, 0xFFEBD9DA.toInt())) })
        }, LinearLayout.LayoutParams(-1, -2).bottom(dp(16)))
        val filters = hStack(); val list = vStack(); var onlyJoined = false
        fun refresh() {
            list.removeAllViews(); SenecData.events.filter { !onlyJoined || it.rsoId in joined }.forEach { list.addView(eventCard(it), LinearLayout.LayoutParams(-1, -2).bottom(dp(10))) }
            filters.removeAllViews(); filters.addView(pill("All Events", !onlyJoined, accent, bg) { onlyJoined = false; refresh() }, LinearLayout.LayoutParams(-2, dp(38)).end(dp(8))); filters.addView(pill("My RSOs", onlyJoined, accent, bg) { onlyJoined = true; refresh() }, LinearLayout.LayoutParams(-2, dp(38)))
        }
        body.addView(filters, LinearLayout.LayoutParams(-1, dp(46)).bottom(dp(10))); body.addView(list); refresh(); return scroll(body)
    }

    private fun eventCard(event: SenecEvent) = MaterialCardView(this).apply {
        radius = dp(16).toFloat(); cardElevation = 0f; setCardBackgroundColor(card); strokeColor = alpha(Color.WHITE, 16); strokeWidth = dp(1); isClickable = true
        setOnClickListener { SenecData.organizations.find { it.id == event.rsoId }?.let(::open) }
        addView(hStack(Gravity.TOP).apply {
            setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.ic_event); imageTintList = ColorStateList.valueOf(event.color); background = round(alpha(event.color, 35), 14); setPadding(dp(12), dp(12), dp(12), dp(12)) }, LinearLayout.LayoutParams(dp(50), dp(50)))
            addView(vStack().apply { setPadding(dp(13), 0, 0, 0); addView(title(event.title, 15f)); addView(text(event.organization, 12f, event.color, true)); addView(text("${event.date} - ${event.time}", 12f, muted), LinearLayout.LayoutParams(-1, -2).top(dp(6))); addView(text(event.location, 12f, muted)) }, LinearLayout.LayoutParams(0, -2, 1f))
        })
    }

    private fun myRsosScreen(): View {
        val body = vStack().apply { setPadding(dp(24), dp(12), dp(24), dp(26)) }
        body.addView(hStack(Gravity.CENTER_VERTICAL).apply {
            addView(vStack().apply { addView(text("MY ORGANIZATIONS", 11f, muted, true)); addView(title("My RSOs", 26f)) }, LinearLayout.LayoutParams(0, -2, 1f)); addView(photo(R.drawable.senecapp_67, 58, 58))
        }, LinearLayout.LayoutParams(-1, -2).bottom(dp(18)))
        val stats = hStack(); listOf(Triple("3", "Joined", primary), Triple("11", "Events attended", accent), Triple("Sem II", "This semester", 0xFF00C9A7.toInt())).forEach { stats.addView(statCard(it.first, it.second, it.third), LinearLayout.LayoutParams(0, dp(82), 1f).end(dp(8))) }
        body.addView(stats, LinearLayout.LayoutParams(-1, dp(82)).bottom(dp(20))); body.addView(section("ACTIVE MEMBERSHIPS"), LinearLayout.LayoutParams(-1, -2).bottom(dp(10)))
        SenecData.organizations.filter { it.id in joined }.forEach { body.addView(membershipCard(it), LinearLayout.LayoutParams(-1, -2).bottom(dp(12))) }
        return scroll(body)
    }

    private fun membershipCard(rso: Rso) = MaterialCardView(this).apply {
        radius = dp(16).toFloat(); cardElevation = 0f; setCardBackgroundColor(card); isClickable = true; setOnClickListener { open(rso) }
        addView(vStack().apply {
            addView(FrameLayout(this@MainActivity).apply { addView(ImageView(this@MainActivity).apply { setImageResource(rso.image); scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1, -1)); addView(text("Member", 10f, fg, true).apply { background = round(alpha(bg, 210), 10); setPadding(dp(9), dp(5), dp(9), dp(5)) }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).margins(dp(10))) }, LinearLayout.LayoutParams(-1, dp(105)))
            addView(hStack(Gravity.CENTER_VERTICAL).apply { setPadding(dp(15), dp(13), dp(15), dp(13)); addView(vStack().apply { addView(title(rso.name, 15f)); addView(text("${rso.members} members", 12f, muted)) }, LinearLayout.LayoutParams(0, -2, 1f)); addView(text("NEXT EVENT\n${rso.nextEvent.substringBefore(" -")}", 10f, rso.color, true).apply { gravity = Gravity.CENTER; background = round(alpha(rso.color, 28), 10); setPadding(dp(9), dp(6), dp(9), dp(6)) }) })
        })
    }

    private fun profileScreen(): View {
        val body = vStack().apply { setPadding(dp(24), dp(12), dp(24), dp(26)) }
        body.addView(hStack(Gravity.CENTER_VERTICAL).apply { addView(vStack().apply { addView(text("SENECAPP - STUDENT", 11f, muted, true)); addView(title("Profile", 26f)) }, LinearLayout.LayoutParams(0, -2, 1f)); addView(photo(R.drawable.senecapp_hat, 58, 58)) }, LinearLayout.LayoutParams(-1, -2).bottom(dp(18)))
        body.addView(profileCard(), LinearLayout.LayoutParams(-1, -2).bottom(dp(20))); body.addView(section("INTERESTS"), LinearLayout.LayoutParams(-1, -2).bottom(dp(8)))
        body.addView(ChipGroup(this).apply { isSingleLine = false; listOf("Tennis", "AI/ML", "Startups", "Travel", "Cars").forEach { addView(chip(it)) }; addView(chip("+ Add", primary)) }, LinearLayout.LayoutParams(-1, -2).bottom(dp(20)))
        body.addView(section("ACCOUNT"), LinearLayout.LayoutParams(-1, -2).bottom(dp(8)))
        body.addView(vStack().apply {
            background = round(card, 16, alpha(Color.WHITE, 16))
            listOf("Notifications" to "Manage event alerts", "Privacy Settings" to "Control who sees your profile", "University Verification" to "Verified via uniandes.edu.co", "Help & Support" to "Report an issue or give feedback").forEach { item ->
                addView(hStack(Gravity.CENTER_VERTICAL).apply { setPadding(dp(15), dp(13), dp(15), dp(13)); addView(vStack().apply { addView(text(item.first, 14f, fg, true)); addView(text(item.second, 12f, muted)) }, LinearLayout.LayoutParams(0, -2, 1f)); addView(text(">", 20f, muted)); setOnClickListener { Toast.makeText(this@MainActivity, item.first, Toast.LENGTH_SHORT).show() } })
            }
        }); return scroll(body)
    }

    private fun profileCard() = MaterialCardView(this).apply {
        radius = dp(20).toFloat(); cardElevation = 0f; setCardBackgroundColor(secondary); strokeColor = alpha(Color.WHITE, 18); strokeWidth = dp(1)
        addView(vStack().apply {
            setPadding(dp(18), dp(18), dp(18), dp(16)); addView(hStack(Gravity.CENTER_VERTICAL).apply { addView(text("SA", 24f, Color.WHITE, true, true).apply { gravity = Gravity.CENTER; background = round(primary, 16) }, LinearLayout.LayoutParams(dp(66), dp(66))); addView(vStack().apply { setPadding(dp(14), 0, 0, 0); addView(title("Sofia Arango", 19f)); addView(text("s.arango@uniandes.edu.co", 12f, muted)); addView(text("\u2605 Ingenieria de Sistemas - 6to semestre", 10f, accent, true), LinearLayout.LayoutParams(-2, -2).top(dp(5))) }, LinearLayout.LayoutParams(0, -2, 1f)) }, LinearLayout.LayoutParams(-1, -2).bottom(dp(14)))
            val stats = hStack(); listOf("3" to "RSOs", "11" to "Events", "2" to "Years").forEach { stats.addView(statText(it.first, it.second), LinearLayout.LayoutParams(0, -2, 1f)) }; addView(stats)
        })
    }

    private fun detailScreen(rso: Rso): View {
        var isJoined = rso.id in joined
        val body = vStack()
        body.addView(FrameLayout(this).apply {
            addView(ImageView(this@MainActivity).apply { setImageResource(rso.image); scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1, -1)); addView(View(this@MainActivity).apply { background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(bg, alpha(bg, 45))) }, FrameLayout.LayoutParams(-1, -1))
            addView(iconButton(R.drawable.ic_back) { show(previous) }, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.TOP or Gravity.START).margins(dp(18)))
            if (rso.verified) addView(text("\u2713 Official RSO", 10f, bg, true).apply { background = round(accent, 10); setPadding(dp(9), dp(5), dp(9), dp(5)) }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).margins(dp(18)))
            addView(vStack(Gravity.BOTTOM).apply { setPadding(dp(24), 0, dp(24), dp(16)); addView(text(rso.category.uppercase(Locale.getDefault()), 11f, accent, true)); addView(title(rso.name, 27f)) }, FrameLayout.LayoutParams(-1, -1))
        }, LinearLayout.LayoutParams(-1, dp(230)))
        val content = vStack().apply { setPadding(dp(24), dp(18), dp(24), dp(24)) }
        val stats = hStack(); listOf(Triple(rso.members.toString(), "Members", rso.color), Triple("8", "Events/sem", accent), Triple("4.8", "Rating", 0xFF00C9A7.toInt())).forEach { stats.addView(statCard(it.first, it.second, it.third), LinearLayout.LayoutParams(0, dp(76), 1f).end(dp(8))) }
        content.addView(stats, LinearLayout.LayoutParams(-1, dp(76)).bottom(dp(18))); content.addView(section("ABOUT")); content.addView(text(rso.description, 14f, 0xFFC8CDE0.toInt()), LinearLayout.LayoutParams(-1, -2).top(dp(7)).bottom(dp(14))); content.addView(ChipGroup(this).apply { isSingleLine = false; rso.tags.forEach { addView(chip(it, rso.color)) } }, LinearLayout.LayoutParams(-1, -2).bottom(dp(16)))
        val related = SenecData.events.filter { it.rsoId == rso.id }; if (related.isNotEmpty()) { content.addView(section("UPCOMING EVENTS"), LinearLayout.LayoutParams(-1, -2).bottom(dp(8))); related.forEach { content.addView(eventCard(it), LinearLayout.LayoutParams(-1, -2).bottom(dp(10))) } }
        content.addView(section("MEMBERS"), LinearLayout.LayoutParams(-1, -2).top(dp(6)).bottom(dp(8))); content.addView(text("MR    JC    AP    DB    LG     +${rso.members - 5} more", 13f, muted, true), LinearLayout.LayoutParams(-1, -2).bottom(dp(18)))
        val joinButton = button(if (isJoined) "\u2713 Joined - Welcome!" else "Join RSO", if (isJoined) secondary else rso.color, if (isJoined) muted else Color.WHITE) {}
        joinButton.setOnClickListener { isJoined = !isJoined; if (isJoined) joined.add(rso.id) else joined.remove(rso.id); joinButton.text = if (isJoined) "\u2713 Joined - Welcome!" else "Join RSO"; joinButton.backgroundTintList = ColorStateList.valueOf(if (isJoined) secondary else rso.color); joinButton.setTextColor(if (isJoined) muted else Color.WHITE) }
        content.addView(joinButton, LinearLayout.LayoutParams(-1, dp(52))); body.addView(content); return scroll(body)
    }

    private fun createRsoScreen(): View {
        val body = vStack().apply { setPadding(dp(24), dp(10), dp(24), dp(28)) }
        body.addView(hStack(Gravity.CENTER_VERTICAL).apply { addView(iconButton(R.drawable.ic_back) { show(previous) }); addView(vStack().apply { setPadding(dp(12), 0, 0, 0); addView(title("Create New RSO", 21f)); addView(text("Propose a new SENECApp organization", 12f, muted)) }) }, LinearLayout.LayoutParams(-1, -2).bottom(dp(18)))
        body.addView(text("RSO proposals are reviewed by Uniandes Student Affairs. Approved organizations receive campus resources, email lists, and official recognition.", 12f, accent).apply { background = round(alpha(accent, 22), 14, alpha(accent, 55)); setPadding(dp(15), dp(14), dp(15), dp(14)) }, LinearLayout.LayoutParams(-1, -2).bottom(dp(16)))
        val name = field("Organization Name", "e.g. Surf & Water Sports Club"); val email = field("Contact Email", "your@uniandes.edu.co", InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        body.addView(name.first); body.addView(name.second, LinearLayout.LayoutParams(-1, dp(52)).bottom(dp(14))); body.addView(email.first); body.addView(email.second, LinearLayout.LayoutParams(-1, dp(52)).bottom(dp(14))); body.addView(section("CATEGORY"), LinearLayout.LayoutParams(-1, -2).bottom(dp(7)))
        var category = ""; val group = ChipGroup(this).apply { isSingleLine = false; isSingleSelection = true }; val submit = button("Submit for Review", secondary, muted) {}
        fun validate() { val ready = name.second.text.isNotBlank() && category.isNotBlank(); submit.isEnabled = ready; submit.backgroundTintList = ColorStateList.valueOf(if (ready) primary else secondary); submit.setTextColor(if (ready) Color.WHITE else muted) }
        listOf("Sports", "Business", "Technology", "Arts", "Travel", "Cars", "Social", "International").forEach { item -> group.addView(chip(item).apply { isCheckable = true; setOnCheckedChangeListener { view, checked -> val selectedChip = view as Chip; if (checked) { category = item; selectedChip.chipBackgroundColor = ColorStateList.valueOf(primary); selectedChip.setTextColor(Color.WHITE) } else { selectedChip.chipBackgroundColor = ColorStateList.valueOf(card); selectedChip.setTextColor(muted) }; validate() } }) }
        body.addView(group, LinearLayout.LayoutParams(-1, -2).bottom(dp(14))); body.addView(section("DESCRIPTION"), LinearLayout.LayoutParams(-1, -2).bottom(dp(7)))
        body.addView(EditText(this).apply { hint = "Describe your RSO's mission, activities, and what students can expect..."; setHintTextColor(muted); setTextColor(fg); textSize = 14f; gravity = Gravity.TOP; typeface = nunito; background = round(card, 14, alpha(Color.WHITE, 20)); setPadding(dp(15), dp(13), dp(15), dp(13)) }, LinearLayout.LayoutParams(-1, dp(120)).bottom(dp(18)))
        name.second.addTextChangedListener(watcher { validate() }); submit.isEnabled = false; submit.setOnClickListener { submissionSuccess(name.second.text.toString()) }; body.addView(submit, LinearLayout.LayoutParams(-1, dp(52))); return scroll(body)
    }

    private fun submissionSuccess(name: String) {
        container.removeAllViews(); container.addView(vStack(Gravity.CENTER).apply { setPadding(dp(34), dp(30), dp(34), dp(30)); addView(text("\u2713", 40f, primary, true).apply { gravity = Gravity.CENTER; background = round(alpha(primary, 30), 22) }, LinearLayout.LayoutParams(dp(82), dp(82)).bottom(dp(20))); addView(title("Proposal Submitted!", 26f).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2).bottom(dp(10))); addView(text("Your RSO proposal for $name has been submitted to Uniandes Student Affairs for review. You will receive a response within 5 business days.", 14f, muted).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2).bottom(dp(24))); addView(button("Back to Discover", primary, Color.WHITE) { show(Screen.DISCOVER) }, LinearLayout.LayoutParams(-1, dp(52))) }, FrameLayout.LayoutParams(-1, -1))
    }

    private fun notificationsScreen(): View {
        val body = vStack().apply { setPadding(dp(24), dp(12), dp(24), dp(26)) }
        val items = listOf(Triple("Tennis Uniandes", "Round Robin Tournament is this Saturday at 8:00 AM.", primary), Triple("AI & ML Group", "New workshop added: LLM Agent Building - Wednesday 5 PM.", 0xFF3B82F6.toInt()), Triple("Emprendedores Uniandes", "Pitch Night #14 spots are filling up - reserve yours now.", 0xFFFF6B35.toInt()), Triple("SENECApp", "Welcome! You have 3 RSOs that match your interests.", accent))
        body.addView(hStack(Gravity.CENTER_VERTICAL).apply { addView(vStack().apply { addView(title("Notifications", 23f)); addView(text("3 unread", 12f, muted)) }, LinearLayout.LayoutParams(0, -2, 1f)); addView(button("Mark all read", secondary, muted) { Toast.makeText(this@MainActivity, "All caught up", Toast.LENGTH_SHORT).show() }); addView(iconButton(R.drawable.ic_back) { show(previous) }, LinearLayout.LayoutParams(dp(42), dp(42)).start(dp(8))) }, LinearLayout.LayoutParams(-1, -2).bottom(dp(16)))
        items.forEachIndexed { index, item -> body.addView(MaterialCardView(this).apply { radius = dp(16).toFloat(); cardElevation = 0f; setCardBackgroundColor(if (index < 3) card else 0xFF1A1F2C.toInt()); addView(hStack(Gravity.TOP).apply { setPadding(dp(14), dp(14), dp(14), dp(14)); addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.ic_notifications); imageTintList = ColorStateList.valueOf(item.third); background = round(alpha(item.third, 28), 12); setPadding(dp(10), dp(10), dp(10), dp(10)) }, LinearLayout.LayoutParams(dp(42), dp(42))); addView(vStack().apply { setPadding(dp(12), 0, 0, 0); addView(text(item.first, 12f, item.third, true)); addView(text(item.second, 14f, if (index < 3) fg else muted)); addView(text(listOf("2h ago", "5h ago", "1d ago", "2d ago")[index], 11f, muted), LinearLayout.LayoutParams(-1, -2).top(dp(5))) }, LinearLayout.LayoutParams(0, -2, 1f)); if (index < 3) addView(View(this@MainActivity).apply { background = round(primary, 4) }, LinearLayout.LayoutParams(dp(8), dp(8))) }) }, LinearLayout.LayoutParams(-1, -2).bottom(dp(10))) }
        return scroll(body)
    }

    private fun open(rso: Rso) { previous = current; selected = rso; show(Screen.DETAIL) }
    private fun field(label: String, hint: String, type: Int = InputType.TYPE_CLASS_TEXT) = section(label.uppercase(Locale.getDefault())) to EditText(this).apply { this.hint = hint; inputType = type; setHintTextColor(muted); setTextColor(fg); textSize = 14f; typeface = nunito; isSingleLine = true; background = round(card, 14, alpha(Color.WHITE, 20)); setPadding(dp(15), 0, dp(15), 0) }
    private fun statCard(value: String, caption: String, color: Int) = vStack(Gravity.CENTER).apply { background = round(card, 14, alpha(Color.WHITE, 16)); addView(text(value, 19f, color, true, true).apply { gravity = Gravity.CENTER }); addView(text(caption, 10f, muted).apply { gravity = Gravity.CENTER }) }
    private fun statText(value: String, caption: String) = vStack(Gravity.CENTER).apply { addView(text(value, 21f, fg, true, true).apply { gravity = Gravity.CENTER }); addView(text(caption, 11f, muted).apply { gravity = Gravity.CENTER }) }
    private fun chip(label: String, color: Int = fg) = Chip(this).apply { text = label; textSize = 12f; typeface = nunito; setTextColor(if (color == fg) fg else color); chipBackgroundColor = ColorStateList.valueOf(if (color == fg) secondary else alpha(color, 28)); chipStrokeWidth = 0f; chipCornerRadius = dp(10).toFloat(); isCheckable = false }
    private fun pill(label: String, active: Boolean, activeColor: Int = primary, activeText: Int = Color.WHITE, click: () -> Unit) = MaterialButton(this).apply { text = label; textSize = 12f; typeface = nunito; setTextColor(if (active) activeText else muted); backgroundTintList = ColorStateList.valueOf(if (active) activeColor else secondary); cornerRadius = dp(11); insetTop = 0; insetBottom = 0; minWidth = 0; minHeight = 0; setPadding(dp(14), 0, dp(14), 0); setOnClickListener { click() } }
    private fun button(label: String, color: Int, textColor: Int, click: () -> Unit) = MaterialButton(this).apply { text = label; textSize = 12f; typeface = bricolage; setTextColor(textColor); backgroundTintList = ColorStateList.valueOf(color); cornerRadius = dp(13); insetTop = 0; insetBottom = 0; minWidth = 0; minHeight = 0; setPadding(dp(13), 0, dp(13), 0); setOnClickListener { click() } }
    private fun iconButton(icon: Int, click: () -> Unit) = ImageButton(this).apply { setImageResource(icon); imageTintList = ColorStateList.valueOf(fg); background = round(secondary, 13); setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { click() }; layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)) }
    private fun photo(resource: Int, width: Int, height: Int) = ShapeableImageView(this).apply { setImageResource(resource); scaleType = ImageView.ScaleType.CENTER_CROP; shapeAppearanceModel = ShapeAppearanceModel.builder().setAllCornerSizes(dp(14).toFloat()).build(); layoutParams = LinearLayout.LayoutParams(dp(width), dp(height)) }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false, heading: Boolean = false) = TextView(this).apply { text = value; textSize = size; setTextColor(color); includeFontPadding = false; setLineSpacing(0f, 1.08f); setTypeface(if (heading) bricolage else nunito, if (bold) Typeface.BOLD else Typeface.NORMAL) }
    private fun title(value: String, size: Float, color: Int = fg) = text(value, size, color, true, true)
    private fun section(value: String) = text(value, 11f, muted, true)
    private fun vStack(gravity: Int = Gravity.NO_GRAVITY) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; this.gravity = gravity }
    private fun hStack(gravity: Int = Gravity.NO_GRAVITY) = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; this.gravity = gravity }
    private fun scroll(content: View) = ScrollView(this).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; setBackgroundColor(bg); addView(content, FrameLayout.LayoutParams(-1, -2)) }
    private fun round(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(radius).toFloat(); stroke?.let { setStroke(dp(1), it) } }
    private fun tinted(resource: Int, color: Int) = ResourcesCompat.getDrawable(resources, resource, theme)?.mutate()?.also { DrawableCompat.setTint(it, color) }
    private fun watcher(change: (String) -> Unit) = object : TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = change(s?.toString().orEmpty()); override fun afterTextChanged(s: Editable?) = Unit }
    private fun alpha(color: Int, amount: Int) = (color and 0x00FFFFFF) or (amount.coerceIn(0, 255) shl 24)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun LinearLayout.LayoutParams.top(value: Int) = apply { topMargin = value }
    private fun LinearLayout.LayoutParams.bottom(value: Int) = apply { bottomMargin = value }
    private fun LinearLayout.LayoutParams.start(value: Int) = apply { marginStart = value }
    private fun LinearLayout.LayoutParams.end(value: Int) = apply { marginEnd = value }
    private fun LinearLayout.LayoutParams.horizontal(value: Int) = apply { marginStart = value; marginEnd = value }
    private fun FrameLayout.LayoutParams.margins(value: Int) = apply { setMargins(value, value, value, value) }
}
