package com.kiwi.project.bpm.designer.agent;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.alibaba.cloud.ai.graph.serializer.check_point.CheckPointSerializer;
import com.alibaba.cloud.ai.graph.serializer.std.ObjectStreamStateSerializer;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.LinkedList;
import java.util.Objects;

/**
 * Graph checkpoint Mongo 持久化（集合 {@code designer_agent_checkpoint}）。
 */
@Slf4j
public class DesignerAgentMongoCheckpointSaver extends MemorySaver {

    public static final String CollectionName = "designer_agent_checkpoint";
    private static final String FieldPayload = "payload";

    private final MongoTemplate mongoTemplate;
    private final CheckPointSerializer checkpointSerializer;

    public DesignerAgentMongoCheckpointSaver(MongoTemplate mongoTemplate) {
        this.mongoTemplate = Objects.requireNonNull(mongoTemplate);
        StateSerializer stateSerializer = new ObjectStreamStateSerializer(OverAllState::new);
        this.checkpointSerializer = new CheckPointSerializer(stateSerializer);
    }

    @Override
    protected LinkedList<Checkpoint> loadedCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints)
            throws Exception {
        String threadId = config.threadId().orElse(BaseCheckpointSaver.THREAD_ID_DEFAULT);
        Document doc = mongoTemplate.findById(threadId, Document.class, CollectionName);
        if (doc == null || doc.getString(FieldPayload) == null) {
            return checkpoints;
        }
        return deserializeCheckpoints(doc.getString(FieldPayload));
    }

    @Override
    protected void insertedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint)
            throws Exception {
        persist(config, checkpoints);
    }

    @Override
    protected void updatedCheckpoint(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Checkpoint checkpoint)
            throws Exception {
        persist(config, checkpoints);
    }

    @Override
    protected void releasedCheckpoints(RunnableConfig config, LinkedList<Checkpoint> checkpoints, Tag releaseTag)
            throws Exception {
        String threadId = config.threadId().orElse(BaseCheckpointSaver.THREAD_ID_DEFAULT);
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(threadId)), CollectionName);
    }

    private void persist(RunnableConfig config, LinkedList<Checkpoint> checkpoints) throws Exception {
        String threadId = config.threadId().orElse(BaseCheckpointSaver.THREAD_ID_DEFAULT);
        Document doc = new Document("_id", threadId);
        doc.put(FieldPayload, serializeCheckpoints(checkpoints));
        mongoTemplate.save(doc, CollectionName);
    }

    private String serializeCheckpoints(LinkedList<Checkpoint> checkpoints) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeInt(checkpoints.size());
            for (Checkpoint checkpoint : checkpoints) {
                checkpointSerializer.write(checkpoint, oos);
            }
            oos.flush();
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    private LinkedList<Checkpoint> deserializeCheckpoints(String content) throws IOException, ClassNotFoundException {
        if (content == null || content.isEmpty()) {
            return new LinkedList<>();
        }
        byte[] bytes = Base64.getDecoder().decode(content);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            int size = ois.readInt();
            LinkedList<Checkpoint> list = new LinkedList<>();
            for (int i = 0; i < size; i++) {
                list.add(checkpointSerializer.read(ois));
            }
            return list;
        }
    }
}
