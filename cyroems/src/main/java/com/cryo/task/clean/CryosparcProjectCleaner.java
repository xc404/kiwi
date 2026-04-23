package com.cryo.task.clean;

import com.cryo.dao.CryosparcProjectRepository;
import com.cryo.service.cryosparc.CryosparcClient;
import com.cryo.task.export.cryosparc.CryosparcProject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.utils.DateUtil;
import org.apache.commons.io.FileUtils;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CryosparcProjectCleaner
{
    private final CryosparcProjectRepository projectRepository;
    private final CryosparcClient cryosparcClient;

    @Scheduled(cron = "0 0 0/10 * * ?")
    public void clean() {

        List<CryosparcProject> cryosparcProjects = this.projectRepository.findByQuery(Query.query(Criteria.where("cleaned").ne(true)));

        cryosparcProjects = cryosparcProjects.stream().filter(project -> {

            return project.getCreated_at().before(DateUtil.minusDays(new Date(), 8));
        }).toList();
        cryosparcProjects.forEach(project -> {
            this.cryosparcClient.detach(project.getProject_uid());
            try {
                FileUtils.forceDelete(new File(project.getPath()));
            } catch( IOException e ) {
                log.error(e.getMessage(), e);
            }
            this.projectRepository.update(new Update().set("cleaned", true), Query.query(Criteria.where("_id").is(project.getId())));
        });
    }

}
