package com.workstudy.common;

import java.util.List;

public class PageResult<T> {
    private List<T> records;
    private Long total;
    private Integer page;
    private Integer pageSize;

    public static <T> PageResult<T> of(List<T> records, Long total, Integer page, Integer pageSize) {
        PageResult<T> r = new PageResult<>();
        r.records = records;
        r.total = total;
        r.page = page;
        r.pageSize = pageSize;
        return r;
    }

    public List<T> getRecords() { return records; }
    public void setRecords(List<T> records) { this.records = records; }
    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
