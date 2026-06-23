package de.ptb.dcc.dtos;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public class PagedResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private boolean hasMore;
    private long total;

    public PagedResponse() {}

    public PagedResponse(List<T> content, int page, int size, boolean hasMore, long total) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.hasMore = hasMore;
        this.total = total;
    }

    public static <S, T> PagedResponse<T> from(Page<S> page, Function<S, T> mapper) {
        List<T> mapped = page.getContent().stream().map(mapper).toList();
        return new PagedResponse<>(
                mapped,
                page.getNumber(),
                page.getSize(),
                page.hasNext(),
                page.getTotalElements()
        );
    }

    public static <T> PagedResponse<T> of(List<T> content, int page, int size, long total) {
        boolean hasMore = (long) (page + 1) * size < total;
        return new PagedResponse<>(content, page, size, hasMore, total);
    }

    public List<T> getContent() { return content; }
    public void setContent(List<T> content) { this.content = content; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public boolean isHasMore() { return hasMore; }
    public void setHasMore(boolean hasMore) { this.hasMore = hasMore; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}
