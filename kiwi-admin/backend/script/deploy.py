#!/usr/bin/env python3
"""
经 SSH/scp 将 kiwi-admin 后端 JAR 上传到远程主机。

在仓库根执行 mvn -pl kiwi-admin/backend -am package（与根 README 多模块建议一致）。
连接信息来自结构化 YAML（--config），见本脚本同目录下 conf/remote.example.yaml（`ssh` 块）。
auth: password 时：优先使用 sshpass + 系统 ssh/scp；若无 sshpass 则使用 paramiko（见同目录 requirements-remote.txt）。

依赖：在本脚本目录执行 pip install -r requirements-remote.txt
"""
from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import subprocess
import sys
import threading
import time
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
# 本脚本位于 kiwi-admin/backend/script/：向上两级为 kiwi-admin
KIWI_ADMIN_ROOT = SCRIPT_DIR.parent.parent
BACKEND_ROOT = KIWI_ADMIN_ROOT / "backend"
REQUIREMENTS_REMOTE_TXT = SCRIPT_DIR / "requirements-remote.txt"

try:
    import yaml
except ImportError:
    print(
        f"缺少 PyYAML，请执行: pip install -r {REQUIREMENTS_REMOTE_TXT}",
        file=sys.stderr,
    )
    sys.exit(1)

BACKEND_POM = BACKEND_ROOT / "pom.xml"
BACKEND_SRC_MAIN = BACKEND_ROOT / "src" / "main"
# 仓库根（kiwi/）：与根 README 一致，在此执行 mvn -pl kiwi-admin/backend -am 以编依赖模块
REPO_ROOT = KIWI_ADMIN_ROOT.parent
ROOT_POM = REPO_ROOT / "pom.xml"
MAVEN_PL_BACKEND = "kiwi-admin/backend"
DEFAULT_LOCAL_JAR = BACKEND_ROOT / "target" / "kiwi-admin-1.0.0.jar"


@dataclass(frozen=True)
class SshTarget:
    """单主机 SSH 目标（由 YAML 解析）。"""

    label: str
    hostname: str
    user: str
    port: int
    auth: str  # key | password
    identity_file: str | None
    password: str | None
    password_env: str | None
    strict_host_key_checking: str


@dataclass(frozen=True)
class Conn:
    target: SshTarget
    ssh_label: str
    local_jar: Path
    remote_dir: str
    remote_jar_name: str


def _ssh_prefix_and_env(t: SshTarget) -> tuple[list[str], dict[str, str]]:
    """password 且使用系统 ssh 时前置 sshpass，并把密码写入本次子进程环境。"""
    env = dict(os.environ)
    if t.auth != "password":
        return [], env
    pw = _resolve_password(t)
    if not pw:
        print(
            "auth: password 时需配置 YAML 的 password、"
            "或 password_env 指向的环境变量、或设置 KIWI_SSH_PASSWORD。",
            file=sys.stderr,
        )
        sys.exit(1)
    if shutil.which("sshpass"):
        env["SSHPASS"] = pw
        return ["sshpass", "-e"], env
    return [], env


def _resolve_password(t: SshTarget) -> str | None:
    if t.password:
        return t.password
    env_name = t.password_env or "KIWI_SSH_PASSWORD"
    v = os.environ.get(env_name)
    if v:
        return v
    return os.environ.get("KIWI_SSH_PASSWORD")


def _ssh_scp_common_opts(t: SshTarget, *, for_scp: bool) -> list[str]:
    """ssh / scp 共用的 -p/-P、StrictHostKeyChecking、密钥等。"""
    port_flag = "-P" if for_scp else "-p"
    opts = [
        port_flag,
        str(t.port),
        "-o",
        f"StrictHostKeyChecking={t.strict_host_key_checking}",
    ]
    if t.auth == "key":
        opts += ["-o", "BatchMode=yes"]
        if t.identity_file:
            p = Path(t.identity_file).expanduser()
            opts += ["-i", str(p.resolve())]
    return opts


def _remote_user_host(t: SshTarget) -> str:
    return f"{t.user}@{t.hostname}"


def _password_uses_paramiko(c: Conn) -> bool:
    """password 且无 sshpass 时用 paramiko。"""
    return c.target.auth == "password" and not shutil.which("sshpass")


