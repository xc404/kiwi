package com.cryo.data.migrate;

import com.cryo.model.user.Group;
import org.springframework.stereotype.Service;

@Service
public class GroupMigrator extends Migrator<Group>
{

    @Override
    void migrate(Group r) {
        this.getMongoTemplate().rawSave(r);
    }

    @Override
    public Class getDbClass() {
        return Group.class;
    }


}
