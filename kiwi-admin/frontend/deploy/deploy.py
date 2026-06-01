#!/usr/bin/env python3
"""
经 SSH/SFTP 将 kiwi-admin 前端构建产物上传到远程主机。

所有参数均在 YAML（`ssh`、`deploy` 块）中配置，见 conf/build.example.yaml。
用法：python deploy.py [配置文件路径]（默认 conf/build.local.yaml）

依赖：pip install -r requirements-remote.txt
"""
from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
import threading
import time
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
FRONTEND_ROOT = SCRIPT_DIR.parent
REQUIREMENTS_REMOTE_TXT = SCRIPT_DIR / "requirements-remote.txt"
DEFAULT_CONFIG_PATH = SCRIPT_DIR / "conf" / "build.local.yaml"
DEFAULT_DIST_DIR = FRONTEND_ROOT / "dist"
DEFAULT_REMOTE_DIR = "/var/www/kiwi-admin"
DEFAULT_BUILD_CONFIGURATION = "production"
DEFAULT_BASE_HREF = "/kiwi-admin/"
DEFAULT_BUILD_SCRIPT = "ng"
DEFAULT_NPM_SCRIPT = "build"
DEFAULT_SSH_PORT = 22
DEFAULT_SSH_AUTH = "key"
DEFAULT_STRICT_HOST_KEY = "accept-new"
DEFAULT_SYNC_MODE = "archive"
VALID_SYNC_MODES = frozenset({"archive", "sftp"})
PARAMIKO_CONNECT_RETRIES = 3
PARAMIKO_CONNECT_RETRY_DELAY = 2.0

try:
    import yaml
except ImportError:
    print(
        f"缺少 PyYAML，请执行: pip install -r {REQUIREMENTS_REMOTE_TXT}",
        file=sys.stderr,
    )
    sys.exit(1)


@dataclass(frozen=True)
class SshTarget:
    label: str
    hostname: str
    user: str
    port: int
    auth: str
    identity_file: str | None
    password: str | None
    password_env: str | None
    strict_host_key_checking: str


@dataclass(frozen=True)
class DeploySettings:
    dist_dir: Path
    remote_dir: str
    backup_before_upload: bool
    npm: str | None
    skip_build: bool
    build_script: str
    npm_script: str
    build_configuration: str
    base_href: str
    sync_mode: str


@dataclass(frozen=True)
class Conn:
    target: SshTarget
    ssh_label: str
    remote_dir: str
    settings: DeploySettings


def _yaml_error(message: str) -> None:
    print(message, file=sys.stderr)
    sys.exit(1)


def _require_raw(raw: dict, section: str, key: str) -> object:
    block = raw.get(section)
    if not isinstance(block, dict):
        _yaml_error(f"YAML 缺少 {section} 块。")
    if key not in block or block[key] is None:
        _yaml_error(f"YAML {section}.{key} 为必填。")
    return block[key]


def _require_str(raw: dict, section: str, key: str) -> str:
    value = _require_raw(raw, section, key)
    text = str(value).strip()
    if not text:
        _yaml_error(f"YAML {section}.{key} 不能为空。")
    return text


def _optional_str(raw: dict, section: str, key: str) -> str | None:
    block = raw.get(section)
    if not isinstance(block, dict):
        return None
    value = block.get(key)
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _optional_bool(raw: dict, section: str, key: str, default: bool) -> bool:
    block = raw.get(section)
    if not isinstance(block, dict) or key not in block or block[key] is None:
        return default
    value = block[key]
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        normalized = value.strip().lower()
        if normalized in ("true", "yes", "1", "on"):
            return True
        if normalized in ("false", "no", "0", "off"):
            return False
    _yaml_error(f"YAML {section}.{key} 必须是布尔值。")


def _optional_int(raw: dict, section: str, key: str, default: int) -> int:
    block = raw.get(section)
    if not isinstance(block, dict) or key not in block or block[key] is None:
        return default
    value = block[key]
    if isinstance(value, int):
        return value
    try:
        return int(value)
    except (TypeError, ValueError):
        _yaml_error(f"YAML {section}.{key} 必须是整数。")


def _optional_str_with_default(raw: dict, section: str, key: str, default: str) -> str:
    value = _optional_str(raw, section, key)
    return value if value is not None else default


