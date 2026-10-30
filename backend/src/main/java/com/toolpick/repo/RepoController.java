package com.toolpick.repo;

import com.toolpick.repo.dto.RepoDetailResponse;
import com.toolpick.repo.dto.StarPointResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/repos/{owner}/{name}")
public class RepoController {

    private final RepoService repoService;

    @GetMapping
    public RepoDetailResponse detail(@PathVariable String owner, @PathVariable String name) {
        return repoService.getDetail(owner, name);
    }

    @GetMapping("/stars")
    public List<StarPointResponse> stars(@PathVariable String owner, @PathVariable String name,
                                         @RequestParam(defaultValue = "30") int days) {
        return repoService.getStarHistory(owner, name, days);
    }
}
