#!/usr/bin/env bash
#
# mdoc_reconstruct.sh
# -------------------
# Cryo-EM tilt-series 重建脚本，覆盖原 mdoc_reconstruct_old.sh 的全部步骤，
# 接收 GNU 长选项替代硬编码绝对路径，便于 Kiwi BPMN 工作流挂载。
#
# 所有命令参数对齐 cryo-em-server-backend cryo-web-server 中的
# SoftwareService / ImodSetting 当前生产值；步骤与
# cryoems-bpm/script/mdoc_reconstruct_old.sh 11 段 echo + 命令一一对应。

set -euo pipefail

# =============================================================================
# 默认值（与后端 ImodSetting/SoftwareService 当前生产值对齐）
# =============================================================================

# 路径与命名（必填，无安全默认）
workdir=""
name=""
thumb_dir=""

# 物理量（必填，来自 MDocMeta）
tilt_axis_angle=""
pixel_size=""

# 粗对齐 tiltxcorr
sigma1="0.03"
sigma2="0.05"
radius2="0.25"

# 粗对齐 newstack
coarse_bin="4"
coarse_mo="0"
coarse_fl="2"
coarse_im="1"

# patch tracking tiltxcorr
pt_border="46"
pt_patch_size="150"
pt_it="4"
pt_im="4"
pt_overlap="0.33"

# imodchopconts
chop_overlap="4"
chop_s="1"

# series_align
max_avg="5.0"

# 重建
recon_bin="4"
recon_bin1="1"
thickness="1600"
image_binned="4"
radial="0.35,0.035"
scale="0,330"
fake_sirt="30"
binvol_b="2"

# 工具路径
series_align_py="/home/cryoems/bin/py/mdoc/tilt_series_align.py"
align_recon_py="/home/cryoems/bin/py/mdoc/align_recon_v2.py"
conda_env="cryoems"

# 行为开关
dry_run="0"
verbose="0"

# =============================================================================
# 帮助
# =============================================================================
print_help() {
    cat <<'EOF'
mdoc_reconstruct.sh — Cryo-EM tilt-series 重建脚本

用法:
    mdoc_reconstruct.sh --workdir <dir> --name <prefix> --thumb-dir <dir> \
                       --tilt-axis-angle <deg> --pixel-size <nm> [其它可选参数]

必填参数:
    --workdir <dir>            mdoc 工作目录（对应 FilePathService#getMdocWorkDir）
    --name <prefix>            mdoc 实例名（对应 context.getInstance().getName()）
    --thumb-dir <dir>          缩略图输出目录（对应 FilePathService#getImageWorkDir）
    --tilt-axis-angle <deg>    倾斜轴角度（如 84.1）
    --pixel-size <nm>          像素尺寸 nm（如 0.303）

可选 — 粗对齐 tiltxcorr:
    --sigma1 <f>               默认 0.03
    --sigma2 <f>               默认 0.05
    --radius2 <f>              默认 0.25

可选 — 粗对齐 newstack:
    --coarse-bin <int>         默认 4
    --coarse-mo <int>          默认 0
    --coarse-fl <int>          默认 2
    --coarse-im <int>          默认 1

可选 — patch tracking tiltxcorr:
    --pt-border <int>          默认 46（生成 -bor 46,46）
    --pt-patch-size <int>      默认 150（生成 -size 150,150）
    --pt-it <int>              默认 4
    --pt-im <int>              默认 4
    --pt-overlap <f>           默认 0.33（生成 -overlap 0.33,0.33）

可选 — imodchopconts:
    --chop-overlap <int>       默认 4
    --chop-s <int>             默认 1

可选 — series_align:
    --max-avg <f>              默认 5.0

可选 — 重建:
    --recon-bin <int>          默认 4   （newstack 第一遍 _ali.mrc 的 -bin）
    --recon-bin1 <int>         默认 1   （newstack 第二遍 _ali_bin1.mrc 的 -bin）
    --thickness <int>          默认 1600（tilt -THICKNESS）
    --image-binned <int>       默认 4   （tilt -IMAGEBINNED）
    --radial <a,b>             默认 0.35,0.035（tilt -RADIAL）
    --scale <a,b>              默认 0,330（tilt -SCALE）
    --fake-sirt <int>          默认 30  （tilt -FakeSIRTiterations）
    --binvol <int>             默认 2   （binvol -b）

可选 — 工具路径与环境:
    --series-align-py <path>   默认 /home/cryoems/bin/py/mdoc/tilt_series_align.py
    --align-recon-py <path>    默认 /home/cryoems/bin/py/mdoc/align_recon_v2.py
    --conda-env <name>         默认 cryoems

行为开关:
    -h, --help                 打印本帮助并退出
    --dry-run                  只回显命令不执行（便于联调）
    -v, --verbose              开启 verbose（去掉 set -m，保留 set -euo pipefail）

退出码:
    0   成功
    1   选项解析或运行时错误
    2   必填参数缺失或上游产物（_raw_bin.mrc / _raw_bin.rawtilt）不存在

示例（对照 mdoc_reconstruct_old.sh 中 tomo_0518-ce_P01 的真实参数）:
    mdoc_reconstruct.sh \
        --workdir /home/cryoems/data/prod/.../mdoc/tomo_0518-ce_P01 \
        --name tomo_0518-ce_P01 \
        --thumb-dir /home/cryoems/data/prod/.../thumbnails \
        --tilt-axis-angle 84.1 \
        --pixel-size 0.303
EOF
}