def _resolve_frontend_path(raw: str) -> Path:
    p = Path(raw)
    if not p.is_absolute():
        p = FRONTEND_ROOT / p
    return p.resolve()


def _ssh_prefix_and_env(t: SshTarget) -> tuple[list[str], dict[str, str]]:
    env = dict(os.environ)
    if t.auth != "password":
        return [], env
    pw = _resolve_password(t)
    if not pw:
        _yaml_error(
            "auth: password 时需在 YAML 配置 ssh.password 或 ssh.password_env。"
        )
    if shutil.which("sshpass"):
        env["SSHPASS"] = pw
        return ["sshpass", "-e"], env
    return [], env


def _resolve_password(t: SshTarget) -> str | None:
    if t.password:
        return t.password
    if t.password_env:
        return os.environ.get(t.password_env)
    return None


def _ssh_scp_common_opts(t: SshTarget, *, for_scp: bool) -> list[str]:
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
    return c.target.auth == "password" and not shutil.which("sshpass")


def _ensure_password_backend(c: Conn) -> None:
    if c.target.auth != "password":
        return
    if not _resolve_password(c.target):
        _yaml_error(
            "auth: password 时需在 YAML 配置 ssh.password 或 ssh.password_env。"
        )
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


def _shell_single_quote(value: str) -> str:
    return value.replace("'", "'\\''")


def _exec_remote_bash_on_client(client: object, script: str) -> None:
    stdin, stdout, stderr = client.exec_command("bash -s", get_pty=False)  # type: ignore[attr-defined]
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


@contextmanager
def _paramiko_client(c: Conn):
    import paramiko

    t = c.target
    pw = _resolve_password(t)
    assert pw is not None
    client: paramiko.SSHClient | None = None
    last_exc: Exception | None = None
    for attempt in range(1, PARAMIKO_CONNECT_RETRIES + 1):
        candidate = paramiko.SSHClient()
        candidate.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        try:
            candidate.connect(
                hostname=t.hostname,
                port=t.port,
                username=t.user,
                password=pw,
                timeout=60,
                allow_agent=False,
                look_for_keys=False,
            )
            client = candidate
            break
        except (paramiko.SSHException, OSError) as exc:
            last_exc = exc
            candidate.close()
            if attempt < PARAMIKO_CONNECT_RETRIES:
                delay = PARAMIKO_CONNECT_RETRY_DELAY * attempt
                print(
                    f"SSH 连接失败（第 {attempt}/{PARAMIKO_CONNECT_RETRIES} 次）: {exc}，"
                    f"{delay:.0f}s 后重试 …",
                    file=sys.stderr,
                )
                time.sleep(delay)
    if client is None:
        assert last_exc is not None
        raise last_exc
    try:
        yield client
    finally:
        client.close()


@contextmanager
def _paramiko_session(c: Conn):
    with _paramiko_client(c) as client:
        sftp = client.open_sftp()
        try:
            yield client, sftp
        finally:
            sftp.close()


def _run_remote_bash_paramiko(c: Conn, script: str) -> None:
    with _paramiko_client(c) as client:
        _exec_remote_bash_on_client(client, script)


def _human_bytes(n: int) -> str:
    if n < 0:
        return "?"
    for unit, label in ((1 << 30, "GiB"), (1 << 20, "MiB"), (1 << 10, "KiB")):
        if n >= unit:
            return f"{n / unit:.2f} {label}"
    return f"{n} B"


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


def _remote_rel_path(remote_dir: str, remote_rel_path: str) -> str:
    rel = remote_rel_path.replace("\\", "/").lstrip("/")
    return f"{remote_dir.rstrip('/')}/{rel}"


def _collect_remote_dirs(remote_dir: str, rel_paths: list[str]) -> list[str]:
    dirs: set[str] = set()
    base = remote_dir.rstrip("/")
    dirs.add(base)
    for rel in rel_paths:
        parent = Path(rel).parent
        if str(parent) == ".":
            continue
        accumulated = base
        for part in parent.as_posix().split("/"):
            accumulated = f"{accumulated}/{part}"
            dirs.add(accumulated)
    return sorted(dirs)


def _batch_mkdir_script(remote_dir: str, rel_paths: list[str]) -> str:
    quoted_dirs = " ".join(
        f"'{_shell_single_quote(path)}'" for path in _collect_remote_dirs(remote_dir, rel_paths)
    )
    return f"set -euo pipefail\nmkdir -p {quoted_dirs}\n"


