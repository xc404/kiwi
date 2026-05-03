package com.cryo.task.gain;

import cn.hutool.core.io.FileUtil;
import com.cryo.model.GainConvertSoftware;
import com.cryo.model.GainConvertStatus;
import com.cryo.service.FilePathService;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.task.support.ExportSupport;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
@RequiredArgsConstructor
@Slf4j
public class GainConvertor
{


    private final SoftwareService softwareService;
    private final FilePathService filePathService;
    private final ExportSupport exportSupport;

    public File convert(File input_file) {
//        File input_file = new File(gain.getFile_path());
        String prefix = FileUtil.getPrefix(input_file.getName());
        File output_file = new File(input_file.getParent(), prefix + "_usable.mrc");
        String suffix = FileUtil.getSuffix(input_file);
//        GainConvertSoftware software = null;
        SoftwareService.CmdProcess process = null;
        if( suffix.equals("gain") || suffix.equals("tif") ) {
            File temp_file = new File(input_file.getParent(), FileUtil.getSuffix(input_file.getName()) + "_gain_temp.mrc");
            SoftwareService.CmdProcess tif2mrc = this.softwareService.tif2mrc(input_file.getAbsolutePath(), temp_file.getAbsolutePath());
            tif2mrc.startAndWait();
            exportSupport.setPermission(temp_file);
            process = this.softwareService.e2proc2d(temp_file.getAbsolutePath(), output_file.getAbsolutePath());
        }
        if( suffix.equals("dm4") ) {
            process = this.softwareService.dm2mrc(input_file.getAbsolutePath(), output_file.getAbsolutePath());
        }
        if( process == null ) {
            throw new RuntimeException(String.format("gain file %s not supported", input_file.getAbsolutePath()));
        }
        log.info("start gain convert: {}", process);
        process.startAndWait();
        log.info("end gain convert");
//        return new GainConvertResult(GainConvertStatus.completed, output_file.getAbsolutePath(), software);
        exportSupport.setPermission(output_file);
        return output_file;
    }

    @Data
    @AllArgsConstructor
    public static class GainConvertResult
    {
        private final GainConvertStatus status;
        private final String outputFile;
        private final GainConvertSoftware software;
    }


}

