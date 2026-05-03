package com.kiwi.cryoems.bpm.support;

import com.kiwi.cryoems.bpm.model.MrcMetadata;

/**
 * 解析 IMOD {@code header} 重定向到文本文件后的输出（与 cyroems {@code MrcFileMetaSupport#parseOutput} 对齐）。
 */
public final class MrcHeaderParser
{

    private MrcHeaderParser() {}

    public static MrcMetadata parse(String output, String sourceFile) {
        MrcMetadata meta = new MrcMetadata();
        meta.setFile(sourceFile);
        output
                .lines()
                .map(String::trim)
                .forEach(
                        l -> {
                            if (l.startsWith("Number of columns, rows, sections .....")) {
                                String[] split = splitLine(l);
                                meta.setColumns(Integer.parseInt(split[0].trim()));
                                meta.setRows(Integer.parseInt(split[1].trim()));
                                meta.setSections(Integer.parseInt(split[2].trim()));
                                return;
                            }
                            if (l.startsWith("Map mode .............................")) {
                                String[] split = splitLine(l);
                                meta.setMode(Integer.parseInt(split[0].trim()));
                                if (split.length > 1) {
                                    meta.setModeName(split[1].trim());
                                }
                                return;
                            }
                            if (l.startsWith("Minimum density .......................   ")) {
                                String[] split = splitLine(l);
                                meta.setMinimumDensity(Double.parseDouble(split[0].trim()));
                                return;
                            }
                            if (l.startsWith("Maximum density .......................   ")) {
                                String[] split = splitLine(l);
                                meta.setMaximumDensity(Double.parseDouble(split[0].trim()));
                                return;
                            }
                            if (l.startsWith("Mean density ..........................   ")) {
                                String[] split = splitLine(l);
                                meta.setMeanDensity(Double.parseDouble(split[0].trim()));
                            }
                        });
        if (meta.getSections() == 0) {
            throw new IllegalStateException("Movie Header is not valid, " + output);
        }
        return meta;
    }

    private static String[] splitLine(String l) {
        String rest = l.substring(39).trim();
        return rest.split(" +");
    }
}