def _backup_file_script(remote_dir: str, remote_rel_path: str) -> str:
    esc_target = _shell_single_quote(_remote_rel_path(remote_dir, remote_rel_path))
    return f"""set -euo pipefail
TARGET='{esc_target}'
if [[ -f "$TARGET" ]]; then
  cp -a "$TARGET" "$TARGET.bak"
fi
"""


def remote_prepare_dir(
    c: Conn, remote_dir: str, remote_rel_path: str, *, backup: bool
) -> None:
    esc_target = _shell_single_quote(_remote_rel_path(remote_dir, remote_rel_path))
    esc_parent = _shell_single_quote(str(Path(remote_rel_path).parent).replace("\\", "/"))
    esc_dir = _shell_single_quote(remote_dir)
    backup_block = ""
    if backup:
        backup_block = """
if [[ -f "$TARGET" ]]; then
  cp -a "$TARGET" "$TARGET.bak"
fi"""
    script = f"""set -euo pipefail
mkdir -p '{esc_dir}'
if [[ '{esc_parent}' != '.' ]]; then
  mkdir -p '{esc_dir}/{esc_parent}'
fi
TARGET='{esc_target}'{backup_block}
"""
    run_remote_bash(c, script)


def _sftp_put_progress_cb():
    last_bucket = [-1]

    def cb(transferred: int, total: int) -> None:
        if total <= 0:
            return
        pct = min(100, int(100 * transferred / total))
        bucket = pct // 10
        if bucket > last_bucket[0] or transferred >= total:
            last_bucket[0] = bucket
            line = (
                f"\r  上传进度: {pct}%  "
                f"({_human_bytes(transferred)} / {_human_bytes(total)})"
            )
            print(line, end="", file=sys.stderr, flush=True)

    return cb


def _sftp_put_on_session(
    c: Conn,
    sftp: object,
    local_path: Path,
    remote_dir: str,
    remote_rel_path: str,
) -> None:
    remote_path = _remote_rel_path(remote_dir, remote_rel_path)
    total = local_path.stat().st_size
    print(
        f"正在通过 SFTP 上传 {remote_rel_path}（{_human_bytes(total)}）→ "
        f"{c.target.user}@{c.target.hostname}:{remote_path} …",
        file=sys.stderr,
    )
    cb = _sftp_put_progress_cb()
    sftp.put(str(local_path), remote_path, callback=cb)  # type: ignore[attr-defined]
    print(file=sys.stderr)


def _scp_upload_paramiko(
    c: Conn, local_path: Path, remote_dir: str, remote_rel_path: str
) -> None:
    with _paramiko_client(c) as client:
        sftp = client.open_sftp()
        try:
            _sftp_put_on_session(c, sftp, local_path, remote_dir, remote_rel_path)
        finally:
            sftp.close()


def _upload_file_paramiko(
    c: Conn,
    client: object,
    sftp: object,
    local_path: Path,
    remote_rel_path: str,
    *,
    backup: bool,
) -> None:
    if backup:
        _exec_remote_bash_on_client(
            client, _backup_file_script(c.remote_dir, remote_rel_path)
        )
    _sftp_put_on_session(c, sftp, local_path, c.remote_dir, remote_rel_path)
    remote_path = _remote_rel_path(c.remote_dir, remote_rel_path)
    print(f"已上传 {remote_rel_path} 至 {c.ssh_label}:{remote_path}")


def _deploy_dist_files_paramiko(c: Conn, files: list[tuple[Path, str]]) -> None:
    rel_paths = [rel for _, rel in files]
    print("正在建立 SSH 会话（单连接复用）…", file=sys.stderr)
    with _paramiko_session(c) as (client, sftp):
        _exec_remote_bash_on_client(client, _batch_mkdir_script(c.remote_dir, rel_paths))
        for local_path, rel in files:
            _upload_file_paramiko(
                c,
                client,
                sftp,
                local_path,
                rel,
                backup=c.settings.backup_before_upload,
            )


def _run_scp_subprocess_with_progress(
    cmd: list[str],
    env: dict[str, str],
    *,
    label: str,
    dest_display: str,
) -> None:
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