# =============================================================================
# 选项解析（纯 bash case，避免 getopt/getopts 跨平台差异）
# =============================================================================
die() {
    echo "[mdoc_reconstruct] $*" >&2
    exit 1
}

require_value() {
    if [[ $# -lt 2 || -z "${2:-}" ]]; then
        die "选项 $1 需要一个参数，运行 --help 查看用法"
    fi
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --workdir)              require_value "$1" "${2:-}"; workdir="$2"; shift 2 ;;
        --name)                 require_value "$1" "${2:-}"; name="$2"; shift 2 ;;
        --thumb-dir)            require_value "$1" "${2:-}"; thumb_dir="$2"; shift 2 ;;
        --tilt-axis-angle)      require_value "$1" "${2:-}"; tilt_axis_angle="$2"; shift 2 ;;
        --pixel-size)           require_value "$1" "${2:-}"; pixel_size="$2"; shift 2 ;;
        --sigma1)               require_value "$1" "${2:-}"; sigma1="$2"; shift 2 ;;
        --sigma2)               require_value "$1" "${2:-}"; sigma2="$2"; shift 2 ;;
        --radius2)              require_value "$1" "${2:-}"; radius2="$2"; shift 2 ;;
        --coarse-bin)           require_value "$1" "${2:-}"; coarse_bin="$2"; shift 2 ;;
        --coarse-mo)            require_value "$1" "${2:-}"; coarse_mo="$2"; shift 2 ;;
        --coarse-fl)            require_value "$1" "${2:-}"; coarse_fl="$2"; shift 2 ;;
        --coarse-im)            require_value "$1" "${2:-}"; coarse_im="$2"; shift 2 ;;
        --pt-border)            require_value "$1" "${2:-}"; pt_border="$2"; shift 2 ;;
        --pt-patch-size)        require_value "$1" "${2:-}"; pt_patch_size="$2"; shift 2 ;;
        --pt-it)                require_value "$1" "${2:-}"; pt_it="$2"; shift 2 ;;
        --pt-im)                require_value "$1" "${2:-}"; pt_im="$2"; shift 2 ;;
        --pt-overlap)           require_value "$1" "${2:-}"; pt_overlap="$2"; shift 2 ;;
        --chop-overlap)         require_value "$1" "${2:-}"; chop_overlap="$2"; shift 2 ;;
        --chop-s)               require_value "$1" "${2:-}"; chop_s="$2"; shift 2 ;;
        --max-avg)              require_value "$1" "${2:-}"; max_avg="$2"; shift 2 ;;
        --recon-bin)            require_value "$1" "${2:-}"; recon_bin="$2"; shift 2 ;;
        --recon-bin1)           require_value "$1" "${2:-}"; recon_bin1="$2"; shift 2 ;;
        --thickness)            require_value "$1" "${2:-}"; thickness="$2"; shift 2 ;;
        --image-binned)         require_value "$1" "${2:-}"; image_binned="$2"; shift 2 ;;
        --radial)               require_value "$1" "${2:-}"; radial="$2"; shift 2 ;;
        --scale)                require_value "$1" "${2:-}"; scale="$2"; shift 2 ;;
        --fake-sirt)            require_value "$1" "${2:-}"; fake_sirt="$2"; shift 2 ;;
        --binvol)               require_value "$1" "${2:-}"; binvol_b="$2"; shift 2 ;;
        --series-align-py)      require_value "$1" "${2:-}"; series_align_py="$2"; shift 2 ;;
        --align-recon-py)       require_value "$1" "${2:-}"; align_recon_py="$2"; shift 2 ;;
        --conda-env)            require_value "$1" "${2:-}"; conda_env="$2"; shift 2 ;;
        --dry-run)              dry_run="1"; shift ;;
        -v|--verbose)           verbose="1"; shift ;;
        -h|--help)              print_help; exit 0 ;;
        --)                     shift; break ;;
        -*)                     die "未知选项: $1，运行 --help 查看用法" ;;
        *)                      die "未识别的位置参数: $1，运行 --help 查看用法" ;;
    esac
