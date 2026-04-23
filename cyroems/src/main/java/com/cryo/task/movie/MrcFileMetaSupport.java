package com.cryo.task.movie;

import com.cryo.model.MrcMetadata;
import com.cryo.service.cmd.CmdException;
import com.cryo.service.cmd.SoftwareService;
import com.cryo.task.support.ExportSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.awaitility.Awaitility.await;

@Service
@Slf4j
@RequiredArgsConstructor
public class MrcFileMetaSupport
{

    private final SoftwareService softwareService;
    private final ExportSupport exportSupport;

    public MrcMetadata getMetaData(String file, File headerOutput) {
        SoftwareService.CmdProcess process = softwareService.header(file, headerOutput.getAbsolutePath());
        process.startAndWait();
        if(!headerOutput.exists()){
            try {
                await().pollInterval(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(10)).until(() -> headerOutput.exists());
            } catch( ConditionTimeoutException e ) {
                throw new CmdException("Header file not exist :" + headerOutput);
            }
        }
        MrcMetadata movieMeta;
        try {
            exportSupport.toSelf(headerOutput);
            movieMeta = parseOutput(FileUtils.readFileToString(headerOutput, StandardCharsets.UTF_8));
        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
        return movieMeta;
    }

    private MrcMetadata parseOutput(String output) {
        MrcMetadata movieMeta = new MrcMetadata();
        output.lines().forEach(l -> {
            l = StringUtils.trim(l);
            if( l.startsWith("Number of columns, rows, sections .....") ) {
                String[] split = splitLine(l);
                int columns = Integer.parseInt(StringUtils.trim(split[0]));
                int rows = Integer.parseInt(StringUtils.trim(split[1]));
                int sections = Integer.parseInt(StringUtils.trim(split[2]));
                movieMeta.setColumns(columns);
                movieMeta.setRows(rows);
                movieMeta.setSections(sections);
                return;
            }
            if( l.startsWith("Map mode .............................") ) {
                String[] split = splitLine(l);
                int mode = Integer.parseInt(StringUtils.trim(split[0]));
                String mode_name = StringUtils.trim(split[1]);
                movieMeta.setMode(mode);
                return;
            }
            if( l.startsWith("Minimum density .......................   ") ) {
                String[] split = splitLine(l);
                double minimum_density = Double.parseDouble(StringUtils.trim(split[0]));
                movieMeta.setMinimum_density(minimum_density);
                return;
            }
            if( l.startsWith("Maximum density .......................   ") ) {
                String[] split = splitLine(l);
                double maximum_density = Double.parseDouble(StringUtils.trim(split[0]));
                movieMeta.setMaximum_density(maximum_density);
                return;
            }
            if( l.startsWith("Mean density ..........................   ") ) {
                String[] split = splitLine(l);
                double mean_density = Double.parseDouble(StringUtils.trim(split[0]));
                movieMeta.setMean_density(mean_density);
                return;
            }
        });
        if( movieMeta.getSections() == 0 ) {
            throw new RuntimeException("Movie Header is not valid, " + output);
        }
        return movieMeta;
    }

    private String[] splitLine(String l) {
        return StringUtils.trim(StringUtils.trim(l).substring(39)).split(" +");
    }


    public static void main(String[] args) {
        MrcFileMetaSupport movieHeaderHandler = new MrcFileMetaSupport(null, null);
        MrcMetadata mrcMetadata = movieHeaderHandler.parseOutput("\n" +
                        " RO image file on unit   1 : /home/Titan2_k3/2025Q1/202503/20250312_szj/20250312_szj_0132.tif     Size=     452819 K\n" +
                        "\n" +
                        "                    This is a TIFF file (in strips of  11520 x      2).\n" +
                        "\n" +
                        " Number of columns, rows, sections .....   11520    8184      40\n" +
                        " Map mode ..............................    0   (byte)                     \n" +
                        " Start cols, rows, sects, grid x,y,z ...    0     0     0   11520   8184     40\n" +
                        " Pixel spacing (Angstroms)..............  0.4287     0.4287     0.4287    \n" +
                        " Cell angles ...........................   90.000   90.000   90.000\n" +
                        " Fast, medium, slow axes ...............    X    Y    Z\n" +
                        " Origin on x,y,z .......................    0.000       0.000       0.000    \n" +
                        " Minimum density .......................   0.0000    \n" +
                        " Maximum density .......................   4.0000    \n" +
                        " Mean density ..........................   2.0000    \n" +
                        " tilt angles (original,current) ........   0.0   0.0   0.0   0.0   0.0   0.0\n" +
                        " Space group,# extra bytes,idtype,lens .        0        0        0        0\n" +
                        "\n" +
                        "     2 Titles :\n" +
                        "SerialEMCCD: Frames . ., scaled by 1.00  r/f 0                                 \n" +
                        "  SuperRef_20250312_szj_0001.dm4 "
                );
        System.out.println(mrcMetadata);
    }
}
