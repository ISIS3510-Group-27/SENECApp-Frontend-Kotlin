package com.senecapp.data;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Unique;

@Entity
public class ProfileRecommendationRecord {
    @Id public long id;
    @Unique public String ownerUid;
    public long savedAt;
    public String payload;
}
