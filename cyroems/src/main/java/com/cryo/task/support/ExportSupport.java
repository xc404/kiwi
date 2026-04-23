package com.cryo.task.support;

import com.cryo.model.Task;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.service.fileservice.FileService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportSupport
{

    public static final String CRYOSPARC_USER = "cryosparc-user";
    private final SoftwareService softwareService;
    private final FileService fileService;
    @Value("${app.file.copy_with_shell}")
    private boolean copy_with_shell = false;


    @Getter
    private String writeBackUser = "root";
    @Getter
    private String writeBackGroup = "root";


    @Getter
    @Value("${app.file.user}")
    private String user;


    public String copyToUser(Task task, List<String> source, File destDir) {

        return this.fileService.rsync_and_acl(source, destDir.getAbsolutePath(),
                task.getGroup_name(), task.getBelong_user(), List.of(CRYOSPARC_USER)).toString();
    }

    public String copyToUser(Task task, File source, File destDir) {
        String user = Optional.ofNullable(task.getBelong_user()).orElse(task.getGroup_name());
        String groupName = task.getGroup_name();

        if( fileService.enabled() ) {
            return this.fileService.rsync_and_acl(List.of(source.getAbsolutePath()), destDir.getAbsolutePath(),
                    task.getGroup_name(), task.getBelong_user(), List.of(CRYOSPARC_USER)).toString();
        }

        if( copy_with_shell ) {
            copy_with_shell(source, destDir, user, groupName, true);
        } else {
            copy_without_shell(source, destDir, user, groupName);
        }
//        File desc = new File(destDir, source.getName());
//        this.softwareService.setfacl(desc,"u:"+CRYOSPARC_USER+":rwx");
        return "copy with sell";
    }

    public SoftwareService.CmdProcess copyToUserShell(Task task, String source, File destDir) {
        String user = Optional.ofNullable(task.getBelong_user()).orElse(task.getGroup_name());
        String groupName = task.getGroup_name();
        return copy_with_shell(new File(source), destDir, user, groupName, false);
    }

    public String copyToUser(Task task, String source, File destDir) {
        return copyToUser(task, new File(source), destDir);
    }

//    public void writeBack(File source, File destDir) {
//        if( copy_with_shell ) {
//            copy_with_shell(source, destDir, writeBackUser, writeBackGroup, true);
//        } else {
//            copy_without_shell(source, destDir, writeBackUser, writeBackGroup);
//        }
//    }
//
//    public SoftwareService.CmdProcess writeBackShell(File source, File destDir) {
//        return copy_with_shell(source, destDir, writeBackUser, writeBackGroup, false);
//    }

//    public SoftwareService.CmdProcess writeBackShell(String source, String destDir) {
//        return copy_with_shell(new File(source), new File(destDir), writeBackUser, writeBackGroup, false);
//    }

    public void toSelf(File file) {
        this.setOwnerAndPermission(file, user, user);
    }

//    public void writeBack(String source, String destDir) {
//        writeBack(new File(source), new File(destDir));
//    }


    private void copy_without_shell(File source, File destDir, String user, String groupName) {
        try {

            File desc = new File(destDir, source.getName());
            FileUtils.copyFile(source, desc);
            setOwnerAndPermission(desc, user, groupName);

        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
    }

    private SoftwareService.CmdProcess copy_with_shell(File source, File destDir, String user, String groupName, boolean execute) {
        SoftwareService.CmdProcess cmdProcess = this.softwareService.copy_and_change_own(source.getAbsolutePath(), destDir.getAbsolutePath(), user, groupName);
        if( execute ) {
            cmdProcess.startAndWait();
        }
        return cmdProcess;
    }


    public void setOwnerAndPermission(Task task, File file) {
        String user = Optional.ofNullable(task.getBelong_user()).orElse(task.getGroup_name());
        String groupName = task.getGroup_name();
        setOwnerAndPermission(file, user, groupName);
    }

    public void setOwnerAndPermission(File file, String user, String groupName) {

        try {
            UserPrincipal owner = Files.getOwner(file.toPath());
            if( owner.getName().equals(user) ) {
                log.info("File {} owner is already {}", file, user);
                return;
            }
        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
        this.softwareService.chown(file, user, groupName).startAndWait();
        log.info("Change file {} owner to {}", file, user);
        setPermission(file);

    }

    public void setPermission(File file) {
        String permission = file.isDirectory() ? "755" : "644";
        this.softwareService.chmod(file, permission).startAndWait();
    }

    public void setPermission(File file, String permission) {
        this.softwareService.chmod(file, permission).startAndWait();
    }

    public void changeOwn(Task task, File file) {
        Path path = file.toPath();

        PosixFileAttributeView foav = Files.getFileAttributeView(path,
                PosixFileAttributeView.class);
        try {

            FileSystem fs = FileSystems.getDefault();
            UserPrincipalLookupService upls = fs.getUserPrincipalLookupService();
            UserPrincipal newOwner = null;
            GroupPrincipal group = null;

            try {
                if( StringUtils.isNotBlank(task.getBelong_user()) ) {
                    try {
                        newOwner = upls.lookupPrincipalByName(task.getBelong_user());
                    } catch( UserPrincipalNotFoundException e ) {
                        log.error(e.getMessage());
                    }
                } else {
                    try {
                        newOwner = upls.lookupPrincipalByName(task.getGroup_name());
                    } catch( UserPrincipalNotFoundException e ) {
                        log.error(e.getMessage());
                    }
                }
                group = upls.lookupPrincipalByGroupName(task.getGroup_name());
            } catch( Exception e ) {
                log.error(e.getMessage(), e);
            }
            if( group != null ) {
                foav.setGroup(group);
            }
            if( newOwner != null ) {
                foav.setOwner(newOwner);
            }


        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
    }

    public boolean enabledFileService() {

        return fileService.enabled();
    }
}
