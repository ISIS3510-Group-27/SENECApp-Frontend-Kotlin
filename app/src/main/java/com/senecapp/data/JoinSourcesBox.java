package com.senecapp.data;

import android.content.Context;
import io.objectbox.BoxStore;
import io.objectbox.query.Query;
import io.objectbox.query.QueryBuilder;
import java.util.List;

// Keep generated ObjectBox references on the Java side, as in ProfileRecommendationBox.
final class JoinSourcesBox {
    private static BoxStore database;

    static synchronized BoxStore open(Context context) {
        if (database == null) {
            database = ProfileRecommendationBox.open(context,
                    new java.io.File(context.getNoBackupFilesDir(), "join-sources"));
        }
        return database;
    }

    static List<JoinSnapshotRecord> visits(BoxStore store, String account, int groupId) {
        try (Query<JoinSnapshotRecord> query = store.boxFor(JoinSnapshotRecord.class)
                .query(JoinSnapshotRecord_.account.equal(account, QueryBuilder.StringOrder.CASE_SENSITIVE)
                        .and(JoinSnapshotRecord_.groupId.equal(groupId)))
                .orderDesc(JoinSnapshotRecord_.takenAt).orderDesc(JoinSnapshotRecord_.id).build()) {
            return query.find();
        }
    }

    static List<JoinSnapshotSourceRecord> sources(BoxStore store, long snapshotId) {
        try (Query<JoinSnapshotSourceRecord> query = store.boxFor(JoinSnapshotSourceRecord.class)
                .query(JoinSnapshotSourceRecord_.snapshotId.equal(snapshotId))
                .order(JoinSnapshotSourceRecord_.position).build()) {
            return query.find();
        }
    }

    private JoinSourcesBox() {}
}
