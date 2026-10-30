package com.toolpick.category;

public record CategoryResponse(String slug, String nameKo) {

    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getSlug(), c.getNameKo());
    }
}
