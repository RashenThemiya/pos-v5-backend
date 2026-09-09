package com.pos.system.dto.sale;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CustomerOrderPageResponse {
    private List<CustomerOrderViewResponse> content;
    private int page;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private String sort;
}
