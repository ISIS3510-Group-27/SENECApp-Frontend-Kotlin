package com.senecapp.data;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.relation.ToOne;

@Entity
public class JoinSnapshotSourceRecord {
    @Id public long id;
    public ToOne<JoinSnapshotRecord> snapshot;
    public int position;
    public String entryPoint;
    public int views;
    public int joins;
    public Double viewToJoinRate;
    public Double shareOfJoins;
    public Double campusViewToJoinRate;
}
