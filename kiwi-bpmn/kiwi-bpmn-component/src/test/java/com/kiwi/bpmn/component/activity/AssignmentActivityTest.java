package com.kiwi.bpmn.component.activity;

import org.camunda.bpm.engine.delegate.Expression;
import org.camunda.bpm.engine.impl.el.ExpressionManager;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentActivityTest {

    private static AssignmentActivity activity(ExpressionManager expressionManager) {
        return new AssignmentActivity(expressionManager);
    }

    @Test
    void applyAssignments_literals() {
        ActivityExecution execution = mock(ActivityExecution.class);
        List<Assignment> items = List.of(
                new Assignment("x", 1),
                new Assignment("flag", true),
                new Assignment("msg", "hi"));
        activity(mock(ExpressionManager.class)).applyAssignments(execution, items);
        verify(execution).setVariable("x", 1);
        verify(execution).setVariable("flag", true);
        verify(execution).setVariable("msg", "hi");
    }

    @Test
    void applyAssignments_variableRef() {
        ExpressionManager em = mock(ExpressionManager.class);
        Expression expr = mock(Expression.class);
        when(em.createExpression("${a}")).thenReturn(expr);
        when(expr.getValue(any())).thenReturn(5);
        ActivityExecution execution = mock(ActivityExecution.class);
        activity(em).applyAssignments(execution, List.of(new Assignment("b", "${a}")));
        verify(execution).setVariable("b", 5);
    }

    @Test
    void applyAssignments_variableRef_missingSource() {
        ExpressionManager em = mock(ExpressionManager.class);
        Expression expr = mock(Expression.class);
        when(em.createExpression("${nope}")).thenReturn(expr);
        when(expr.getValue(any())).thenThrow(new IllegalArgumentException("missing"));
        ActivityExecution execution = mock(ActivityExecution.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> activity(em).applyAssignments(execution, List.of(new Assignment("b", "${nope}"))));
    }

    @Test
    void resolveAssignmentsListImpl_invalidJsonString() {
        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariable("assignments")).thenReturn("not-json");
        assertThrows(
                IllegalArgumentException.class,
                () -> AssignmentActivity.resolveAssignmentsListImpl(execution));
    }

    @Test
    void resolveAssignmentsListImpl_jsonArrayString() {
        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariable("assignments"))
                .thenReturn("[{\"key\":\"x\",\"value\":1},{\"key\":\"y\",\"value\":\"z\"}]");
        List<Assignment> list = AssignmentActivity.resolveAssignmentsListImpl(execution);
        assertEquals(2, list.size());
        assertEquals("x", list.get(0).getKey());
        assertEquals(1, list.get(0).getValue());
        assertEquals("y", list.get(1).getKey());
        assertEquals("z", list.get(1).getValue());
    }

    @Test
    void resolveAssignmentsListImpl_asListOfMaps() {
        ActivityExecution execution = mock(ActivityExecution.class);
        List<Map<String, Object>> data = List.of(Map.of("key", "a", "value", 1));
        when(execution.getVariable("assignments")).thenReturn(data);
        List<Assignment> list = AssignmentActivity.resolveAssignmentsListImpl(execution);
        assertEquals(1, list.size());
        assertEquals("a", list.get(0).getKey());
        assertEquals(1, list.get(0).getValue());
    }

    @Test
    void resolveAssignmentsListImpl_asListOfAssignment() {
        ActivityExecution execution = mock(ActivityExecution.class);
        List<Assignment> data = List.of(new Assignment("k", "v"));
        when(execution.getVariable("assignments")).thenReturn(data);
        List<Assignment> list = AssignmentActivity.resolveAssignmentsListImpl(execution);
        assertEquals(1, list.size());
        assertEquals("k", list.get(0).getKey());
        assertEquals("v", list.get(0).getValue());
    }

    @Test
    void applyAssignments_stringLiteral_notTreatedAsRef() {
        ActivityExecution execution = mock(ActivityExecution.class);
        activity(mock(ExpressionManager.class))
                .applyAssignments(
                        execution, List.of(new Assignment("hint", "prefix_${a}_suffix")));
        verify(execution).setVariable("hint", "prefix_${a}_suffix");
    }

    @Test
    void resolveAssignmentsListImpl_itemNotConvertible() {
        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariable("assignments")).thenReturn(List.of("bad"));
        assertThrows(
                IllegalArgumentException.class,
                () -> AssignmentActivity.resolveAssignmentsListImpl(execution));
    }
}
