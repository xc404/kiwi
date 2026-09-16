package com.kiwi.project.ai.authoring;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按 sequenceFlow 做分层布局（列 = 从起点的最长路径，行 = 同层分支）。
 * <p>
 * 前端虽有 bpmn-io 的 {@code bpmn-auto-layout}，但它用裸 {@code bpmn-moddle} 重写 XML，
 * 会丢掉 {@code kiwi:*} / {@code camunda:*}；坐标必须在编译器里算，属性才能一起发出去。
 */
public class AiWorkflowPlanLayout {

    private static final int OriginX = 160;
    private static final int OriginY = 160;
    private static final int ColumnGap = 80;
    private static final int RowStride = 160;

    public Map<String, Box> layout(AiWorkflowPlan plan) {
        Map<String, Integer> column = assignColumns(plan);
        Map<String, Integer> row = assignRows(plan, column);
        int maxCol = 0;
        for (int c : column.values()) {
            maxCol = Math.max(maxCol, c);
        }
        int[] colWidth = new int[maxCol + 1];
        for (int i = 0; i <= maxCol; i++) {
            colWidth[i] = 36;
        }
        for (AiWorkflowPlan.Node node : plan.getNodes()) {
            int col = column.getOrDefault(node.getId(), 0);
            colWidth[col] = Math.max(colWidth[col], nodeWidth(node.getType()));
        }
        int[] colX = new int[maxCol + 1];
        colX[0] = OriginX;
        for (int i = 1; i <= maxCol; i++) {
            colX[i] = colX[i - 1] + colWidth[i - 1] + ColumnGap;
        }
        Map<String, Box> boxes = new LinkedHashMap<>();
        for (AiWorkflowPlan.Node node : plan.getNodes()) {
            int width = nodeWidth(node.getType());
            int height = nodeHeight(node.getType());
            int col = column.getOrDefault(node.getId(), 0);
            int r = row.getOrDefault(node.getId(), 0);
            int x = colX[col] + (colWidth[col] - width) / 2;
            int y = OriginY + r * RowStride - height / 2;
            boxes.put(node.getId(), new Box(x, y, width, height));
        }
        return boxes;
    }

    private Map<String, Integer> assignColumns(AiWorkflowPlan plan) {
        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        Map<String, Integer> indegree = new LinkedHashMap<>();
        for (AiWorkflowPlan.Node node : plan.getNodes()) {
            outgoing.put(node.getId(), new ArrayList<>());
            indegree.put(node.getId(), 0);
        }
        if (plan.getFlows() != null) {
            for (AiWorkflowPlan.Flow flow : plan.getFlows()) {
                if (flow == null
                        || !outgoing.containsKey(flow.getSourceRef())
                        || !indegree.containsKey(flow.getTargetRef())) {
                    continue;
                }
                outgoing.get(flow.getSourceRef()).add(flow.getTargetRef());
                String targetId = flow.getTargetRef();
                indegree.put(targetId, indegree.get(targetId) + 1);
            }
        }
        Map<String, Integer> column = new LinkedHashMap<>();
        ArrayDeque<String> ready = new ArrayDeque<>();
        for (AiWorkflowPlan.Node node : plan.getNodes()) {
            if (indegree.get(node.getId()) == 0) {
                ready.add(node.getId());
                column.put(node.getId(), 0);
            }
        }
        while (!ready.isEmpty()) {
            String current = ready.removeFirst();
            int nextCol = column.get(current) + 1;
            for (String target : outgoing.get(current)) {
                int currentCol = column.getOrDefault(target, 0);
                if (nextCol > currentCol) {
                    column.put(target, nextCol);
                }
                int remaining = indegree.get(target) - 1;
                indegree.put(target, remaining);
                if (remaining == 0) {
                    ready.add(target);
                }
            }
        }
        int fallback = 0;
        for (int c : column.values()) {
            fallback = Math.max(fallback, c);
        }
        fallback++;
        for (AiWorkflowPlan.Node node : plan.getNodes()) {
            column.putIfAbsent(node.getId(), fallback);
        }
        return column;
    }

    private Map<String, Integer> assignRows(AiWorkflowPlan plan, Map<String, Integer> column) {
        Map<String, List<String>> incoming = new LinkedHashMap<>();
        Map<String, Integer> index = new LinkedHashMap<>();
        int i = 0;
        for (AiWorkflowPlan.Node node : plan.getNodes()) {
            incoming.put(node.getId(), new ArrayList<>());
            index.put(node.getId(), i++);
        }
        if (plan.getFlows() != null) {
            for (AiWorkflowPlan.Flow flow : plan.getFlows()) {
                if (flow == null || !incoming.containsKey(flow.getTargetRef())) {
                    continue;
                }
                incoming.get(flow.getTargetRef()).add(flow.getSourceRef());
            }
        }
        int maxCol = 0;
        for (int c : column.values()) {
            maxCol = Math.max(maxCol, c);
        }
        Map<String, Integer> row = new LinkedHashMap<>();
        for (int col = 0; col <= maxCol; col++) {
            List<AiWorkflowPlan.Node> inColumn = new ArrayList<>();
            for (AiWorkflowPlan.Node node : plan.getNodes()) {
                if (column.getOrDefault(node.getId(), 0) == col) {
                    inColumn.add(node);
                }
            }
            int columnIndex = col;
            inColumn.sort((a, b) -> {
                int cmp = Double.compare(
                        preferredRow(a.getId(), incoming, row, column, columnIndex, index),
                        preferredRow(b.getId(), incoming, row, column, columnIndex, index));
                if (cmp != 0) {
                    return cmp;
                }
                return Integer.compare(index.get(a.getId()), index.get(b.getId()));
            });
            int r = 0;
            for (AiWorkflowPlan.Node node : inColumn) {
                row.put(node.getId(), r++);
            }
        }
        return row;
    }

    private double preferredRow(
            String nodeId,
            Map<String, List<String>> incoming,
            Map<String, Integer> row,
            Map<String, Integer> column,
            int nodeColumn,
            Map<String, Integer> index) {
        List<String> preds = incoming.get(nodeId);
        if (preds == null || preds.isEmpty()) {
            return index.getOrDefault(nodeId, 0);
        }
        double sum = 0;
        int count = 0;
        for (String pred : preds) {
            if (!row.containsKey(pred)) {
                continue;
            }
            if (column.getOrDefault(pred, 0) >= nodeColumn) {
                continue;
            }
            sum += row.get(pred);
            count++;
        }
        return count == 0 ? index.getOrDefault(nodeId, 0) : sum / count;
    }

    private int nodeWidth(String type) {
        return switch (StringUtils.defaultString(type)) {
            case "startEvent", "endEvent" -> 36;
            case "exclusiveGateway" -> 50;
            default -> 100;
        };
    }

    private int nodeHeight(String type) {
        return switch (StringUtils.defaultString(type)) {
            case "startEvent", "endEvent" -> 36;
            case "exclusiveGateway" -> 50;
            default -> 80;
        };
    }

    public record Box(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int centerY() {
            return y + height / 2;
        }
    }
}
