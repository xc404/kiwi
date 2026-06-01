#!/usr/bin/env python3
"""
经 SSH/scp 将 kiwi-admin 后端构建产物上传到远程主机。

在仓库根执行 mvn -pl kiwi-admin/backend -am package（与根 README 多模块建议一致）。
连接与部署选项来自 conf/build.local.yaml（`ssh`、`deploy` 块），见 conf/build.example.yaml。
构建产物为应用 thin jar + 依赖 lib jar；mvn 输出在 target/，脚本会同步到 backend/bin/ 再上传。
incremental 为 true 且远端 lib 未过期时通常只构建/上传应用 jar（不启用 -Plib-jar）；
为 false（或依赖 lib 过期、远端无 lib）时构建并上传应用 jar 与 lib jar；并同步 config/、restart.sh、stop.sh（不一致时交互确认覆盖）。
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
# 仓库根（kiwi/）：与根 README 一致，在此执行 mvn -pl kiwi-admin/backend -am 以编依赖模块
REPO_ROOT = KIWI_ADMIN_ROOT.parent
ROOT_POM = REPO_ROOT / "pom.xml"
MAVEN_PL_BACKEND = "kiwi-admin/backend"
DEFAULT_CONFIG_PATH = SCRIPT_DIR / "conf" / "build.local.yaml"
ARTIFACT_VERSION = "1.0.0-SNAPSHOT"
ARTIFACT_BASENAME = f"kiwi-admin-{ARTIFACT_VERSION}"
BIN_DIR = BACKEND_ROOT / "bin"
MAVEN_APP_JAR = BACKEND_ROOT / "target" / f"{ARTIFACT_BASENAME}.jar"
MAVEN_LIB_JAR = BACKEND_ROOT / "target" / f"{ARTIFACT_BASENAME}-lib.jar"
DEFAULT_APP_JAR = BIN_DIR / "kiwi-admin.jar"
DEFAULT_LIB_JAR = BIN_DIR / "kiwi-admin-lib.jar"
SPRING_RESOURCES_DIR = BACKEND_ROOT / "src" / "main" / "resources"
DEFAULT_CONFIG_DIR = BIN_DIR / "config"
DEFAULT_SPRING_PROFILE = "dev"
DEPLOY_SHELL_SCRIPTS = ("restart.sh", "stop.sh")


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
class DeploySettings:
    """部署选项（由 YAML 的 deploy 块解析）。"""

    app_jar: Path
    lib_jar: Path
    config_dir: Path
    spring_profiles_active: str
    remote_dir: str
    remote_app_name: str
    remote_lib_name: str
    mvn: str | None
    skip_build: bool
    incremental: bool


@dataclass(frozen=True)
class Conn:
    target: SshTarget
    ssh_label: str
    remote_dir: str
    settings: DeploySettings


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


def _scp_upload_paramiko(
    c: Conn, local_jar: Path, remote_dir: str, remote_rel_path: str
) -> None:
    remote_path = _remote_rel_path(remote_dir, remote_rel_path)
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
        "或在 YAML deploy.mvn 中指定 mvn.cmd（Windows）或 mvn 的完整路径。",
        file=sys.stderr,
    )
    sys.exit(1)


def run_mvn_package(mvn_override: str | None, *, build_lib_jar: bool = True) -> None:
    """在仓库根执行 reactor 构建，与根 README 中 mvn -pl kiwi-admin/backend -am 一致。"""
    exe = resolve_mvn_executable(mvn_override)
    if not ROOT_POM.is_file():
        print(
            f"未找到仓库根 POM：{ROOT_POM}。deploy.py 需放在 kiwi-admin/backend/script/ 下，"
            "且应在完整克隆的 kiwi 仓库中使用。",
            file=sys.stderr,
        )
        sys.exit(1)
    cmd = [
        exe,
        "-pl",
        MAVEN_PL_BACKEND,
        "-am",
        "clean",
        "package",
        "-DskipTests",
    ]
    if build_lib_jar:
        cmd.append("-Plib-jar")
    try:
        subprocess.run(cmd, cwd=str(REPO_ROOT), check=True)
    except subprocess.CalledProcessError as exc:
        print(
            f"\nmvn package 失败（退出码 {exc.returncode}）。"
            "请查看上方 Maven 编译/依赖错误；常见原因：未使用 JDK 25、或 reactor 模块编译失败。",
            file=sys.stderr,
        )
        sys.exit(exc.returncode if exc.returncode else 1)


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


def remote_file_exists(c: Conn, remote_file_path: str) -> bool:
    esc = remote_file_path.replace("'", "'\\''")
    script = f"""set -euo pipefail
