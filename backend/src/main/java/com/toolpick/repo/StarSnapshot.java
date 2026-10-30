package com.toolpick.repo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** 날짜별 스타 수. (repo_id, snapshot_date)가 UNIQUE라 같은 날 다시 수집하면 덮어쓴다 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StarSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repo_id")
    private Repo repo;

    @Column(nullable = false)
    private LocalDate snapshotDate;

    @Column(nullable = false)
    private int stars;
}
