package com.ddh.agent.interfaces.dto.response;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

@Data
public class PageResponse<T> {
    private List<T> content;
    private long total;
    private int page;
    private int size;

    public static <T> PageResponse<T> of(IPage<?> page, List<T> content) {
        PageResponse<T> r = new PageResponse<>();
        r.setContent(content);
        r.setTotal(page.getTotal());
        r.setPage((int) page.getCurrent());
        r.setSize((int) page.getSize());
        return r;
    }
}
