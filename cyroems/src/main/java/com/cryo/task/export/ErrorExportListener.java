package com.cryo.task.export;

import com.cryo.dao.UserRepository;
import com.cryo.model.Task;
import com.cryo.model.export.ExportTask;
import com.cryo.model.user.User;
import com.cryo.service.mail.MailService;
import com.cryo.service.mail.MailTemplate;
import com.cryo.task.event.ExportTaskStatisticEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.core.utils.DateUtil;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorExportListener implements ApplicationListener<ExportTaskStatisticEvent>
{
    private final Set<String> reportedTasks = new HashSet<>();
    private final MailService mailService;
    private final UserRepository userRepository;

    @Override
    public void onApplicationEvent(ExportTaskStatisticEvent event) {
        Task task = event.getExportTaskVo().getTask();
        ExportTask exportTask = event.getExportTaskVo().getExportTask();

        if( reportedTasks.contains(exportTask.getId()) ) {
            log.info("reported task");
            return;
        }
        if( exportTask.getMovie_statistic().getError() <= 0 && Optional.ofNullable(exportTask.getMdoc_statistic()).map(Task.Statistic::getError).orElse(0l) <= 0 ) {
            return;
        }
        sendErrorMail(task, exportTask);
        reportedTasks.add(exportTask.getId());
    }

    public void sendErrorMail(Task task, ExportTask exportTask) {
        log.info("send error mail");
        Map<String, Object> context = Map.of(
                "task_name", exportTask.getName(),
                "task_id", exportTask.getId(),
                "task_status", exportTask.getStatus().name(),
                "task_output_dir", exportTask.getOutputDir(),

                "task_create_time", Objects.requireNonNull(DateUtil.format(exportTask.getCreated_at(), DateUtil.PATTERN_DATETIME))
        );
        String subject = MessageFormat.format("[Error] {0} Auto-Completed", exportTask.getName());
        User user = this.userRepository.findById(task.getOwner()).orElse(null);
        if( user == null ) {
            log.error("user not found");
            return;
        }
        this.mailService.sendMail(user.getEmail(), subject, MailTemplate.export_error, context);
    }
}