def _ensure_password_backend(c: Conn) -> None:
    if c.target.auth != "password":
        return
    if not _resolve_password(c.target):
        print(
            "auth: password 时需配置 YAML 的 password、"
            "或 password_env 指向的环境变量、或设置 KIWI_SSH_PASSWORD。",
            file=sys.stderr,
        )
        sys.exit(1)
    if shutil.which("sshpass"):
        return
    try:
        import paramiko  # noqa: F401
    except ImportError:
        print(
            "auth: password 时：可在 PATH 中安装 sshpass，"
            f"或执行 pip install paramiko（已列入 {REQUIREMENTS_REMOTE_TXT}）。",
            file=sys.stderr,
        )
        sys.exit(1)


@contextmanager
def _paramiko_client(c: Conn):
    import paramiko

    t = c.target
    pw = _resolve_password(t)
    assert pw is not None
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(
        hostname=t.hostname,
        port=t.port,
        username=t.user,
        password=pw,
        timeout=60,
        allow_agent=False,
        look_for_keys=False,
    )
    try:
        yield client
    finally:
        client.close()


def _run_remote_bash_paramiko(c: Conn, script: str) -> None:
    with _paramiko_client(c) as client:
        stdin, stdout, stderr = client.exec_command("bash -s", get_pty=False)
        stdin.write(script.encode("utf-8"))
        stdin.channel.shutdown_write()
        err_b = stderr.read()
        out_b = stdout.read()
        rc = stdout.channel.recv_exit_status()
    if rc != 0:
        msg = (err_b.decode("utf-8", errors="replace") or out_b.decode("utf-8", errors="replace")).strip()
        if msg:
            print(msg, file=sys.stderr)
        sys.exit(rc)


def _run_remote_capture_paramiko(c: Conn, script: str) -> tuple[int, str, str]:
    with _paramiko_client(c) as client:
        stdin, stdout, stderr = client.exec_command("bash -s", get_pty=False)
        stdin.write(script.encode("utf-8"))
        stdin.channel.shutdown_write()
        out_b = stdout.read()
        err_b = stderr.read()
        rc = stdout.channel.recv_exit_status()
    return (
        rc,
        out_b.decode("utf-8", errors="replace"),
        err_b.decode("utf-8", errors="replace"),
    )


def _human_bytes(n: int) -> str:
    if n < 0:
        return "?"
    for unit, label in ((1 << 30, "GiB"), (1 << 20, "MiB"), (1 << 10, "KiB")):
        if n >= unit:
            return f"{n / unit:.2f} {label}"
    return f"{n} B"


def _sftp_put_progress_cb():
    """Paramiko sftp.put(callback=…) 每块调用；在 stderr 打进度（约每 5% 刷新一行）。"""
    last_bucket = [-1]

    def cb(transferred: int, total: int) -> None:
        if total <= 0:
            return
        pct = min(100, int(100 * transferred / total))
        bucket = pct // 5
        if bucket > last_bucket[0] or transferred >= total:
            last_bucket[0] = bucket
            line = (
                f"\r  上传进度: {pct}%  "
                f"({_human_bytes(transferred)} / {_human_bytes(total)})"
            )
            print(line, end="", file=sys.stderr, flush=True)

    return cb


def _scp_upload_paramiko(c: Conn, local_jar: Path, remote_dir: str, remote_jar_name: str) -> None:
    remote_path = f"{remote_dir.rstrip('/')}/{remote_jar_name}"
    total = local_jar.stat().st_size
    print(
        f"正在通过 SFTP 上传 {local_jar.name}（{_human_bytes(total)}）→ "
        f"{c.target.user}@{c.target.hostname}:{remote_path} …",
        file=sys.stderr,
    )
    cb = _sftp_put_progress_cb()
    with _paramiko_client(c) as client:
        sftp = client.open_sftp()
        try:
            sftp.put(str(local_jar), remote_path, callback=cb)
        finally:
            sftp.close()
    print(file=sys.stderr)


