package com.toolpick.category;

import com.toolpick.common.config.CacheConfig;
import com.toolpick.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Cacheable(CacheConfig.CATEGORIES)
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAllByOrderBySortOrderAsc().stream().map(CategoryResponse::from).toList();
    }

    public Category getBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("카테고리가 없습니다: " + slug));
    }
}
