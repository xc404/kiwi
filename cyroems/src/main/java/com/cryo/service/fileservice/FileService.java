package com.cryo.service.fileservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService implements InitializingBean
{


    @Value("${app.file.service.api}")
    private String apiRoot;
    @Value("${app.file.service.apiKey:BioEMAPIFORIO}")
    private String apiKey = "BioEMAPIFORIO";

    @Value("${app.file.service.enabled:true}")
    private boolean enabled = true;
    private RestClient restClient;

    public JsonNode rsync_and_acl(List<String> source, String destination, String group, String user, List<String> aclUsers) {

        ResponseEntity<JsonNode> rsyncAndAcl = restClient.post().uri("rsync_and_acl")
                .body(new FileServiceRequest(source, destination, group, user, aclUsers))
                .retrieve()
                .toEntity(JsonNode.class);
        JsonNode body = rsyncAndAcl.getBody();
        log.info("Rsync and ACL result: {}", body);
        if( !rsyncAndAcl.getStatusCode().is2xxSuccessful() || body == null ) {
            throw new RuntimeException(String.valueOf(body));
        }
        return rsyncAndAcl.getBody();
    }

    public JsonNode acl_only(List<String> source, List<String> aclUsers) {

        ResponseEntity<JsonNode> rsyncAndAcl = restClient.post().uri("acl_only")
                .body(new AclRequest(source, aclUsers))
                .retrieve()
                .toEntity(JsonNode.class);
        JsonNode body = rsyncAndAcl.getBody();
        log.info("Rsync and ACL result: {}", body);
        if( !rsyncAndAcl.getStatusCode().is2xxSuccessful() || body == null ) {
            throw new RuntimeException(String.valueOf(body));
        }
        return rsyncAndAcl.getBody();
    }

    public JsonNode chown_only(List<String> source, String group, String user) {

        ResponseEntity<JsonNode> rsyncAndAcl = restClient.post().uri("chown_only")
                .body(new ChownRequest(source, group, user))
                .retrieve()
                .toEntity(JsonNode.class);
        JsonNode body = rsyncAndAcl.getBody();
        log.info("Rsync and ACL result: {}", body);
        if( !rsyncAndAcl.getStatusCode().is2xxSuccessful() || body == null ) {
            throw new RuntimeException(String.valueOf(body));
        }
        return body;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.restClient = RestClient.builder().baseUrl(apiRoot)
                .defaultHeader("X-API-Key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public boolean enabled() {
        return true;
    }

    @Data
    private class FileServiceRequest
    {
        @JsonProperty("src_paths")
        private List<String> source;
        @JsonProperty("dst_dir")
        private String destination;
        @JsonProperty("group")
        private String group;
        @JsonProperty("owner")
        private String user;
        @JsonProperty("acl_users")
        private List<String> aclUsers;
        private boolean recursive_acl_for_dirs = true;

        public FileServiceRequest(List<String> source, String destination, String group, String user, List<String> aclUsers) {
            this.source = source;
            this.destination = destination;
            this.group = group;
            this.user = user;
            this.aclUsers = aclUsers;
        }
    }

    private static class AclRequest
    {
        @JsonProperty("paths")
        private List<String> source;
        @JsonProperty("acl_users")
        private List<String> aclUsers;
        private boolean recursive = true;
        private boolean set_default_for_dirs = true;

        public AclRequest(List<String> source, List<String> aclUsers) {
            this.source = source;
            this.aclUsers = aclUsers;
        }
    }

    private static class ChownRequest
    {
        @JsonProperty("paths")
        private List<String> source;
        @JsonProperty("group")
        private String group;
        @JsonProperty("owner")
        private String user;
        private boolean recursive = true;

        public ChownRequest(List<String> source, String group, String user) {
            this.source = source;
            this.group = group;
            this.user = user;
        }
    }
}
