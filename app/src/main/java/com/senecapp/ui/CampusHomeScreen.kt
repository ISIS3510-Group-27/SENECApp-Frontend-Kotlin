package com.senecapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.senecapp.data.*

@Composable
internal fun CampusHomeScreen(uid: String, profile: UserProfile, sensorContrast: Boolean,
    profileLoading: Boolean, profileError: String?, onRefreshProfile: () -> Unit, onSignOut: () -> Unit,
    provider: ViewModelProvider) {
    val home = remember(provider) { provider[CampusHomeViewModel::class.java] }
    val organizations = remember(provider) { provider[OrganizationsViewModel::class.java] }
    val recommendations = remember(provider) { provider[GroupRecommendationsViewModel::class.java] }
    val profileRecommendations = remember(provider) { provider["profileRecommendations", GroupRecommendationsViewModel::class.java] }
    val events = remember(provider) { provider[CampusEventsViewModel::class.java] }
    val myRsos = remember(provider) { provider[MyRSOsViewModel::class.java] }
    val freeNow = remember(provider) { provider[FreeNowViewModel::class.java] }
    val createGroup = remember(provider) { provider[CreateGroupViewModel::class.java] }
    val checkIn = remember(provider) { provider[EventCheckInViewModel::class.java] }
    var selectedTab by rememberSaveable(uid) { mutableStateOf("Discover") }
    var discoverStack by rememberSaveable(uid) { mutableStateOf(listOf("root")) }
    var eventsStack by rememberSaveable(uid) { mutableStateOf(listOf("root")) }
    var myStack by rememberSaveable(uid) { mutableStateOf(listOf("root")) }
    var profileStack by rememberSaveable(uid) { mutableStateOf(listOf("root")) }
    val stack = when (selectedTab) { "Events" -> eventsStack; "My RSOs" -> myStack; "Profile" -> profileStack; else -> discoverStack }
    fun setStack(routes: List<String>) {
        when (selectedTab) { "Events" -> eventsStack = routes; "My RSOs" -> myStack = routes; "Profile" -> profileStack = routes; else -> discoverStack = routes }
    }
    fun navigate(route: String) { home.clearError(); setStack(stack + route) }
    fun back() {
        if (home.busy || createGroup.state.submitting) return
        if (stack.size > 1) setStack(stack.dropLast(1)) else selectedTab = "Discover"
    }
    fun group(id: Int, source: String = "direct", requestId: String? = null) { navigate("group:$id:$source:${requestId.orEmpty()}") }
    fun event(id: Int, source: String = "events", requestId: String? = null) {
        checkIn.clear(); navigate("event:$id:$source:${requestId.orEmpty()}")
    }
    fun changed() {
        myRsos.refresh(); organizations.refresh(); recommendations.load(); home.refresh()
    }
    val route = stack.last()
    val parts = route.split(":")
    val contrast = when (home.outdoorMode) { "On" -> true; "Off" -> false; else -> sensorContrast }
    val screenStates = rememberSaveableStateHolder()
    LaunchedEffect(uid) { home.start(uid); myRsos.load(); events.load(false) }
    LaunchedEffect(route) {
        when (parts[0]) {
            "group" -> home.openGroup(parts[1].toInt(), parts[2], parts.getOrNull(3)?.takeIf { it.isNotBlank() })
            "event" -> {
                val id = parts[1].toInt()
                val preview = events.state.items.firstOrNull { it.id == id } ?: home.detail?.events?.firstOrNull { it.id == id }
                events.openId(id, parts[2], preview, parts.getOrNull(3)?.takeIf { it.isNotBlank() })
            }
            "createEvent" -> if (home.detail?.group?.id != parts[1].toInt()) home.openGroup(parts[1].toInt())
        }
    }
    LaunchedEffect(checkIn.state.result) {
        if (checkIn.state.result != null) {
            checkIn.state.eventId?.let(events::checkedIn); home.refresh()
        }
    }
    BackHandler(enabled = stack.size > 1 || selectedTab != "Discover") { back() }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(Modifier.weight(1f)) {
            screenStates.SaveableStateProvider("$selectedTab:$route") {
                when (parts[0]) {
                    "group" -> GroupDetailsScreen(home, parts[1].toInt(), ::back, ::changed,
                        { event(it.id, "group_detail") }, { navigate("createEvent:${parts[1]}") }, { navigate("admins:${parts[1]}") })
                    "event" -> EventDetailsScreen(events.state, parts[1].toInt(), parts[1].toInt() in home.savedEvents,
                        home.schedule, myRsos.state.mine.any { it.id == events.state.selected?.groupId && it.role == "admin" },
                        checkIn, ::back, { events.openId(parts[1].toInt(), parts[2], requestId = parts.getOrNull(3)?.takeIf { it.isNotBlank() }) },
                        { home.toggleEvent(parts[1].toInt()) }, { group(it, "event") })
                    "filters" -> GroupFiltersScreen(organizations.state.filters, home.interests, home.buildings,
                        { organizations.filter(it); back() }, ::back)
                    "notices" -> NotificationsScreen(home, ::back) {
                        when { it.eventId != null -> event(it.eventId, "notification")
                            it.groupId != null -> group(it.groupId, "notification") }
                    }
                    "schedule" -> ScheduleScreen(home, ::back)
                    "itinerary" -> ItineraryScreen(events.state.items, home.savedEvents, home::toggleEvent,
                        { event(it.id) }, ::back)
                    "recommendations" -> RecommendationsScreen(recommendations.state, ::back) {
                        group(it, "recommendation", recommendations.state.result?.requestId)
                    }
                    "free" -> {
                        LaunchedEffect(Unit) { freeNow.load() }
                        DemoView("Free right now?", showSampleLabel = false, header = { BackLink(::back) }) {
                            FreeNowSection(freeNow.state, freeNow::load, freeNow::loadDemoNoon,
                                { event(it, "free_now", freeNow.state.result?.requestId) }, freeNow::setLocationConsent, freeNow::refreshConsent,
                                freeNow::useLocation, freeNow::locationUnavailable, externalDetails = true)
                        }
                    }
                    "createEvent" -> CreateEventScreen(home, ::back) { events.load(false); changed(); back() }
                    "admins" -> AdminRequestsScreen(parts[1].toInt(), ::back)
                    "createGroup" -> CreateGroupScreen(state = createGroup.state, onLoadCatalogue = createGroup::loadCatalogue,
                        onName = createGroup::setName, onCategory = createGroup::setCategory,
                        onDescription = createGroup::setDescription, onContactEmail = createGroup::setContactEmail,
                        onToggleInterest = createGroup::toggleInterest, onSubmit = createGroup::submit,
                        onDone = { createGroup.reset(); changed(); back() }, onBack = ::back)
                    else -> when (selectedTab) {
                        "Events" -> EventFeedScreen(events.state, home.savedEvents, myRsos.state.mine.map { it.id }.toSet(),
                            { events.load(false) }, home::toggleEvent, { event(it.id) }, { navigate("free") }, { navigate("itinerary") },
                            onScan = { id, code, coordinates ->
                                event(id)
                                checkIn.checkIn(id, code, coordinates, if (coordinates == null) "Checked in without location." else null)
                            })
                        "My RSOs" -> MyRSOsScreen(myRsos.state, myRsos::ensureLoaded, { navigate("createGroup") }, home.attended, { group(it) })
                        "Profile" -> ProfileScreen(profile, profileLoading, profileError ?: home.error, onRefreshProfile, onSignOut,
                            profileRecommendations.state, profileRecommendations::load,
                            { group(it, "recommendation", profileRecommendations.state.result?.requestId) },
                            profileRecommendations::joinGroup, joinedCount = if (myRsos.state.loading) null else myRsos.state.mine.size,
                            attended = home.attended, outdoorMode = home.outdoorMode, onOutdoorMode = home::outdoor,
                            onLocation = { home.setLocation(it) { onRefreshProfile(); freeNow.refreshConsent() } },
                            onSchedule = { navigate("schedule") }, settingsBusy = home.busy, externalDetails = true)
                        else -> DiscoverScreen(showBottomNavigation = false, highContrast = contrast,
                            organizationsState = organizations.state, onSearch = organizations::search,
                            onRefresh = organizations::refresh,
                            onOpenOrganization = { id, search -> group(id, if (search) "search" else "explore") },
                            onToggleSave = organizations::toggleSave,
                            recommendationsState = recommendations.state, onLoadRecommendations = recommendations::load,
                            onOpenRecommendedGroup = { group(it, "recommendation", recommendations.state.result?.requestId) },
                            externalDetails = true, unreadCount = home.unread, onNotifications = { navigate("notices") },
                            onFilters = { navigate("filters") }, onSeeAll = { navigate("recommendations") },
                            onCreateGroup = { navigate("createGroup") })
                    }
                }
            }
        }
        if (parts[0] != "createGroup") AppBottomNavigation(selectedTab, contrast) {
            if (!home.busy) {
                if (it == selectedTab) setStack(listOf("root")) else selectedTab = it
            }
        }
    }
}

@Composable
private fun RecommendationsScreen(state: GroupRecommendationsUiState, onBack: () -> Unit, onOpen: (Int) -> Unit) {
    DemoView("Recommended for you", showSampleLabel = false, header = { BackLink(onBack) }) {
        ViewText("Recommended for you", heading = true)
        state.result?.items.orEmpty().forEach { group ->
            Box(Modifier.clickable { onOpen(group.id) }) {
                ViewCard {
                    OrganizationImage(group.name, group.category, organizationColor(group.color),
                        Modifier.fillMaxWidth().height(120.dp), group.imageUrl)
                    ViewText(group.name + if (group.verified) " ✓" else "", true)
                    ViewText("${group.memberCount} members · ${group.category}", color = viewMuted)
                    group.reasons.forEach { ViewText(it, color = viewAccent) }
                }
            }
        }
        state.error?.let { InfoNotice(it, true) }
    }
}
