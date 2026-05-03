package com.cryo.ctl.test;

import com.cryo.service.cmd.SoftwareService;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.nio.file.attribute.BasicFileAttributes;

@Controller
public class ProcessTestCtl
{
    private final TaskExecutor taskExecutor;

    public ProcessTestCtl(TaskExecutor taskExecutor, SoftwareService softwareService) {
        this.taskExecutor = taskExecutor;
        this.softwareService = softwareService;
    }

    public static class ProcessInput
    {
        public String cmd;
        public String args;
    }

    private final SoftwareService softwareService;
//
//    @ResponseBody
//    @PostMapping("/test/process")
//    public R exe(@RequestBody ProcessInput processInput) {
//        SoftwareService.SoftwareConfig softwareCmd = this.softwareService.getSoftwareCmd(SoftwareExe.valueOf(processInput.cmd));
//        SoftwareService.CmdProcess cmdProcess = softwareService.cmdProcess(softwareCmd);
//        cmdProcess.command(processInput.args.split(" +"));
//        cmdProcess.startAndWait();
//        return R.success();
//    }


//    @ResponseBody
//    @PostMapping("/test/process/native")
//    public R<String> nativeCmd(@RequestBody ProcessInput processInput) {
//        SoftwareService.SoftwareConfig softwareConfig = new SoftwareService.SoftwareConfig();
//        softwareConfig.setPath(processInput.cmd);
//        SoftwareService.CmdProcess cmdProcess = softwareService.cmdProcess(softwareConfig);
//        cmdProcess.command(processInput.args.split(" +"));
//        cmdProcess.startAndWait();
//
//        return R.success(cmdProcess.result());
//
//
//    }
//
//    @ResponseBody
//    @PostMapping("/test/process/setfacl")
//    public R<String> setfacl(@RequestBody ProcessInput processInput) {
//        SoftwareService.SoftwareConfig softwareCmd = softwareService.getSoftwareCmd(SoftwareExe.setfacl);

    /// /        SoftwareService.CmdProcess cmdProcess = softwareService.cmdProcess(softwareCmd);
    /// /        cmdProcess.command(processInput.args.split(" +"));
    /// /        cmdProcess.startAndWait();
//        SoftwareService.CmdProcess cmdProcess = softwareService.setfacl(new File(processInput.args), processInput.cmd);
//        cmdProcess.startAndWait();
//        return R.success(cmdProcess.result());
//
//
//    }


//    @ResponseBody
//    @PostMapping("/test/fileExist")
//    public R exist() {
//        try {
//            FileUtils.copyFile(new File("/home/ppd/data/20250402_cy_2844.tif"),new File("/home/cellverse/output/20250402_cy_2844.tif"));
//        } catch( IOException e ) {
//            throw new RuntimeException(e);
//        }
//        File file = new File("/home/ai_platform");
//        return R.success(file.exists());
//    }
//
//
//    @ResponseBody
//    @GetMapping("/test/fileExist")
//    public R fileSort(String path) {
//        Collection<File> files = FileUtils.listFiles(new File(path), null, false);
//        List<FileSort> ctime = files.stream().map(FileSort::new)
//                .sorted(Comparator.comparing(o -> o.ctime))
//                .toList();
//        List<FileSort> atime = files.stream().map(FileSort::new)
//                .sorted(Comparator.comparing(o -> o.atime))
//                .toList();
//        List<FileSort> mtime = files.stream().map(FileSort::new)
//                .sorted(Comparator.comparing(o -> o.mtime))
//                .toList();
//        List<FileSort> lastModified = files.stream().map(FileSort::new)
//                .sorted(Comparator.comparing(o -> o.lastModified))
//                .toList();
//
//        return R.success(Map.of(
//                "ctime", ctime,
//                "atime", atime,
//                "mtime", mtime,
//                "lastModified", lastModified
//        ));
//    }

    public static class FileSort
    {
        public String name;
        public long size;
        public String ctime;
        public String atime;
        public String mtime;
        public String lastModified;

        public FileSort(File file) {
            this.name = file.getName();
            this.size = file.length();
            BasicFileAttributes attr = com.cryo.common.utils.FileUtils.attr(file);
            ;
            this.ctime = attr.creationTime().toString();
            this.atime = attr.lastAccessTime().toString();
            this.mtime = attr.lastModifiedTime().toString();
            this.lastModified = com.cryo.common.utils.FileUtils.lastModified(file).toString();
        }
    }

}
