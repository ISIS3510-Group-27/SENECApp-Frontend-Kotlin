package com.senecapp.data;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;
import io.objectbox.annotation.Index;

@Entity
public class JoinSnapshotRecord {
    @Id public long id;
    @Index public String account;
    public int groupId;
    public long takenAt;
    public int days;
    public int views;
    public int joins;
    public String question;
    public String answer;
    public String campusAnswer;
}
