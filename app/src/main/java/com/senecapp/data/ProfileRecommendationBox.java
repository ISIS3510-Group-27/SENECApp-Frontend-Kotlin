package com.senecapp.data;

import android.content.Context;
import java.io.File;
import io.objectbox.BoxStore;
import io.objectbox.query.Query;
import io.objectbox.query.QueryBuilder;

// Keep generated ObjectBox references in Java so AGP's Kotlin compilation can run before javac.
final class ProfileRecommendationBox {
    static BoxStore open(Context context, File directory) {
        return MyObjectBox.builder().androidContext(context).directory(directory).build();
    }

    static ProfileRecommendationRecord find(BoxStore store, String uid) {
        try (Query<ProfileRecommendationRecord> query = store.boxFor(ProfileRecommendationRecord.class)
                .query(ProfileRecommendationRecord_.ownerUid.equal(uid, QueryBuilder.StringOrder.CASE_SENSITIVE))
                .build()) {
            return query.findUnique();
        }
    }

    private ProfileRecommendationBox() {}
}
