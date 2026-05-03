package com.kiwi.bpmn.component.mongo;

import com.kiwi.bpmn.component.utils.ExecutionUtils;
import com.kiwi.bpmn.core.annotation.ComponentDescription;
import com.kiwi.bpmn.core.annotation.ComponentParameter;
import io.swagger.v3.oas.annotations.media.Schema;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.camunda.bpm.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

/**
 * 使用 {@link MongoTemplate} 对默认库中的集合执行常见读写，filter / document 为 JSON 字符串。
 */
@ConditionalOnBean(MongoTemplate.class)
@Component("mongoActivity")
@ComponentDescription(
        name = "MongoDB",
        group = "数据",
        version = "1.0",
        description = "对 MongoDB 集合执行 findOne / find / insert / updateOne / deleteOne / count（条件与文档均为 JSON）",
        inputs = {
                @ComponentParameter(key = "collection", name = "集合名", description = "必填"),
                @ComponentParameter(
                        key = "operation",
                        name = "操作",
                        description = "findOne | find | insert | updateOne | deleteOne | count，默认 findOne"),
                @ComponentParameter(
                        key = "filter",
                        name = "filter(JSON)",
                        description = "查询条件，默认 {}"),
                @ComponentParameter(
                        key = "document",
                        name = "document(JSON)",
                        description = "insert 时为插入文档；updateOne 时为更新文档（可含 $set 等）"),
                @ComponentParameter(
                        key = "limit",
                        name = "find 条数上限",
                        description = "仅 find 有效，默认 500，最大 10000"),
        },
        outputs = {
                @ComponentParameter(
                        key = "result",
                        name = "结果变量名",
                        description = "写入查询结果、删除/更新影响条数等",
                        schema = @Schema(defaultValue = "result")),
        })
public class MongoActivity extends AbstractBpmnActivityBehavior {

    private static final int DEFAULT_FIND_LIMIT = 500;
    private static final int MAX_FIND_LIMIT = 10000;

    private final MongoTemplate mongoTemplate;

    public MongoActivity(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void execute(ActivityExecution execution) throws Exception {
        String collection = ExecutionUtils.getStringInputVariable(execution, "collection")
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("流程变量 collection（集合名）不能为空"));

        String op = ExecutionUtils.getStringInputVariable(execution, "operation")
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse("findOne");
        op = op.toLowerCase(Locale.ROOT);

        String filterJson = ExecutionUtils.getStringInputVariable(execution, "filter").orElse("{}");
        Document filter = parseJsonAsDocument(filterJson, "filter");

        String resultVar = ExecutionUtils.getOutputVariableName(execution, "result");

        String filterStr = filter.toJson();
        Object out = switch (op) {
            case "findone" -> mongoTemplate.findOne(new BasicQuery(filterStr), Document.class, collection);
            case "find" -> {
                int lim = resolveFindLimit(execution);
                Query q = new BasicQuery(filterStr);
                q.limit(lim);
                yield mongoTemplate.find(q, Document.class, collection);
            }
            case "insert" -> {
                String docJson = ExecutionUtils.getStringInputVariable(execution, "document")
                        .filter(s -> !s.isBlank())
                        .orElseThrow(() -> new IllegalArgumentException("insert 需要流程变量 document（JSON 文档）"));
                Document doc = parseJsonAsDocument(docJson, "document");
                yield mongoTemplate.insert(doc, collection);
            }
            case "updateone" -> {
                String updJson = ExecutionUtils.getStringInputVariable(execution, "document")
                        .filter(s -> !s.isBlank())
                        .orElseThrow(() -> new IllegalArgumentException("updateOne 需要流程变量 document（更新 JSON，可含 $set）"));
                Document update = parseJsonAsDocument(updJson, "document");
                MongoCollection<Document> coll = mongoTemplate.getCollection(collection);
                UpdateResult ur = coll.updateOne(filter, update);
                yield ur.getModifiedCount();
            }
            case "deleteone" -> {
                DeleteResult dr = mongoTemplate.getCollection(collection).deleteOne(filter);
                yield dr.getDeletedCount();
            }
            case "count" -> mongoTemplate.count(new BasicQuery(filterStr), collection);
            default -> throw new IllegalArgumentException(
                    "不支持的操作: " + op + "，请使用 findOne | find | insert | updateOne | deleteOne | count");
        };

        if (resultVar != null && !resultVar.isBlank()) {
            execution.setVariable(resultVar, out);
        }

        super.leave(execution);
    }

    private int resolveFindLimit(ActivityExecution execution) {
        return ExecutionUtils.getIntInputVariable(execution, "limit")
                .map(l -> Math.min(Math.max(l, 1), MAX_FIND_LIMIT))
                .orElse(DEFAULT_FIND_LIMIT);
    }

    private static Document parseJsonAsDocument(String json, String fieldLabel) {
        Objects.requireNonNull(json, fieldLabel);
        String trimmed = json.trim();
        if (trimmed.isEmpty()) {
            return new Document();
        }
        try {
            return Document.parse(trimmed);
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldLabel + " 不是合法 JSON: " + e.getMessage(), e);
        }
    }
}
