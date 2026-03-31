package com.kiwi.project.system.spi;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

public interface DictProvider
{
    String group();

    Page<Dict> getDict(@Nullable String pattern, Pageable pageable);
}
