#!/bin/bash


source ~/.bashrc
source activate cryoems

set -m


#tiltxcorr
echo "tiltxcorr started at $(date +'%Y-%m-%d %H:%M:%S')"
tiltxcorr -inp /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_raw_bin.mrc -ou /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01.prexf -ro 84.1 -ti /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_raw_bin.rawtilt -sigma1 0.03 -radius2 0.25 -sigma2 0.05
echo "tiltxcorr ended at $(date +'%Y-%m-%d %H:%M:%S')"

#xftoxg
echo "xftoxg started at $(date +'%Y-%m-%d %H:%M:%S')"
xftoxg -n 0 -in /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01.prexf -g /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01.prexg
echo "xftoxg ended at $(date +'%Y-%m-%d %H:%M:%S')"

#newstack
echo "newstack started at $(date +'%Y-%m-%d %H:%M:%S')"
newstack -in /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_raw_bin.mrc -ou /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_preali.mrc -bin 4 -mo 0 -fl 2 -x /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01.prexg -im 1
echo "newstack ended at $(date +'%Y-%m-%d %H:%M:%S')"

#patch_tracking_tiltxcorr
echo "patch_tracking_tiltxcorr started at $(date +'%Y-%m-%d %H:%M:%S')"
tiltxcorr -inp /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_preali.mrc -ou /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_pt.fid -ro 84.1 -ti /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_raw_bin.rawtilt -sigma1 0.03 -radius2 0.25 -sigma2 0.05 -bor 46,46 -it 4 -im 4 -size 150,150 -overlap 0.33,0.33
echo "patch_tracking_tiltxcorr ended at $(date +'%Y-%m-%d %H:%M:%S')"

#imodchopconts
echo "imodchopconts started at $(date +'%Y-%m-%d %H:%M:%S')"
imodchopconts -i /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_pt.fid -ou /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01.fid -overlap 4 -s 1
echo "imodchopconts ended at $(date +'%Y-%m-%d %H:%M:%S')"

#series_align
echo "series_align started at $(date +'%Y-%m-%d %H:%M:%S')"
python /home/cryoems/bin/py/mdoc/tilt_series_align.py --image_file /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_preali.mrc --model_file /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01.fid --tilt_path /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_raw_bin.rawtilt --tilt_axis_angle 84.1 --pixel_size 0.303 --max_avg 5.0
echo "series_align ended at $(date +'%Y-%m-%d %H:%M:%S')"

#xfproduct
echo "xfproduct started at $(date +'%Y-%m-%d %H:%M:%S')"
xfproduct -in1 /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01.prexg -in2 /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01.tltxf -ou /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_fid.xf --s 1,4
echo "xfproduct ended at $(date +'%Y-%m-%d %H:%M:%S')"

#patch2imod
echo "patch2imod started at $(date +'%Y-%m-%d %H:%M:%S')"
patch2imod -s 10 /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01.resid -ou /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01.resmod
echo "patch2imod ended at $(date +'%Y-%m-%d %H:%M:%S')"

#align_recon_newstack1
echo "align_recon_newstack1 started at $(date +'%Y-%m-%d %H:%M:%S')"
newstack -in /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_raw_bin.mrc -ou /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_ali.mrc -bin 4 -x /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_fid.xf
echo "align_recon_newstack1 ended at $(date +'%Y-%m-%d %H:%M:%S')"

#align_recon_newstack2
echo "align_recon_newstack2 started at $(date +'%Y-%m-%d %H:%M:%S')"
newstack -in /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_raw_bin.mrc -ou /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_ali_bin1.mrc -bin 1 -x /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_fid.xf
echo "align_recon_newstack2 ended at $(date +'%Y-%m-%d %H:%M:%S')"

#align_tilt
echo "align_tilt started at $(date +'%Y-%m-%d %H:%M:%S')"
tilt -inp /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_ali.mrc -ou /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_full_rec_bin4.mrc -TILTFILE /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_raw_bin.rawtilt -XTILTFILE /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01.tltxf -THICKNESS 1600 -IMAGEBINNED 4 -RADIAL 0.35,0.035 -FalloffIsTrueSigma 1 -XAXISTILT 0 -SCALE 0,330 -PERPENDICULAR -MODE 2 -AdjustOrigin -ActionIfGPUFails 1,2 -FakeSIRTiterations 30 -SUBSETSTART 0,0
echo "align_tilt ended at $(date +'%Y-%m-%d %H:%M:%S')"

#align_binvol
echo "align_binvol started at $(date +'%Y-%m-%d %H:%M:%S')"
binvol -b 2 -o /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_full_rec_bin4.mrc /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_full_rec_bin4.mrc
echo "align_binvol ended at $(date +'%Y-%m-%d %H:%M:%S')"

#align_recon_align_recon
echo "align_recon_align_recon started at $(date +'%Y-%m-%d %H:%M:%S')"
python /home/cryoems/bin/py/mdoc/align_recon_v2.py -in /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/mdoc/tomo_0518-ce_P01/tomo_0518-ce_P01_full_rec_bin4.mrc -ou /home/cryoems/data/prod/tomo_20260518_ce_6a0aec14a968f3579402dc0b/tomo_20260518_ce-20260521145718/thumbnails/tomo_0518-ce_P01_full_rec_bin8_unit8.mrc
echo "align_recon_align_recon ended at $(date +'%Y-%m-%d %H:%M:%S')"