def scp_upload(c: Conn, local_path: Path, remote_dir: str, remote_rel_path: str) -> None:
    if _password_uses_paramiko(c):
        _scp_upload_paramiko(c, local_path, remote_dir, remote_rel_path)
        return
    cmd, host, env = scp_cmd(c)
    dest = f"{host}:{_remote_rel_path(remote_dir, remote_rel_path)}"
    full_cmd = cmd + [str(local_path), dest]
    size = local_path.stat().st_size
    print(
        f"正在通过 scp 上传 {remote_rel_path}（{_human_bytes(size)}）→ {dest} …",
        file=sys.stderr,
    )
    _run_scp_subprocess_with_progress(
        full_cmd,
        env,
        label=remote_rel_path,
        dest_display=dest,
    )


def _upload_file_to_remote(
    c: Conn,
    local_path: Path,
    remote_rel_path: str,
) -> None:
    remote_prepare_dir(
        c,
        c.remote_dir,
        remote_rel_path,
        backup=c.settings.backup_before_upload,
    )
    scp_upload(c, local_path, c.remote_dir, remote_rel_path)
    remote_path = _remote_rel_path(c.remote_dir, remote_rel_path)
    print(f"已上传 {remote_rel_path} 至 {c.ssh_label}:{remote_path}")


