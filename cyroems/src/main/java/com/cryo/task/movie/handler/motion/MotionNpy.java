package com.cryo.task.movie.handler.motion;

import lombok.Data;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.openlca.npy.Npy;
import org.openlca.npy.arrays.NpyFloatArray;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class MotionNpy
{
    public static final String PATCH_NUMBER = "# Number of patches:";

    @Data
    public static class MotionLogPatch
    {
        private final int x;
        private int y;
        private int z = 2;
        private final float[] data;

        public MotionLogPatch(int patchCount, float[] data) {
            this.x = patchCount;
            this.data = data;
            this.y = data.length / (2 * patchCount);
        }

        public int[] getShape() {
            return new int[]{x, y, z};
        }
    }

    public void writePatchNpy(String motionLogFile, String outputFile) {
        MotionLogPatch parse = parsePatchLog(motionLogFile);
        NpyFloatArray npyDoubleArray = new NpyFloatArray(parse.getShape(), parse.data, false);

        Npy.write(new File(outputFile), npyDoubleArray);
    }

    public void writeFullNpy(String motionLogFile, String outputFile) {
        MotionLogPatch parse = parseFullLog(motionLogFile);
        NpyFloatArray npyDoubleArray = new NpyFloatArray(parse.getShape(), parse.data, false);
        Npy.write(new File(outputFile), npyDoubleArray);
    }


    public MotionLogPatch parsePatchLog(String motionLogFile) {
        try {

            List<String> lines = FileUtils.readLines(new File(motionLogFile), StandardCharsets.UTF_8);
            String numberLine = lines.get(1);
            int patchCount = Integer.parseInt(StringUtils.trim(numberLine.substring(PATCH_NUMBER.length())));
            FloatBuffer buffer = FloatBuffer.allocate(lines.size() * 2);
            for( String line : lines ) {
                if( StringUtils.isNotBlank(line) && !line.startsWith("#") ) {
                    String[] split = StringUtils.trim(line).split(" +");
                    buffer.put(Float.parseFloat(split[3]));
                    buffer.put(Float.parseFloat(split[4]));
                }
            }
            buffer.flip();
            float[] data = new float[buffer.remaining()];
            buffer.get(data);
            return new MotionLogPatch(patchCount, data);
        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
    }


    public MotionLogPatch parseFullLog(String motionLogFile) {
        try {

            List<String> lines = FileUtils.readLines(new File(motionLogFile), StandardCharsets.UTF_8);
            FloatBuffer buffer = FloatBuffer.allocate(lines.size() * 2);
            for( String line : lines ) {
                if( StringUtils.isNotBlank(line) && !line.startsWith("#") ) {
                    String[] split = StringUtils.trim(line).split(" +");
                    buffer.put(Float.parseFloat(split[1]));
                    buffer.put(Float.parseFloat(split[2]));
                }
            }
            buffer.flip();
            float[] data = new float[buffer.remaining()];
            buffer.get(data);
            return new MotionLogPatch(1, data);
        } catch( IOException e ) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        String file = "E:\\cryoem\\data\\output\\20250311_91_0001.log0-Patch-Patch.log";
        String out = "E:\\cryoem\\data\\output\\20250311_91_0001_local_patch_traj1.npy";
//        NpyDoubleArray  array = NpyDoubleArray.of(new int[]{2, 3}, new double[]{1, 2, 3, 4, 5, 6});;

//        System.out.println(array);
//        Npy.write(new File("E:\\Projects\\cryo-em-server-backend-main-2\\20250519_yueyang_7519_rigid_motion_traj1.npy"), array);
        MotionNpy npy = new MotionNpy();
        npy.writePatchNpy(file, out);
    }

}
