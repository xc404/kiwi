package com.cryo.model.user;

import com.cryo.common.model.IdEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Document("group")
@Data
public class Group extends IdEntity
{
    private String group_name;
    private String group_display_name;
    private long volume_in_bytes;
    private List<String> group_admin;
    private Map<String, Member> members;  // key: user_id
    private List<String> data_owned;
    private List<String> related_projects;

    public Map<String, Member> getMembers() {
        if (members == null) members = new HashMap<>();
        return members;
    }

    @Data
    public static class Member
    {
        private String user_id;
        private String user_name;
        private String role;
        private String join_date;
        private String exit_date;
    }
}
