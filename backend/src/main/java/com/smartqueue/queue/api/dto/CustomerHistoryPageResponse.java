package com.smartqueue.queue.api.dto;

import java.util.List;

public class CustomerHistoryPageResponse {
    private List<QueueTokenResponse> items;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;

    public CustomerHistoryPageResponse() {}

    public CustomerHistoryPageResponse(List<QueueTokenResponse> items, long totalElements, int totalPages, int page, int size) {
        this.items = items;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.page = page;
        this.size = size;
    }

    public List<QueueTokenResponse> getItems() { return items; }
    public void setItems(List<QueueTokenResponse> items) { this.items = items; }

    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