if [[ -f '{esc}' ]]; then exit 0; else exit 1; fi
"""
    code, _, _ = run_remote_capture(c, script)
    return code == 0


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


def _remote_rel_path(remote_dir: str, remote_rel_path: str) -> str:
    return f"{remote_dir.rstrip('/')}/{remote_rel_path}"


def remote_prepare_dir_and_backup(c: Conn, remote_dir: str, remote_rel_path: str) -> None:
    esc_target = _remote_rel_path(remote_dir, remote_rel_path).replace("'", "'\\''")
    esc_parent = str(Path(remote_rel_path).parent).replace("'", "'\\''")
    esc_dir = remote_dir.replace("'", "'\\''")
    script = f"""set -euo pipefail
mkdir -p '{esc_dir}'
if [[ '{esc_parent}' != '.' ]]; then
  mkdir -p '{esc_dir}/{esc_parent}'
fi
TARGET='{esc_target}'
if [[ -f "$TARGET" ]]; then
  cp -a "$TARGET" "$TARGET.bak"
fi
"""
    run_remote_bash(c, script)


def lib_jar_stale(settings: DeploySettings, *, remote_lib_present: bool) -> bool:
    """依赖 lib jar 是否可能过期（POM 变更后需全量部署）。"""
    if not settings.lib_jar.is_file():
        return not remote_lib_present
    return settings.lib_jar.stat().st_mtime < _reactor_pom_mtimes()


def _confirm_overwrite(message: str) -> bool:
    """交互确认是否覆盖远端已有且内容不一致的文件。"""
    if not sys.stdin.isatty():
        print(f"非交互终端，跳过覆盖：{message}", file=sys.stderr)
        return False
    try:
        answer = input(f"{message} [y/N]: ").strip().lower()
    except (EOFError, KeyboardInterrupt):
        print(file=sys.stderr)
        return False
    return answer in ("y", "yes")


def _upload_file_to_remote(
    c: Conn,
    local_path: Path,
    remote_rel_path: str,
    *,
    label: str,
) -> None:
    remote_dir = c.remote_dir
    remote_path = _remote_rel_path(remote_dir, remote_rel_path)
    remote_prepare_dir_and_backup(c, remote_dir, remote_rel_path)
    scp_upload(c, local_path, remote_dir, remote_rel_path)
    print(f"已上传 {label} 至 {c.ssh_label}:{remote_path}")


def _deploy_upload_managed_file(
    c: Conn,
    local_path: Path,
    remote_rel_path: str,
    *,
    label: str,
) -> bool:
    """
    用于 config、shell 脚本等文本产物：
    远端不存在则直接上传；存在且 SHA256 一致则跳过；不一致时询问是否覆盖。
    """
    remote_path = _remote_rel_path(c.remote_dir, remote_rel_path)
    local_hash = _local_file_sha256(local_path)

    if not remote_file_exists(c, remote_path):
        print(f"远端尚无 {label}，将上传。")
        _upload_file_to_remote(c, local_path, remote_rel_path, label=label)
        return True

    remote_hash = remote_jar_sha256(c, remote_path)
    if remote_hash is not None and remote_hash == local_hash:
        print(f"{label} 与远端一致，跳过上传。")
        return False

    if remote_hash is None:
        print(f"无法校验远端 {label} 的 SHA256。")
    else:
        print(f"远端 {label} 与本地内容不一致。")

    if not _confirm_overwrite(f"是否覆盖远端 {remote_path}？"):
        print(f"已跳过 {label}。")
        return False

    _upload_file_to_remote(c, local_path, remote_rel_path, label=label)
    return True


def remote_chmod_executable(c: Conn, remote_dir: str, script_names: tuple[str, ...]) -> None:
    if not script_names:
        return
    esc_dir = remote_dir.rstrip("/").replace("'", "'\\''")
    args = " ".join(f"'{n}'" for n in script_names)
    script = f"""set -euo pipefail
