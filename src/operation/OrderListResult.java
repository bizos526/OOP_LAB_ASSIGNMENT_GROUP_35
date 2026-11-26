package operation;

import java.util.ArrayList;
import java.util.List;

public class OrderListResult {
    private List<Order> orderList;
    private int currentPage;
    private int totalPages;

    public OrderListResult(List<Order> orderList, int currentPage, int totalPages) {
        this.orderList = (orderList != null) ? orderList : new ArrayList<>();
        this.currentPage = currentPage;
        this.totalPages = totalPages;
    }

    public List<Order> getOrderList() {
        return orderList;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPages() {
        return totalPages;
    }
}