def _iter_dist_files(dist_dir: Path) -> list[tuple[Path, str]]:
    if not dist_dir.is_dir():
        return []
    files: list[tuple[Path, str]] = []
    for path in sorted(dist_dir.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(dist_dir).as_posix()
        files.append((path, rel))
    return files


def _create_dist_archive(dist_dir: Path, files: list[tuple[Path, str]]) -> Path:
    fd, raw_path = tempfile.mkstemp(suffix=".tar.gz", prefix="kiwi-admin-dist-")
    os.close(fd)
    archive_path = Path(raw_path)
    try:
        with tarfile.open(archive_path, "w:gz") as tar:
            for local_path, rel in files:
                tar.add(local_path, arcname=rel)
    except OSError:
        archive_path.unlink(missing_ok=True)
        raise
    return archive_path


def _extract_archive_remote_script(
    remote_dir: str, remote_archive: str, *, backup: bool
) -> str:
    esc_dir = _shell_single_quote(remote_dir)
    esc_archive = _shell_single_quote(remote_archive)
    backup_block = ""
    if backup:
        backup_block = f"""
if [[ -d '{esc_dir}' ]] && [[ -n "$(ls -A '{esc_dir}' 2>/dev/null || true)" ]]; then
  BAK='{esc_dir}.bak.'$(date +%Y%m%d%H%M%S)
  cp -a '{esc_dir}' "$BAK"
  echo "已备份至 $BAK"
fi"""
    return f"""set -euo pipefail
REMOTE_DIR='{esc_dir}'
ARCHIVE='{esc_archive}'
mkdir -p "$REMOTE_DIR"{backup_block}
shopt -s dotglob nullglob
entries=("$REMOTE_DIR"/*)
if (( ${{#entries[@]}} )); then
  rm -rf "${{entries[@]}}"
fi
tar xzf "$ARCHIVE" -C "$REMOTE_DIR"
rm -f "$ARCHIVE"
"""


def _deploy_dist_archive(c: Conn, settings: DeploySettings) -> bool:
    dist_dir = settings.dist_dir
    if not dist_dir.is_dir():
        print(f"本地构建目录不存在: {dist_dir}", file=sys.stderr)
        sys.exit(1)

    files = _iter_dist_files(dist_dir)
    if not files:
        print(f"构建目录为空: {dist_dir}", file=sys.stderr)
        sys.exit(1)

    file_count = len(files)
    archive_path = _create_dist_archive(dist_dir, files)
    remote_archive = f"/tmp/kiwi-admin-deploy-{int(time.time())}.tar.gz"
    try:
        archive_size = archive_path.stat().st_size
        print(
            f"共 {file_count} 个文件，已打包为 tar.gz（{_human_bytes(archive_size)}），"
            f"单次上传至 {c.remote_dir} …"
        )
        scp_upload(c, archive_path, "/tmp", Path(remote_archive).name)
        print("正在远端解压 …", file=sys.stderr)
        run_remote_bash(
            c,
            _extract_archive_remote_script(
                c.remote_dir,
                remote_archive,
                backup=settings.backup_before_upload,
            ),
        )
        print(f"同步完成：已解压 {file_count} 个文件至 {c.remote_dir}。")
    finally:
        archive_path.unlink(missing_ok=True)
    return True


def _deploy_dist_sftp(c: Conn, settings: DeploySettings) -> bool:
    dist_dir = settings.dist_dir
    if not dist_dir.is_dir():
        print(f"本地构建目录不存在: {dist_dir}", file=sys.stderr)
        sys.exit(1)

    files = _iter_dist_files(dist_dir)
    if not files:
        print(f"构建目录为空: {dist_dir}", file=sys.stderr)
        sys.exit(1)

    total = len(files)
    print(f"共 {total} 个文件待逐文件 SFTP 同步至 {c.remote_dir} …")

    if _password_uses_paramiko(c):
        _deploy_dist_files_paramiko(c, files)
    else:
        for local_path, rel in files:
            _upload_file_to_remote(c, local_path, rel)

    print(f"同步完成：共上传 {total} 个文件。")
    return True


def resolve_npm_executable(npm_path: str | None) -> str:
    if npm_path:
        o = npm_path.strip()
        p = Path(o)
        if p.is_file():
            return str(p.resolve())
        w = shutil.which(o)
        if w:
            return w
        _yaml_error(f"无法解析 deploy.npm: {npm_path}")

    for env_key in ("NPM", "NPM_CMD"):
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

    search = ("npm.cmd", "npm") if sys.platform == "win32" else ("npm", "npm.cmd")
    for name in search:
        w = shutil.which(name)
        if w:
            return w

    _yaml_error(
        "未找到 npm。请在 YAML deploy.npm 中指定，或确保 npm 在 PATH 中。"
    )


def run_npm_build(settings: DeploySettings) -> None:
    npm_exe = resolve_npm_executable(settings.npm)
    package_json = FRONTEND_ROOT / "package.json"
    if not package_json.is_file():
        print(f"未找到 package.json: {package_json}", file=sys.stderr)
        sys.exit(1)

    if settings.build_script == "ng":
        cmd = [
            npm_exe,
            "exec",
            "--",
            "ng",
            "build",
            f"--base-href={settings.base_href}",
            f"--configuration={settings.build_configuration}",
        ]
        print(
            f"执行 ng build（base-href={settings.base_href}, "
            f"configuration={settings.build_configuration}）…"
        )
    elif settings.build_script == "npm":
        cmd = [npm_exe, "run", settings.npm_script]
        print(f"执行 npm run {settings.npm_script} …")
    else:
        _yaml_error("deploy.build_script 必须是 ng 或 npm。")

    try:
        subprocess.run(cmd, cwd=str(FRONTEND_ROOT), check=True)
    except subprocess.CalledProcessError as exc:
        print(
            f"\n前端构建失败（退出码 {exc.returncode}）。"
            "请查看上方 npm/ng 编译错误。",
            file=sys.stderr,
        )
        sys.exit(exc.returncode if exc.returncode else 1)


def deploy_dist_files(c: Conn, settings: DeploySettings) -> bool:
    if settings.sync_mode == "archive":
        return _deploy_dist_archive(c, settings)
    return _deploy_dist_sftp(c, settings)


def _build_ssh_target_from_raw(raw: dict) -> SshTarget:
    hostname = _require_str(raw, "ssh", "hostname")
    user = _require_str(raw, "ssh", "user")
    port = _optional_int(raw, "ssh", "port", DEFAULT_SSH_PORT)
    auth = _optional_str_with_default(raw, "ssh", "auth", DEFAULT_SSH_AUTH).lower()
    if auth not in ("key", "password"):
        _yaml_error("ssh.auth 必须是 key 或 password。")

    strict_host_key_checking = _optional_str_with_default(
        raw, "ssh", "strict_host_key_checking", DEFAULT_STRICT_HOST_KEY
    )
    label = _optional_str(raw, "ssh", "host") or f"{user}@{hostname}"

    identity_file = _optional_str(raw, "ssh", "identity_file")
    password = _optional_str(raw, "ssh", "password")
    password_env = _optional_str(raw, "ssh", "password_env")

    if auth == "key":
        password = None
        password_env = None
    else:
        identity_file = None
        if not password and not password_env:
            _yaml_error("auth: password 时需配置 ssh.password 或 ssh.password_env。")

    return SshTarget(
        label=label,
        hostname=hostname,
        user=user,
        port=port,
        auth=auth,
        identity_file=identity_file,
        password=password,
        password_env=password_env,
        strict_host_key_checking=strict_host_key_checking,
    )


def _build_deploy_settings_from_raw(raw: dict) -> DeploySettings:
    backup_before_upload = _optional_bool(raw, "deploy", "backup_before_upload", True)

    dist_raw = _optional_str(raw, "deploy", "dist_dir")
    dist_dir = _resolve_frontend_path(dist_raw) if dist_raw else DEFAULT_DIST_DIR.resolve()

    remote_dir = _optional_str_with_default(raw, "deploy", "remote_dir", DEFAULT_REMOTE_DIR)
    npm = _optional_str(raw, "deploy", "npm")
    skip_build = _optional_bool(raw, "deploy", "skip_build", False)
    build_script = _optional_str_with_default(
        raw, "deploy", "build_script", DEFAULT_BUILD_SCRIPT
    ).lower()
    npm_script = _optional_str_with_default(raw, "deploy", "npm_script", DEFAULT_NPM_SCRIPT)
    build_configuration = _optional_str_with_default(
        raw, "deploy", "build_configuration", DEFAULT_BUILD_CONFIGURATION
    )
    base_href = _optional_str_with_default(raw, "deploy", "base_href", DEFAULT_BASE_HREF)
    sync_mode = _optional_str_with_default(
        raw, "deploy", "sync_mode", DEFAULT_SYNC_MODE
    ).lower()

    if build_script not in ("ng", "npm"):
        _yaml_error("deploy.build_script 必须是 ng 或 npm。")
    if sync_mode not in VALID_SYNC_MODES:
        _yaml_error("deploy.sync_mode 必须是 archive 或 sftp。")

    return DeploySettings(
        dist_dir=dist_dir,
        remote_dir=remote_dir,
        backup_before_upload=backup_before_upload,
        npm=npm,
        skip_build=skip_build,
        build_script=build_script,
        npm_script=npm_script,
        build_configuration=build_configuration,
        base_href=base_href,
        sync_mode=sync_mode,
    )


def load_deploy_config(path: Path) -> tuple[SshTarget, DeploySettings]:
    raw = yaml.safe_load(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        _yaml_error("YAML 根节点必须是映射。")
    if "ssh" not in raw or not isinstance(raw["ssh"], dict):
        _yaml_error("YAML 缺少 ssh 块。")
    deploy = raw.get("deploy")
    if deploy is None:
        deploy = {}
    if not isinstance(deploy, dict):
        _yaml_error("YAML deploy 块必须是映射。")
    raw = {**raw, "deploy": deploy}
    return _build_ssh_target_from_raw(raw), _build_deploy_settings_from_raw(raw)


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


def cmd_deploy(c: Conn) -> bool:
    settings = c.settings
    if not settings.skip_build:
        run_npm_build(settings)
    else:
        print("skip_build: true，跳过构建。")

    return deploy_dist_files(c, settings)


def _resolve_config_path(raw: str) -> Path:
    p = Path(raw.strip())
    if not p.is_absolute():
        p = SCRIPT_DIR / p
    return p.resolve()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="构建 kiwi-admin 前端并上传至远程 SFTP/SSH 主机。",
    )
    parser.add_argument(
        "config",
        nargs="?",
        default=str(DEFAULT_CONFIG_PATH),
        help=f"配置文件路径（默认 {DEFAULT_CONFIG_PATH.relative_to(SCRIPT_DIR)}）",
    )
    return parser.parse_args()


def main() -> None:
    _ensure_utf8_stdio()
    args = parse_args()
    cfg_path = _resolve_config_path(args.config)
    if not cfg_path.is_file():
        example = SCRIPT_DIR / "conf" / "build.example.yaml"
        print(
            f"配置文件不存在: {cfg_path}\n"
            f"请复制 {example} 为 conf/build.local.yaml，并填写 ssh.hostname、ssh.user。",
            file=sys.stderr,
        )
        sys.exit(1)
    target, settings = load_deploy_config(cfg_path)
    c = _conn_from_config(target, settings)
    _ensure_password_backend(c)
    cmd_deploy(c)


if __name__ == "__main__":
    main()