done

# 必填校验：缺失退出码 2（与 raw 产物缺失同档错误等级）
[[ -n "${workdir}" ]]         || { echo "[mdoc_reconstruct] --workdir 必填" >&2; exit 2; }
[[ -n "${name}" ]]            || { echo "[mdoc_reconstruct] --name 必填" >&2; exit 2; }
[[ -n "${thumb_dir}" ]]       || { echo "[mdoc_reconstruct] --thumb-dir 必填" >&2; exit 2; }
[[ -n "${tilt_axis_angle}" ]] || { echo "[mdoc_reconstruct] --tilt-axis-angle 必填" >&2; exit 2; }
[[ -n "${pixel_size}" ]]      || { echo "[mdoc_reconstruct] --pixel-size 必填" >&2; exit 2; }

# verbose 模式：仅影响日志显示密度；保留 set -euo pipefail，避免静默吞错
if [[ "${verbose}" == "1" ]]; then
    set -x
fi

# =============================================================================
# conda 激活（与旧脚本 source ~/.bashrc + source activate cryoems 一致；
#             支持 --conda-env 覆盖；--dry-run 时跳过；
#             无 conda 的执行环境（如 CI 容器）静默跳过激活避免误退出）
# =============================================================================
if [[ "${dry_run}" != "1" ]]; then
    if [[ -f "${HOME}/.bashrc" ]]; then
        # shellcheck disable=SC1091
        source "${HOME}/.bashrc" || true
    fi
    if command -v conda >/dev/null 2>&1; then
        # shellcheck disable=SC1091
        source activate "${conda_env}"
    else
        echo "[mdoc_reconstruct] warn: 未检测到 conda 可执行，跳过 'source activate ${conda_env}'" >&2
    fi
fi

# =============================================================================
# 路径派生
# =============================================================================
# 全部按 ${workdir}/${name}.<ext> 派生，命名与下列 Handler 中
# filePathService.getMdocWorkDir(context) + "/" + name + ".<ext>" 一一对应：
#   - cryo-web-server/.../task/tilt/stack/MdocStackHandler.java   -> _raw_bin.mrc / _raw_bin.rawtilt
#   - cryo-web-server/.../task/tilt/align/CoarseAlign.java        -> .prexf / .prexg / _preali.mrc
#   - cryo-web-server/.../task/tilt/patchtracking/PatchTracking.java -> _pt.fid / .fid / _fid.xf / .resmod
#   - cryo-web-server/.../task/tilt/seriesalign/SeriesAlign.java  -> .tltxf / .resid（python 隐式产物）
#   - cryo-web-server/.../task/tilt/recon/AlignRecon.java         -> _ali.mrc / _ali_bin1.mrc / _full_rec_bin4.mrc
# 缩略图独立目录与 align_recon_v2.py 行为一致。
raw_stack="${workdir}/${name}_raw_bin.mrc"
raw_tilt="${workdir}/${name}_raw_bin.rawtilt"
prexf="${workdir}/${name}.prexf"
prexg="${workdir}/${name}.prexg"
preali="${workdir}/${name}_preali.mrc"
pt_fid="${workdir}/${name}_pt.fid"
fid="${workdir}/${name}.fid"
tltxf="${workdir}/${name}.tltxf"
resid="${workdir}/${name}.resid"
resmod="${workdir}/${name}.resmod"
fid_xf="${workdir}/${name}_fid.xf"
ali="${workdir}/${name}_ali.mrc"
ali_bin1="${workdir}/${name}_ali_bin1.mrc"
full_rec="${workdir}/${name}_full_rec_bin4.mrc"
thumb="${thumb_dir}/${name}_full_rec_bin8_unit8.mrc"

# 上游产物存在性校验：等价于 MdocStackHandler 之后的语义边界
if [[ "${dry_run}" != "1" ]]; then
    [[ -f "${raw_stack}" ]] || { echo "[mdoc_reconstruct] raw_stack 不存在: ${raw_stack}" >&2; exit 2; }
    [[ -f "${raw_tilt}" ]]  || { echo "[mdoc_reconstruct] raw_tilt 不存在: ${raw_tilt}" >&2; exit 2; }
fi

# =============================================================================
# run_step 封装：统一 started/ended 时间戳；--dry-run 下只回显
# =============================================================================
run_step() {
    local label="$1"; shift
    echo "${label} started at $(date +'%Y-%m-%d %H:%M:%S')"
    if [[ "${dry_run}" == "1" ]]; then
        # 回显命令以便联调对照（注意 IFS 默认空格连接，多空格会被压缩）
        echo "+ $*"
    else
        if ! "$@"; then
            local rc=$?
            echo "[mdoc_reconstruct] step '${label}' failed (exit=${rc}): $*" >&2
            exit "${rc}"
        fi
    fi
    echo "${label} ended at $(date +'%Y-%m-%d %H:%M:%S')"
}

