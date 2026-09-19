package io.mailagent;
public enum SubjectMode {
    CONTAINS("包含"), EXACT("完全匹配"), REGEX("正则表达式");
    private final String label;
    SubjectMode(String label) { this.label=label; }
    public String getLabel() { return label; }
}