def _run_scp_subprocess_with_progress(
    cmd: list[str],
    env: dict[str, str],
    *,
    label: str,
    dest_display: str,
) -> None:
    """OpenSSH scp 无原生进度：打印体积并周期性提示已等待时间。"""
    stop = threading.Event()

    def heartbeat() -> None:
        waited = 0
        while not stop.wait(2.0):
            waited += 2
            print(
                f"\r  scp 进行中… {waited}s  ({label} → {dest_display})",
                end="",
                file=sys.stderr,
                flush=True,
            )

    t = threading.Thread(target=heartbeat, daemon=True)
    t.start()
    try:
        subprocess.run(cmd, check=True, env=env)
    finally:
        stop.set()
        time.sleep(0.05)
    print(file=sys.stderr)


def ssh_cmd(c: Conn) -> tuple[list[str], dict[str, str]]:
    t = c.target
    prefix, env = _ssh_prefix_and_env(t)
    inner = ["ssh", *_ssh_scp_common_opts(t, for_scp=False), _remote_user_host(t)]
    return prefix + inner, env


def scp_cmd(c: Conn) -> tuple[list[str], str, dict[str, str]]:
    t = c.target
    prefix, env = _ssh_prefix_and_env(t)
    inner = ["scp", *_ssh_scp_common_opts(t, for_scp=True)]
    return prefix + inner, _remote_user_host(t), env


def run_remote_bash(c: Conn, script: str) -> None:
    if _password_uses_paramiko(c):
        _run_remote_bash_paramiko(c, script)
        return
    cmd, env = ssh_cmd(c)
    cmd = cmd + ["bash", "-s"]
    subprocess.run(cmd, input=script, text=True, check=True, env=env)


def run_remote_capture(c: Conn, script: str) -> tuple[int, str, str]:
    if _password_uses_paramiko(c):
        return _run_remote_capture_paramiko(c, script)
    cmd, env = ssh_cmd(c)
    cmd = cmd + ["bash", "-s"]
    p = subprocess.run(
        cmd,
        input=script,
        text=True,
        capture_output=True,
        env=env,
    )
    return p.returncode, p.stdout, p.stderr


def resolve_mvn_executable(override: str | None) -> str:
    """
    解析 Maven 可执行路径。Windows 会尝试 mvn.cmd；支持 MVN / MAVEN_CMD / MAVEN_HOME。
    仅在需要执行 package 时调用，避免仅上传时依赖 mvn。
    """
    if override:
        o = override.strip()
        p = Path(o)
        if p.is_file():
            return str(p.resolve())
        w = shutil.which(o)
        if w:
            return w
        print(f"无法解析 Maven 可执行文件: {override}", file=sys.stderr)
        sys.exit(1)

    for env_key in ("MVN", "MAVEN_CMD"):
        v = os.environ.get(env_key)
        if not v:
            continue
        v = v.strip().strip('"')
        p = Path(v)
        if p.is_file():
            return str(p.resolve())
        w = shutil.which(v)
        if w:
            return w

    mh = (os.environ.get("MAVEN_HOME") or "").strip().strip('"')
    if mh:
        bin_dir = Path(mh) / "bin"
        if sys.platform == "win32":
            for n in ("mvn.cmd", "mvn.exe", "mvn"):
                cand = bin_dir / n
                if cand.is_file():
                    return str(cand.resolve())
        else:
            cand = bin_dir / "mvn"
            if cand.is_file():
                return str(cand.resolve())

    search = ("mvn.cmd", "mvn") if sys.platform == "win32" else ("mvn", "mvn.cmd")
    for name in search:
        w = shutil.which(name)
        if w:
            return w

    print(
        "未找到 Maven（mvn）。请安装 Maven 并加入 PATH，或设置环境变量 MAVEN_HOME / MVN，"
        "或使用 --mvn 指定 mvn.cmd（Windows）或 mvn 的完整路径。",
        file=sys.stderr,
    )
    sys.exit(1)


def run_mvn_package(mvn_override: str | None) -> None:
    """在仓库根执行 reactor 构建，与根 README 中 mvn -pl kiwi-admin/backend -am 一致。"""
    exe = resolve_mvn_executable(mvn_override)
    if not ROOT_POM.is_file():
        print(
            f"未找到仓库根 POM：{ROOT_POM}。remote_run.py 需放在 kiwi-admin/backend/script/ 下，"
            "且应在完整克隆的 kiwi 仓库中使用。",
            file=sys.stderr,
        )
        sys.exit(1)
    subprocess.run(
        [
            exe,
            "-pl",
            MAVEN_PL_BACKEND,
            "-am",
            "package",
            "-DskipTests",
        ],
        cwd=str(REPO_ROOT),
        check=True,
    )


