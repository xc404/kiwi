#!/bin/bash
source ~/.bashrc
source activate cryoems

set -m

mc2.sh -InTiff /home/Titan1_k3/2025Q2/2021506/20250605_cy/20250605_cy_2150.tif -Mag 1.014 0.986 13.5 -FmDose 7.5 -Gain /home/cryoems/data/dev/dev-chao-test_684268c3d5842525ab3007bf/SuperRef_20250605_cy_0001.mrc -OutMrc /home/cryoems/data/dev/dev-chao-test_684268c3d5842525ab3007bf/motion/20250605_cy_2150.mrc -FtBin 2 -Patch 5 5 -PixSize 0.41 -kV 300.0 -LogFile /home/cryoems/data/dev/dev-chao-test_684268c3d5842525ab3007bf/motion/20250605_cy_2150_log

ctffind5.sh -i /home/cryoems/data/dev/dev-chao-test_684268c3d5842525ab3007bf/motion/20250605_cy_2104.mrc -o /home/cryoems/data/dev/dev-chao-test_684268c3d5842525ab3007bf/ctf/20250605_cy_2104_freq.mrc -p 0.82 -v 300.0 -c 2.7 -a 0.07 -s 512.0 -rmin 30.0 -rmax 5.0 -dmin 5000.0 -dmax 50000.0 -step 100.0

mrc_png.sh -i /home/cryoems/data/dev/dev-chao-test_684268c3d5842525ab3007bf/motion/20250605_cy_1698_DW.mrc -o /home/cryoems/data/dev/dev-chao-test_684268c3d5842525ab3007bf/thumbnails/20250605_cy_1698_DW_thumb_@1024.png

ctf_png.sh -i /home/cryoems/data/dev/dev-chao-test_684268c3d5842525ab3007bf/ctf/20250605_cy_1696_freq.mrc -o /home/cryoems/data/dev/dev-chao-test_684268c3d5842525ab3007bf/thumbnails/20250605_cy_1696_freq.png