cd '{esc_dir}'
chmod +x {args}
"""
    run_remote_bash(c, script)


def scp_upload(c: Conn, local_path: Path, remote_dir: str, remote_rel_path: str) -> None:
    if _password_uses_paramiko(c):
        _scp_upload_paramiko(c, local_path, remote_dir, remote_rel_path)
        return
    cmd, host, env = scp_cmd(c)
    dest = f"{host}:{_remote_rel_path(remote_dir, remote_rel_path)}"
    full_cmd = cmd + [str(local_path), dest]
    size = local_path.stat().st_size
    print(
        f"正在通过 scp 上传 {local_path.name}（{_human_bytes(size)}）→ {dest} …",
        file=sys.stderr,
    )
    _run_scp_subprocess_with_progress(
        full_cmd,
        env,
        label=local_path.name,
        dest_display=dest,
    )
    print("  scp 上传完成。", file=sys.stderr)


def _deploy_upload_artifact(
    c: Conn,
    local_path: Path,
    remote_rel_path: str,
    *,
    incremental: bool,
    label: str,
) -> bool:
    remote_path = _remote_rel_path(c.remote_dir, remote_rel_path)
    local_hash = _local_file_sha256(local_path)
    if incremental:
        remote_hash = remote_jar_sha256(c, remote_path)
        if remote_hash is not None and remote_hash == local_hash:
            print(f"增量：本地与远端 {label} 的 SHA256 一致，跳过备份与上传。")
            return False
        if remote_hash is None:
            print(f"远端尚无 {label} 或无法校验，将执行上传。")
    _upload_file_to_remote(c, local_path, remote_rel_path, label=label)
    return True


def sync_spring_config_to_bin(settings: DeploySettings) -> None:
    """将 src/main/resources 下 Spring 配置复制到 bin/config/。"""
    profile = settings.spring_profiles_active
    dest = settings.config_dir
    dest.mkdir(parents=True, exist_ok=True)

    base_yml = SPRING_RESOURCES_DIR / "application.yml"
    profile_yml = SPRING_RESOURCES_DIR / f"application-{profile}.yml"
    if not base_yml.is_file():
        print(f"未找到 Spring 配置: {base_yml}", file=sys.stderr)
        sys.exit(1)

    shutil.copy2(base_yml, dest / "application.yml")
    if profile_yml.is_file():
        shutil.copy2(profile_yml, dest / profile_yml.name)
    else:
        print(
            f"警告：未找到 {profile_yml}，仅同步 application.yml。",
            file=sys.stderr,
        )

    props = dest / "application.properties"
    props.write_text(
        f"spring.profiles.active={profile}\n",
        encoding="utf-8",
    )
    names = ", ".join(p.name for p in sorted(dest.iterdir()) if p.is_file())
    print(f"已同步 Spring 配置至 {dest}（profile={profile}）：{names}")


def deploy_config_files(c: Conn, settings: DeploySettings) -> bool:
    """上传 bin/config/ 至远端 config/ 目录（不一致时确认覆盖）。"""
    config_dir = settings.config_dir
    if not config_dir.is_dir():
        print(f"本地配置目录不存在: {config_dir}", file=sys.stderr)
        sys.exit(1)
    uploaded = False
    for path in sorted(config_dir.iterdir()):
        if not path.is_file():
            continue
        rel = f"config/{path.name}"
        if _deploy_upload_managed_file(c, path, rel, label=rel):
            uploaded = True
    return uploaded


def deploy_shell_scripts(c: Conn, settings: DeploySettings) -> bool:
    """上传 backend/bin/ 下的 restart.sh、stop.sh 至远端部署目录（不一致时确认覆盖）。"""
    uploaded = False
    chmod_names: list[str] = []
    for name in DEPLOY_SHELL_SCRIPTS:
        local = BIN_DIR / name
        if not local.is_file():
            print(f"警告：未找到本地脚本 {local}，跳过。", file=sys.stderr)
            continue
        if _deploy_upload_managed_file(c, local, name, label=name):
            uploaded = True
            chmod_names.append(name)
    if chmod_names:
        remote_chmod_executable(c, settings.remote_dir, tuple(chmod_names))
    return uploaded


def sync_maven_outputs_to_bin(settings: DeploySettings, *, sync_lib: bool) -> None:
    """将 target/ 下 Maven 产物复制到 bin/（或 YAML 配置的 app_jar、lib_jar 路径）。"""
    if not MAVEN_APP_JAR.is_file():
        print(f"Maven 未生成应用 JAR: {MAVEN_APP_JAR}", file=sys.stderr)
        sys.exit(1)
    settings.app_jar.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(MAVEN_APP_JAR, settings.app_jar)
    if sync_lib:
        if not MAVEN_LIB_JAR.is_file():
            print(f"Maven 未生成依赖 lib JAR: {MAVEN_LIB_JAR}", file=sys.stderr)
            sys.exit(1)
        settings.lib_jar.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(MAVEN_LIB_JAR, settings.lib_jar)
        print(
            f"已同步至 {settings.app_jar.parent}: "
            f"{settings.app_jar.name}、{settings.lib_jar.name}",
        )
    else:
        print(f"已同步至 {settings.app_jar.parent}: {settings.app_jar.name}")


def _maybe_run_mvn(
    settings: DeploySettings,
    mvn_override: str | None,
    *,
    build_lib_jar: bool,
) -> None:
    if settings.skip_build:
        return
    if build_lib_jar:
        print("执行 mvn package（-Plib-jar，含依赖 lib JAR）…")
    else:
        print("执行 mvn package（跳过依赖 lib JAR 构建）…")
    run_mvn_package(mvn_override, build_lib_jar=build_lib_jar)
    sync_maven_outputs_to_bin(settings, sync_lib=build_lib_jar)


def cmd_deploy(c: Conn, *, mvn_override: str | None) -> bool:
    settings = c.settings
    if settings.incremental:
        remote_lib_path = f"{settings.remote_dir.rstrip('/')}/{settings.remote_lib_name}"
        remote_lib_present = remote_file_exists(c, remote_lib_path)
        remote_lib_missing = not remote_lib_present
        lib_stale = lib_jar_stale(settings, remote_lib_present=remote_lib_present)
        build_lib_jar = remote_lib_missing or lib_stale
        if not build_lib_jar:
            print("增量：远端 lib jar 可用且未过期，跳过 lib JAR 构建与上传。")
        elif remote_lib_missing:
            print("增量：远端尚无 lib jar，转为全量部署（应用 jar + lib jar）。")
        elif lib_stale:
            print("增量：检测到依赖 lib 可能过期（POM 变更），转为全量部署（应用 jar + lib jar）。")
    else:
        build_lib_jar = True
        print("全量部署（incremental: false）：构建并上传应用 jar 与 lib jar（覆盖远端）。")

    _maybe_run_mvn(settings, mvn_override, build_lib_jar=build_lib_jar)
    sync_spring_config_to_bin(settings)
    uploaded_config = deploy_config_files(c, settings)
    uploaded_scripts = deploy_shell_scripts(c, settings)

    if not settings.app_jar.is_file():
        print(f"本地应用 JAR 不存在: {settings.app_jar}", file=sys.stderr)
        sys.exit(1)

    if build_lib_jar:
        if not settings.lib_jar.is_file():
            print(f"本地依赖 lib JAR 不存在: {settings.lib_jar}", file=sys.stderr)
            sys.exit(1)
        uploaded_lib = _deploy_upload_artifact(
            c,
            settings.lib_jar,
            settings.remote_lib_name,
            incremental=False,
            label="依赖 lib JAR",
        )
        uploaded_app = _deploy_upload_artifact(
            c,
            settings.app_jar,
            settings.remote_app_name,
            incremental=settings.incremental,
            label="应用 JAR",
        )
        return uploaded_lib or uploaded_app or uploaded_config or uploaded_scripts

    uploaded_app = _deploy_upload_artifact(
        c,
        settings.app_jar,
        settings.remote_app_name,
        incremental=True,
        label="应用 JAR",
    )
    return uploaded_app or uploaded_config or uploaded_scripts


def _bool_from_yaml(value: object | None, default: bool) -> bool:
    if value is None:
        return default
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in ("true", "yes", "1", "on"):
            return True
        if normalized in ("false", "no", "0", "off"):
            return False
        return False
    return bool(value)


def _ssh_dict_from_raw(raw: dict) -> dict:
    """支持 `ssh:` 嵌套；兼容旧版平铺（仅 ssh 相关键）。"""
    if "ssh" in raw and isinstance(raw["ssh"], dict):
        return dict(raw["ssh"])
    reserved = {"spring", "deploy"}
    deploy_keys = {
        "local_jar",
        "app_jar",
        "lib_jar",
        "config_dir",
        "spring_profiles_active",
        "spring.profiles.active",
        "remote_dir",
        "remote_jar_name",
        "remote_app_name",
        "remote_lib_name",
        "mvn",
        "skip_build",
        "incremental",
        "no_incremental",
        "package",
        "local_war",
        "bundle_zip",
        "remote_bundle_name",
    }
    return {
        k: v
        for k, v in raw.items()
        if k not in reserved and k not in deploy_keys
    }


def _deploy_dict_from_raw(raw: dict) -> dict:
    if "deploy" in raw and isinstance(raw["deploy"], dict):
        return dict(raw["deploy"])
    keys = (
        "local_jar",
        "app_jar",
        "lib_jar",
        "config_dir",
        "spring_profiles_active",
        "spring.profiles.active",
        "remote_dir",
        "remote_jar_name",
        "remote_app_name",
        "remote_lib_name",
        "mvn",
        "skip_build",
        "incremental",
        "no_incremental",
    )
    return {k: raw[k] for k in keys if k in raw}


def _resolve_backend_path(raw: object | None, default: Path) -> Path:
    if raw is None:
        return default.resolve()
    p = Path(str(raw).strip())
    if not p.is_absolute():
        p = BACKEND_ROOT / p
    return p.resolve()


def _build_deploy_settings_from_dict(deploy: dict) -> DeploySettings:
    if deploy.get("package") not in (None, "jar"):
        legacy = str(deploy.get("package")).strip().lower()
        if legacy == "zip":
            print("提示：deploy.package=zip 已废弃，请删除该字段（现统一为 jar 分包部署）。", file=sys.stderr)
        elif legacy == "war":
            print("提示：deploy.package=war 已不再支持。", file=sys.stderr)
    remote_dir = str(deploy.get("remote_dir") or "/opt/kiwi-admin")
    remote_app_name = str(
        deploy.get("remote_app_name") or deploy.get("remote_jar_name") or "kiwi-admin.jar"
    )
    remote_lib_name = str(deploy.get("remote_lib_name") or "kiwi-admin-lib.jar")
    mvn_raw = deploy.get("mvn")
    mvn = str(mvn_raw).strip() if mvn_raw else None
    skip_build = _bool_from_yaml(deploy.get("skip_build"), False)
    if "incremental" in deploy:
        incremental = _bool_from_yaml(deploy.get("incremental"), True)
    elif "no_incremental" in deploy:
        incremental = not _bool_from_yaml(deploy.get("no_incremental"), False)
    else:
        incremental = True
    app_raw = deploy.get("app_jar", deploy.get("local_jar"))
    app_jar = _resolve_backend_path(app_raw, DEFAULT_APP_JAR)
    profile_raw = deploy.get("spring_profiles_active", deploy.get("spring.profiles.active"))
    spring_profile = str(profile_raw).strip() if profile_raw else DEFAULT_SPRING_PROFILE
    config_default = app_jar.parent / "config"
    return DeploySettings(
        app_jar=app_jar,
        lib_jar=_resolve_backend_path(deploy.get("lib_jar"), DEFAULT_LIB_JAR),
        config_dir=_resolve_backend_path(deploy.get("config_dir"), config_default),
        spring_profiles_active=spring_profile,
        remote_dir=remote_dir,
        remote_app_name=remote_app_name,
        remote_lib_name=remote_lib_name,
        mvn=mvn,
        skip_build=skip_build,
        incremental=incremental,
    )


def _build_ssh_target_from_dict(ssh: dict) -> SshTarget:
    hostname = ssh.get("hostname")
    user = ssh.get("user")
    port = ssh.get("port", 22)
    auth = (ssh.get("auth") or "key").strip().lower()
    if auth not in ("key", "password"):
        print("ssh.auth 必须是 key 或 password。", file=sys.stderr)
        sys.exit(1)
    if not hostname or not user:
        print("YAML 中 ssh.hostname、ssh.user 为必填。", file=sys.stderr)
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


def load_deploy_config(path: Path) -> tuple[SshTarget, DeploySettings]:
    raw = yaml.safe_load(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        print("YAML 根节点必须是映射。", file=sys.stderr)
        sys.exit(1)
    ssh = _ssh_dict_from_raw(raw)
    deploy = _deploy_dict_from_raw(raw)
    return _build_ssh_target_from_dict(ssh), _build_deploy_settings_from_dict(deploy)


def _ensure_utf8_stdio() -> None:
    for stream in (sys.stdout, sys.stderr):
        reconf = getattr(stream, "reconfigure", None)
        if callable(reconf):
            try:
                reconf(encoding="utf-8", errors="replace")
            except (OSError, ValueError, TypeError):
                pass


def _conn_from_config(target: SshTarget, settings: DeploySettings) -> Conn:
    return Conn(
        target=target,
        ssh_label=target.label,
        remote_dir=settings.remote_dir,
        settings=settings,
    )


def _parse_args() -> argparse.Namespace:
    default_config = DEFAULT_CONFIG_PATH.relative_to(SCRIPT_DIR).as_posix()
    parser = argparse.ArgumentParser(
        description="构建并部署 kiwi-admin 后端产物到远端主机。",
    )
    parser.add_argument(
        "-c",
        "--config",
        default=default_config,
        help=(
            f"部署配置文件路径（默认: {default_config}；"
            "相对路径按 deploy.py 所在目录解析）"
        ),
    )
    return parser.parse_args()


def _resolve_config_path(config_arg: str) -> Path:
    config_path = Path(config_arg).expanduser()
    if not config_path.is_absolute():
        config_path = SCRIPT_DIR / config_path
    return config_path.resolve()


def main() -> None:
    _ensure_utf8_stdio()
    args = _parse_args()
    cfg_path = _resolve_config_path(args.config)
    if not cfg_path.is_file():
        example = SCRIPT_DIR / "conf" / "build.example.yaml"
        print(
            f"配置文件不存在: {cfg_path}\n"
            f"请复制 {example} 为 conf/build.local.yaml，并编辑 ssh、deploy 块。",
            file=sys.stderr,
        )
        sys.exit(1)
    target, settings = load_deploy_config(cfg_path)
    c = _conn_from_config(target, settings)
    _ensure_password_backend(c)
    cmd_deploy(c, mvn_override=settings.mvn)


if __name__ == "__main__":
    main()