def _local_file_sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def _reactor_pom_mtimes() -> float:
    """参与 backend 反应堆的各 POM 最新修改时间。"""
    mt = 0.0
    for p in (ROOT_POM, BACKEND_POM):
        if p.is_file():
            mt = max(mt, p.stat().st_mtime)
    for rel in ("kiwi-common/pom.xml", "kiwi-bpmn/pom.xml"):
        pp = REPO_ROOT / rel
        if pp.is_file():
            mt = max(mt, pp.stat().st_mtime)
    bpmn = REPO_ROOT / "kiwi-bpmn"
    if bpmn.is_dir():
        for child in bpmn.iterdir():
            if child.is_dir():
                sub = child / "pom.xml"
                if sub.is_file():
                    mt = max(mt, sub.stat().st_mtime)
    return mt


def _reactor_src_newest_mtime() -> float:
    """kiwi-common / kiwi-bpmn* / backend 的 src/main 下最新修改时间。"""
    mt = 0.0
    roots: list[Path] = []
    cm = REPO_ROOT / "kiwi-common" / "src" / "main"
    if cm.is_dir():
        roots.append(cm)
    if BACKEND_SRC_MAIN.is_dir():
        roots.append(BACKEND_SRC_MAIN)
    bpmn = REPO_ROOT / "kiwi-bpmn"
    if bpmn.is_dir():
        for child in bpmn.iterdir():
            if child.is_dir():
                sm = child / "src" / "main"
                if sm.is_dir():
                    roots.append(sm)
    for root in roots:
        for f in root.rglob("*"):
            if f.is_file():
                mt = max(mt, f.stat().st_mtime)
    return mt


def _reactor_inputs_newest_mtime() -> float:
    return max(_reactor_pom_mtimes(), _reactor_src_newest_mtime())


def should_skip_mvn(local_jar: Path) -> bool:
    """本地 JAR 不早于反应堆相关 POM/源码 → 跳过 mvn。"""
    if not local_jar.is_file():
        return False
    return local_jar.stat().st_mtime >= _reactor_inputs_newest_mtime()


def remote_jar_sha256(c: Conn, remote_jar_path: str) -> str | None:
    esc = remote_jar_path.replace("'", "'\\''")
    script = f"""set -euo pipefail
if [[ ! -f '{esc}' ]]; then exit 0; fi
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum '{esc}' | awk '{{print $1}}'
elif command -v openssl >/dev/null 2>&1; then
  openssl dgst -sha256 '{esc}' | awk '{{print $NF}}'
else
  exit 1
fi
"""
    code, out, _err = run_remote_capture(c, script)
    if code != 0:
        return None
    hx = out.strip().splitlines()
    if not hx:
        return None
    line = hx[-1].strip()
    if len(line) == 64 and all(ch in "0123456789abcdef" for ch in line.lower()):
        return line.lower()
    return None


def remote_prepare_dir_and_backup(c: Conn, remote_dir: str, remote_jar_name: str) -> None:
    script = f"""set -euo pipefail
mkdir -p '{remote_dir}'
JAR='{remote_dir}/{remote_jar_name}'
if [[ -f "$JAR" ]]; then
  cp -a "$JAR" "$JAR.bak"
fi
"""
    run_remote_bash(c, script)


def scp_upload(c: Conn, local_jar: Path, remote_dir: str, remote_jar_name: str) -> None:
    if _password_uses_paramiko(c):
        _scp_upload_paramiko(c, local_jar, remote_dir, remote_jar_name)
        return
    cmd, host, env = scp_cmd(c)
    dest = f"{host}:{remote_dir}/{remote_jar_name}"
    full_cmd = cmd + [str(local_jar), dest]
    size = local_jar.stat().st_size
    print(
        f"正在通过 scp 上传 {local_jar.name}（{_human_bytes(size)}）→ {dest} …",
        file=sys.stderr,
    )
    _run_scp_subprocess_with_progress(
        full_cmd,
        env,
        label=local_jar.name,
        dest_display=dest,
    )
    print("  scp 上传完成。", file=sys.stderr)


