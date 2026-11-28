package com.senecapp.data

import android.content.Context
import io.objectbox.BoxStore

internal class JoinSourcesStore(private val database: () -> BoxStore) : JoinSourcesStorage {
    constructor(context: Context) : this({ JoinSourcesBox.open(context.applicationContext) })

    override fun history(key: JoinSourcesKey): List<JoinSourcesVisit> {
        val store = database()
        return store.callInReadTx {
            JoinSourcesBox.visits(store, key.account, key.groupId).map { row ->
                val sources = JoinSourcesBox.sources(store, row.id).map {
                    JoinSource(it.entryPoint, it.views, it.joins, it.viewToJoinRate,
                        it.shareOfJoins, it.campusViewToJoinRate)
                }
                JoinSourcesVisit(row.takenAt, JoinSources(row.question, row.answer, row.campusAnswer,
                    row.days, row.views, row.joins, sources))
            }
        }
    }

    override fun save(key: JoinSourcesKey, visit: JoinSourcesVisit) {
        val store = database()
        store.runInTx {
            val history = JoinSourcesBox.visits(store, key.account, key.groupId)
            remove(store, replacedJoinVisits(history, visit.takenAt) { it.takenAt })
            val data = visit.data
            val id = store.boxFor(JoinSnapshotRecord::class.java).put(JoinSnapshotRecord().apply {
                account = key.account
                groupId = key.groupId
                takenAt = visit.takenAt
                days = data.days
                views = data.views
                joins = data.joins
                question = data.question
                answer = data.answer
                campusAnswer = data.campusAnswer
            })
            val sources = store.boxFor(JoinSnapshotSourceRecord::class.java)
            data.sources.forEachIndexed { index, value ->
                sources.put(JoinSnapshotSourceRecord().apply {
                    snapshot.targetId = id
                    position = index
                    entryPoint = value.entryPoint
                    views = value.views
                    joins = value.joins
                    viewToJoinRate = value.viewToJoinRate
                    shareOfJoins = value.shareOfJoins
                    campusViewToJoinRate = value.campusViewToJoinRate
                })
            }
        }
    }

    override fun clear(key: JoinSourcesKey) {
        val store = database()
        store.runInTx { remove(store, JoinSourcesBox.visits(store, key.account, key.groupId)) }
    }

    private fun remove(store: BoxStore, records: List<JoinSnapshotRecord>) {
        val sources = store.boxFor(JoinSnapshotSourceRecord::class.java)
        val visits = store.boxFor(JoinSnapshotRecord::class.java)
        records.forEach { record ->
            JoinSourcesBox.sources(store, record.id).forEach { sources.remove(it.id) }
            visits.remove(record.id)
        }
    }
}
