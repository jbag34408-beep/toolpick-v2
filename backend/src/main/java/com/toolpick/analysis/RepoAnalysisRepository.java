package com.toolpick.analysis;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RepoAnalysisRepository extends JpaRepository<RepoAnalysis, Long> {

    @EntityGraph(attributePaths = {"repo", "category"})
    List<RepoAnalysis> findByStatusOrderByIdAsc(AnalysisStatus status);

    @EntityGraph(attributePaths = "category")
    List<RepoAnalysis> findByRepoIdIn(Collection<Long> repoIds);

    @EntityGraph(attributePaths = "category")
    Optional<RepoAnalysis> findByRepoId(Long repoId);
}