def cmd_deploy(
    c: Conn,
    *,
    skip_build: bool,
    incremental: bool,
    mvn_override: str | None,
) -> bool:
    local_jar = c.local_jar
    remote_dir = c.remote_dir
    remote_jar_name = c.remote_jar_name
    remote_jar_path = f"{remote_dir}/{remote_jar_name}"

    if not skip_build:
        if incremental and should_skip_mvn(local_jar):
            print(
                "增量：本地 JAR 不早于反应堆相关 POM（含根父 POM、kiwi-common/kiwi-bpmn 等）"
                "与 src/main 修改时间，跳过 mvn package。",
            )
        else:
            if not incremental:
                print("全量：执行 mvn package …")
            run_mvn_package(mvn_override)

    if not local_jar.is_file():
        print(f"本地 JAR 不存在: {local_jar}", file=sys.stderr)
        sys.exit(1)

    local_hash = _local_file_sha256(local_jar)
    remote_hash: str | None = None
    if incremental:
        remote_hash = remote_jar_sha256(c, remote_jar_path)
        if remote_hash is not None and remote_hash == local_hash:
            print("增量：本地与远端 JAR 的 SHA256 一致，跳过备份与上传。")
            return False
        if remote_hash is None:
            print("远端尚无同名 JAR 或无法校验，将执行完整上传。")

    remote_prepare_dir_and_backup(c, remote_dir, remote_jar_name)
    scp_upload(c, local_jar, remote_dir, remote_jar_name)
    print(f"已上传至 {c.ssh_label}:{remote_dir}/{remote_jar_name}")
    return True


def _ssh_dict_from_raw(raw: dict) -> dict:
    """支持 `ssh:` 嵌套；兼容旧版平铺（仅 ssh 相关键）。"""
    if "ssh" in raw and isinstance(raw["ssh"], dict):
        return dict(raw["ssh"])
    reserved = {"spring"}
    return {k: v for k, v in raw.items() if k not in reserved}


def _build_ssh_target_from_dict(
    ssh: dict,
    *,
    override_hostname: str | None,
    override_user: str | None,
    override_port: int | None,
) -> SshTarget:
    hostname = override_hostname or ssh.get("hostname")
    user = override_user or ssh.get("user")
    port = override_port if override_port is not None else ssh.get("port", 22)
    auth = (ssh.get("auth") or "key").strip().lower()
    if auth not in ("key", "password"):
        print("ssh.auth 必须是 key 或 password。", file=sys.stderr)
        sys.exit(1)
    if not hostname or not user:
        print("YAML 中 ssh.hostname、ssh.user 为必填（或通过命令行覆盖）。", file=sys.stderr)
        sys.exit(1)
    if not isinstance(port, int):
        port = int(port)

    label = str(ssh.get("host") or f"{user}@{hostname}")
    identity = ssh.get("identity_file")
    password_raw = ssh.get("password")
    password = str(password_raw) if password_raw is not None else None
    password_env = ssh.get("password_env")
    strict_ = str(ssh.get("strict_host_key_checking") or "accept-new")

    if auth != "key":
        identity = None

    return SshTarget(
        label=label,
        hostname=str(hostname),
        user=str(user),
        port=port,
        auth=auth,
        identity_file=str(identity) if identity else None,
        password=str(password) if password else None,
        password_env=str(password_env) if password_env else None,
        strict_host_key_checking=strict_,
    )


