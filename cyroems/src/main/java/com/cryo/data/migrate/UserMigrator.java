package com.cryo.data.migrate;

import com.cryo.model.user.User;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class UserMigrator extends Migrator<User>
{

    @Override
    void migrate(User r) {
        if( StringUtils.isBlank(r.getSys_username()) ){
            r.setSys_username(r.getUser_group());
        }
        r.setMigrated(true);
        this.getMongoTemplate().rawSave(r);
    }

    @Override
    public Class getDbClass() {
        return User.class;
    }


}