# =============================================================================
# 11 段命令（顺序与 mdoc_reconstruct_old.sh 完全一致；
#           参数顺序对齐 cryo-em-server-backend SoftwareService 字面顺序）
# =============================================================================

# 1) tiltxcorr（粗对齐）-> prexf
run_step "tiltxcorr" \
    tiltxcorr \
        -inp "${raw_stack}" \
        -ou "${prexf}" \
        -ro "${tilt_axis_angle}" \
        -ti "${raw_tilt}" \
        -sigma1 "${sigma1}" \
        -radius2 "${radius2}" \
        -sigma2 "${sigma2}"

# 2) xftoxg -> prexg
run_step "xftoxg" \
    xftoxg \
        -n 0 \
        -in "${prexf}" \
        -g "${prexg}"

# 3) newstack（粗对齐 _preali.mrc）-> preali
run_step "newstack" \
    newstack \
        -in "${raw_stack}" \
        -ou "${preali}" \
        -bin "${coarse_bin}" \
        -mo "${coarse_mo}" \
        -fl "${coarse_fl}" \
        -x "${prexg}" \
        -im "${coarse_im}"

# 4) tiltxcorr（patch tracking）-> pt_fid
run_step "patch_tracking_tiltxcorr" \
    tiltxcorr \
        -inp "${preali}" \
        -ou "${pt_fid}" \
        -ro "${tilt_axis_angle}" \
        -ti "${raw_tilt}" \
        -sigma1 "${sigma1}" \
        -radius2 "${radius2}" \
        -sigma2 "${sigma2}" \
        -bor "${pt_border},${pt_border}" \
        -it "${pt_it}" \
        -im "${pt_im}" \
        -size "${pt_patch_size},${pt_patch_size}" \
        -overlap "${pt_overlap},${pt_overlap}"

# 5) imodchopconts -> fid
run_step "imodchopconts" \
    imodchopconts \
        -i "${pt_fid}" \
        -ou "${fid}" \
        -overlap "${chop_overlap}" \
        -s "${chop_s}"

# 6) tilt_series_align.py -> tltxf / resid 等隐式产物
run_step "series_align" \
    python "${series_align_py}" \
        --image_file "${preali}" \
        --model_file "${fid}" \
        --tilt_path "${raw_tilt}" \
        --tilt_axis_angle "${tilt_axis_angle}" \
        --pixel_size "${pixel_size}" \
        --max_avg "${max_avg}"

# 7) xfproduct（合并 prexg + tltxf）-> fid_xf
run_step "xfproduct" \
    xfproduct \
        -in1 "${prexg}" \
        -in2 "${tltxf}" \
        -ou "${fid_xf}" \
        --s "1,4"

# 8) patch2imod -> resmod
run_step "patch2imod" \
    patch2imod \
        -s 10 \
        "${resid}" \
        -ou "${resmod}"

# 9) newstack（align_recon 第一遍，bin=recon_bin）-> ali
run_step "align_recon_newstack1" \
    newstack \
        -in "${raw_stack}" \
        -ou "${ali}" \
        -bin "${recon_bin}" \
        -x "${fid_xf}"

# 10) newstack（align_recon 第二遍，bin=recon_bin1）-> ali_bin1
run_step "align_recon_newstack2" \
    newstack \
        -in "${raw_stack}" \
        -ou "${ali_bin1}" \
        -bin "${recon_bin1}" \
        -x "${fid_xf}"

# 11) tilt（重建）-> full_rec
run_step "align_tilt" \
    tilt \
        -inp "${ali}" \
        -ou "${full_rec}" \
        -TILTFILE "${raw_tilt}" \
        -XTILTFILE "${tltxf}" \
        -THICKNESS "${thickness}" \
        -IMAGEBINNED "${image_binned}" \
        -RADIAL "${radial}" \
        -FalloffIsTrueSigma 1 \
        -XAXISTILT 0 \
        -SCALE "${scale}" \
        -PERPENDICULAR \
        -MODE 2 \
        -AdjustOrigin \
        -ActionIfGPUFails 1,2 \
        -FakeSIRTiterations "${fake_sirt}" \
        -SUBSETSTART 0,0

# 12) binvol（原位覆盖 full_rec）
run_step "align_binvol" \
    binvol \
        -b "${binvol_b}" \
        -o "${full_rec}" \
        "${full_rec}"

# 13) align_recon_v2.py -> thumb
run_step "align_recon_align_recon" \
    python "${align_recon_py}" \
        -in "${full_rec}" \
        -ou "${thumb}"