def load_remote_config(
    path: Path,
    *,
    override_hostname: str | None,
    override_user: str | None,
    override_port: int | None,
) -> SshTarget:
    raw = yaml.safe_load(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        print("YAML 根节点必须是映射。", file=sys.stderr)
        sys.exit(1)
    ssh = _ssh_dict_from_raw(raw)
    return _build_ssh_target_from_dict(
        ssh,
        override_hostname=override_hostname,
        override_user=override_user,
        override_port=override_port,
    )


def add_connection_args(p: argparse.ArgumentParser) -> None:
    p.add_argument(
        "--config",
        type=Path,
        required=True,
        metavar="PATH",
        help="结构化 YAML（见 kiwi-admin/backend/script/conf/remote.example.yaml：ssh）",
    )
    p.add_argument(
        "--hostname",
        default=None,
        help="覆盖 YAML 中的 hostname",
    )
    p.add_argument(
        "--user",
        default=None,
        help="覆盖 YAML 中的 user",
    )
    p.add_argument(
        "--port",
        type=int,
        default=None,
        help="覆盖 YAML 中的 port",
    )
    p.add_argument(
        "--local-jar",
        type=Path,
        default=DEFAULT_LOCAL_JAR,
        help=f"本地 JAR 路径（默认：{DEFAULT_LOCAL_JAR.relative_to(KIWI_ADMIN_ROOT)}）",
    )
    p.add_argument(
        "--remote-dir",
        default="/opt/kiwi-admin",
        help="远端部署目录（默认：/opt/kiwi-admin）",
    )
    p.add_argument(
        "--remote-jar-name",
        default="kiwi-admin.jar",
        help="远端 JAR 文件名（默认：kiwi-admin.jar）",
    )
    p.add_argument(
        "--mvn",
        default=None,
        metavar="PATH",
        help="Maven 可执行文件（默认：从 PATH 查找 mvn / mvn.cmd，或 MAVEN_HOME、环境变量 MVN）",
    )
    p.add_argument(
        "--skip-build",
        action="store_true",
        help="跳过 mvn package",
    )
    p.add_argument(
        "--no-incremental",
        action="store_true",
        help="关闭增量：始终 mvn（除非 --skip-build）并始终上传",
    )


def build_parser() -> argparse.ArgumentParser:
    ex_yaml = SCRIPT_DIR / "conf" / "remote.example.yaml"
    p = argparse.ArgumentParser(
        description="Kiwi-admin 远程 JAR 上传（SSH + scp + 可选 mvn 构建）。",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=f"""
增量更新（默认开启）：
  · 未指定 --skip-build 时：若本地 JAR 已存在且不早于根 POM、backend 与各依赖模块 POM
    及 kiwi-common / kiwi-bpmn* / backend 下 src/main 的最新修改时间，则跳过 mvn package。
  · 构建命令与仓库根 README 一致：在仓库根执行 mvn -pl kiwi-admin/backend -am package -DskipTests。
  · 上传前：比较本地与远端 JAR 的 SHA256；一致则跳过备份与 scp。

全量：使用 --no-incremental 将始终执行 mvn（在未 --skip-build 时）并始终上传。

密码认证：YAML 中 auth: password，并配置 password 或 password_env / KIWI_SSH_PASSWORD。
  优先使用 sshpass + 系统 ssh/scp；若无 sshpass 则使用 paramiko（见 {REQUIREMENTS_REMOTE_TXT}）。

配置示例：{ex_yaml}
""".strip(),
    )
    add_connection_args(p)
    return p


def _ensure_utf8_stdio() -> None:
    for stream in (sys.stdout, sys.stderr):
        reconf = getattr(stream, "reconfigure", None)
        if callable(reconf):
            try:
                reconf(encoding="utf-8", errors="replace")
            except (OSError, ValueError, TypeError):
                pass


def _conn_from_ns(ns: argparse.Namespace) -> Conn:
    cfg_path = Path(ns.config).expanduser().resolve()
    if not cfg_path.is_file():
        print(f"配置文件不存在: {cfg_path}", file=sys.stderr)
        sys.exit(1)
    target = load_remote_config(
        cfg_path,
        override_hostname=ns.hostname,
        override_user=ns.user,
        override_port=ns.port,
    )

    return Conn(
        target=target,
        ssh_label=target.label,
        local_jar=Path(ns.local_jar).resolve(),
        remote_dir=ns.remote_dir,
        remote_jar_name=ns.remote_jar_name,
    )


def main(argv: list[str] | None = None) -> None:
    _ensure_utf8_stdio()
    argv = argv if argv is not None else sys.argv[1:]
    parser = build_parser()
    args = parser.parse_args(argv)
    c = _conn_from_ns(args)
    _ensure_password_backend(c)
    incremental = not args.no_incremental
    cmd_deploy(
        c,
        skip_build=args.skip_build,
        incremental=incremental,
        mvn_override=args.mvn,
    )


if __name__ == "__main__":
    main()
