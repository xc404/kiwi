package com.kiwi.bpmn.component.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.operaton.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.operaton.bpm.engine.variable.Variables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoActivityTest {

    private MongoTemplate mongoTemplate;
    private MongoActivity activity;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        activity = spy(new MongoActivity(mongoTemplate));
    }

    @Test
    void execute_findOne_writesDocument() throws Exception {
        Document doc = new Document("_id", "1").append("name", "kiwi");
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("items"))).thenReturn(doc);

        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("collection")).thenReturn(Variables.stringValue("items"));
        when(execution.getVariableTyped("operation")).thenReturn(Variables.stringValue("findOne"));
        when(execution.getVariableTyped("filter")).thenReturn(Variables.stringValue("{\"name\":\"kiwi\"}"));
        when(execution.getVariableTyped("result")).thenReturn(Variables.stringValue("out"));
        doNothing().when(activity).leave(any());

        activity.execute(execution);

        verify(execution).setVariable(eq("out"), eq(doc));
        verify(activity).leave(execution);
    }

    @Test
    void execute_insert_writesInsertedDocument() throws Exception {
        Document inserted = new Document("name", "new");
        when(mongoTemplate.insert(any(Document.class), eq("items"))).thenReturn(inserted);

        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("collection")).thenReturn(Variables.stringValue("items"));
        when(execution.getVariableTyped("operation")).thenReturn(Variables.stringValue("insert"));
        when(execution.getVariableTyped("filter")).thenReturn(null);
        when(execution.getVariableTyped("document")).thenReturn(Variables.stringValue("{\"name\":\"new\"}"));
        when(execution.getVariableTyped("result")).thenReturn(Variables.stringValue("out"));
        doNothing().when(activity).leave(any());

        activity.execute(execution);

        verify(execution).setVariable(eq("out"), eq(inserted));
    }

    @Test
    void execute_count_writesCount() throws Exception {
        when(mongoTemplate.count(any(Query.class), eq("items"))).thenReturn(42L);

        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("collection")).thenReturn(Variables.stringValue("items"));
        when(execution.getVariableTyped("operation")).thenReturn(Variables.stringValue("count"));
        when(execution.getVariableTyped("filter")).thenReturn(Variables.stringValue("{}"));
        when(execution.getVariableTyped("result")).thenReturn(Variables.stringValue("total"));
        doNothing().when(activity).leave(any());

        activity.execute(execution);

        verify(execution).setVariable(eq("total"), eq(42L));
    }

    @Test
    void execute_invalidFilterJson_throws() {
        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("collection")).thenReturn(Variables.stringValue("items"));
        when(execution.getVariableTyped("operation")).thenReturn(Variables.stringValue("findOne"));
        when(execution.getVariableTyped("filter")).thenReturn(Variables.stringValue("{"));

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> activity.execute(execution));
        assertTrue(ex.getMessage().contains("filter"));
    }

    @Test
    void execute_blankCollection_throws() {
        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("collection")).thenReturn(Variables.stringValue("  "));

        assertThrows(IllegalArgumentException.class, () -> activity.execute(execution));
    }

    @Test
    void execute_blankResultVar_skipsSetVariable() throws Exception {
        when(mongoTemplate.count(any(Query.class), anyString())).thenReturn(0L);

        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("collection")).thenReturn(Variables.stringValue("items"));
        when(execution.getVariableTyped("operation")).thenReturn(Variables.stringValue("count"));
        when(execution.getVariableTyped("filter")).thenReturn(null);
        when(execution.getVariableTyped("result")).thenReturn(Variables.stringValue("   "));
        doNothing().when(activity).leave(any());

        activity.execute(execution);

        verify(execution, never()).setVariable(anyString(), any());
        verify(activity).leave(execution);
    }

    @Test
    void execute_updateOne_writesModifiedCount() throws Exception {
        @SuppressWarnings("unchecked")
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(mongoTemplate.getCollection("items")).thenReturn(collection);
        when(collection.updateOne(any(Document.class), any(Document.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("collection")).thenReturn(Variables.stringValue("items"));
        when(execution.getVariableTyped("operation")).thenReturn(Variables.stringValue("updateOne"));
        when(execution.getVariableTyped("filter")).thenReturn(Variables.stringValue("{\"_id\":\"1\"}"));
        when(execution.getVariableTyped("document")).thenReturn(Variables.stringValue("{\"$set\":{\"name\":\"x\"}}"));
        when(execution.getVariableTyped("result")).thenReturn(Variables.stringValue("modified"));
        doNothing().when(activity).leave(any());

        activity.execute(execution);

        verify(execution).setVariable(eq("modified"), eq(1L));
    }

    @Test
    void execute_deleteOne_writesDeletedCount() throws Exception {
        @SuppressWarnings("unchecked")
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(mongoTemplate.getCollection("items")).thenReturn(collection);
        when(collection.deleteOne(any(Document.class))).thenReturn(DeleteResult.acknowledged(1));

        ActivityExecution execution = mock(ActivityExecution.class);
        when(execution.getVariableTyped("collection")).thenReturn(Variables.stringValue("items"));
        when(execution.getVariableTyped("operation")).thenReturn(Variables.stringValue("deleteOne"));
        when(execution.getVariableTyped("filter")).thenReturn(Variables.stringValue("{\"_id\":\"1\"}"));
        when(execution.getVariableTyped("result")).thenReturn(Variables.stringValue("deleted"));
        doNothing().when(activity).leave(any());

        activity.execute(execution);

        verify(execution).setVariable(eq("deleted"), eq(1L));
    }
}
