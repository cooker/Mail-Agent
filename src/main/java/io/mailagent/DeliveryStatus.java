package io.mailagent;
public enum DeliveryStatus {
    PENDING("待发送"), SENDING("发送中"), RETRY("等待重试"), SENT("已发送"), FAILED("失败"), UNKNOWN("结果未知");
    private final String label;
    DeliveryStatus(String label) { this.label=label; }
    public String getLabel() { return label; }
}
