package com.kiwi.cryoems.bpm.support;

import com.kiwi.cryoems.bpm.model.MrcMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MrcHeaderParserTest
{

    @Test
    void parsesSampleHeaderText() {
        String output =
                "\n"
                        + " RO image file on unit   1 : /tmp/movie.tif     Size=     452819 K\n"
                        + "\n"
                        + "                    This is a TIFF file (in strips of  11520 x      2).\n"
                        + "\n"
                        + " Number of columns, rows, sections .....   11520    8184      40\n"
                        + " Map mode ..............................    0   (byte)                     \n"
                        + " Start cols, rows, sects, grid x,y,z ...    0     0     0   11520   8184     40\n"
                        + " Pixel spacing (Angstroms)..............  0.4287     0.4287     0.4287    \n"
                        + " Minimum density .......................   0.0000    \n"
                        + " Maximum density .......................   4.0000    \n"
                        + " Mean density ..........................   2.0000    \n";

        MrcMetadata m = MrcHeaderParser.parse(output, "/tmp/movie.tif");
        assertEquals(11520, m.getColumns());
        assertEquals(8184, m.getRows());
        assertEquals(40, m.getSections());
        assertEquals(0, m.getMode());
        assertTrue(m.getModeName().contains("byte"));
        assertEquals(0.0, m.getMinimumDensity(), 1e-9);
        assertEquals(4.0, m.getMaximumDensity(), 1e-9);
        assertEquals(2.0, m.getMeanDensity(), 1e-9);
        assertEquals("/tmp/movie.tif", m.getFile());
    }
}
