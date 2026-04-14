#!/usr/bin/env python3
"""
远程部署 / 启停 / JDWP 调试（经 SSH，调用本机 OpenSSH 客户端与 Maven）。

连接信息来自单主机 YAML（--config），见 script/ssh/remote.example.yaml。
auth: password 时：优先使用 sshpass + 系统 ssh/scp；若无 sshpass 则使用 paramiko（见 requirements-remote.txt）。
stdin 用于远程脚本，不支持交互式键盘输密。

依赖：pip install -r requirements-remote.txt
"""
from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import subprocess
import sys
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path

try:
    import yaml
except ImportError:
    print(
        "缺少 PyYAML，请执行: pip install -r requirements-remote.txt",
        file=sys.stderr,
    )
    sys.exit(1)

SCRIPT_DIR = Path(__file__).resolve().parent
KIWI_ADMIN_ROOT = SCRIPT_DIR.parent
BACKEND_ROOT = KIWI_ADMIN_ROOT / "backend"
BACKEND_POM = BACKEND_ROOT / "pom.xml"
BACKEND_SRC_MAIN = BACKEND_ROOT / "src" / "main"
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
    jdwp_port: str
    jdwp_bind: str
    local_debug_port: str


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
    """password 且无 sshpass 时用 paramiko（不占用 stdin 传脚本）。"""
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
            "或执行 pip install paramiko（已列入 requirements-remote.txt）。",
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


def _scp_upload_paramiko(c: Conn, local_jar: Path, remote_dir: str, remote_jar_name: str) -> None:
    remote_path = f"{remote_dir.rstrip('/')}/{remote_jar_name}"
    with _paramiko_client(c) as client:
        sftp = client.open_sftp()
        try:
            sftp.put(str(local_jar), remote_path)
        finally:
            sftp.close()


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
    仅在需要执行 package 时调用，避免 stop/start 等子命令依赖 mvn。
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
    exe = resolve_mvn_executable(mvn_override)
    subprocess.run(
        [exe, "-f", str(BACKEND_POM), "package", "-DskipTests"],
        cwd=str(KIWI_ADMIN_ROOT),
        check=True,
    )


def _local_file_sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def _backend_inputs_newest_mtime() -> float:
    mt = BACKEND_POM.stat().st_mtime
    if BACKEND_SRC_MAIN.is_dir():
        for p in BACKEND_SRC_MAIN.rglob("*"):
            if p.is_file():
                mt = max(mt, p.stat().st_mtime)
    return mt


def should_skip_mvn(local_jar: Path) -> bool:
    if not local_jar.is_file():
        return False
    return local_jar.stat().st_mtime >= _backend_inputs_newest_mtime()


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


def remote_stop(c: Conn, remote_dir: str, remote_jar_name: str) -> None:
    script = f"""set -euo pipefail
REMOTE_DIR='{remote_dir}'
REMOTE_JAR_NAME='{remote_jar_name}'
JAR="$REMOTE_DIR/$REMOTE_JAR_NAME"
PID_FILE="$REMOTE_DIR/app.pid"
if [[ -f "$PID_FILE" ]]; then
  pid=$(cat "$PID_FILE" || true)
  if [[ -n "${{pid:-}}" ]] && kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null || true
  fi
  rm -f "$PID_FILE"
fi
while read -r pid; do
  [[ -n "$pid" ]] || continue
  kill "$pid" 2>/dev/null || true
done < <(pgrep -f "$JAR" 2>/dev/null || true)
"""
    run_remote_bash(c, script)


def remote_start(
    c: Conn,
    remote_dir: str,
    remote_jar_name: str,
    debug: bool,
) -> None:
    dbg = "1" if debug else "0"
    script = f"""set -euo pipefail
cd '{remote_dir}'
JAR='{remote_dir}/{remote_jar_name}'
JDWP_PORT='{c.jdwp_port}'
JDWP_BIND='{c.jdwp_bind}'
DEBUG='{dbg}'
if [[ ! -f "$JAR" ]]; then
  echo "Remote JAR not found: $JAR" >&2
  exit 1
fi
if [[ "$DEBUG" == "1" ]]; then
  nohup java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=${{JDWP_BIND}}:${{JDWP_PORT}} -jar "$JAR" >> app.log 2>&1 & echo $! > app.pid
else
  nohup java -jar "$JAR" >> app.log 2>&1 & echo $! > app.pid
fi
echo "Started PID $(cat app.pid) (logs: {remote_dir}/app.log)"
"""
    run_remote_bash(c, script)


def scp_upload(c: Conn, local_jar: Path, remote_dir: str, remote_jar_name: str) -> None:
    if _password_uses_paramiko(c):
        _scp_upload_paramiko(c, local_jar, remote_dir, remote_jar_name)
        return
    cmd, host, env = scp_cmd(c)
    dest = f"{host}:{remote_dir}/{remote_jar_name}"
    subprocess.run(cmd + [str(local_jar), dest], check=True, env=env)


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
            print("增量：本地 JAR 不早于 pom / 源码树修改时间，跳过 mvn package。")
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


def load_ssh_target(
    path: Path,
    *,
    override_hostname: str | None,
    override_user: str | None,
    override_port: int | None,
) -> SshTarget:
    raw = yaml.safe_load(path.read_text(encoding="utf-8"))
    if not isinstance(raw, dict):
        print("YAML 根节点必须是映射（单主机一份配置）。", file=sys.stderr)
        sys.exit(1)

    hostname = override_hostname or raw.get("hostname")
    user = override_user or raw.get("user")
    port = override_port if override_port is not None else raw.get("port", 22)
    auth = (raw.get("auth") or "key").strip().lower()
    if auth not in ("key", "password"):
        print("auth 必须是 key 或 password。", file=sys.stderr)
        sys.exit(1)
    if not hostname or not user:
        print("YAML 中 hostname、user 为必填（或通过命令行覆盖）。", file=sys.stderr)
        sys.exit(1)
    if not isinstance(port, int):
        port = int(port)

    label = str(raw.get("host") or f"{user}@{hostname}")
    identity = raw.get("identity_file")
    password_raw = raw.get("password")
    password = str(password_raw) if password_raw is not None else None
    password_env = raw.get("password_env")
    strict_ = str(raw.get("strict_host_key_checking") or "accept-new")

    if auth == "key":
        pass
    else:
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


def add_connection_args(p: argparse.ArgumentParser) -> None:
    p.add_argument(
        "--config",
        type=Path,
        required=True,
        metavar="PATH",
        help="单主机连接 YAML（见 script/ssh/remote.example.yaml）",
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
        "--jdwp-port",
        default="5005",
        help="JDWP 端口（默认：5005）",
    )
    p.add_argument(
        "--jdwp-bind",
        default="127.0.0.1",
        help="JDWP 绑定地址（默认：127.0.0.1）",
    )
    p.add_argument(
        "--local-debug-port",
        default="5005",
        help="文档/提示用：本机 SSH -L 转发时建议的本地端口（默认：5005）",
    )
    p.add_argument(
        "--mvn",
        default=None,
        metavar="PATH",
        help="Maven 可执行文件（默认：从 PATH 查找 mvn / mvn.cmd，或 MAVEN_HOME、环境变量 MVN）",
    )


def build_parser() -> argparse.ArgumentParser:
    ex_yaml = SCRIPT_DIR / "ssh" / "remote.example.yaml"
    p = argparse.ArgumentParser(
        description="Kiwi-admin 远程部署、启停与 JDWP 调试（SSH + scp + mvn）。",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=f"""
增量更新（默认开启）：
  · 未指定 --skip-build 时：若本地 JAR 已存在且不早于 backend/pom.xml 与 backend/src/main 下
    最新修改时间，则跳过 mvn package。
  · 上传前：比较本地与远端 JAR 的 SHA256；一致则跳过备份与 scp。

全量：使用 --no-incremental 将始终执行 mvn（在未 --skip-build 时）并始终上传。

密码认证：YAML 中 auth: password，并配置 password 或 password_env / KIWI_SSH_PASSWORD。
  优先使用 sshpass + 系统 ssh/scp；若无 sshpass 则使用 paramiko（见 requirements-remote.txt）。
  stdin 用于远程脚本，不支持交互式键盘输密。

本地端口转发示例（IDE 连接 localhost 的 LOCAL_DEBUG_PORT）：
  ssh -p <port> -L <local_debug_port>:127.0.0.1:<jdwp_port> <user>@<hostname> -N

配置示例：{ex_yaml}
""".strip(),
    )
    parent = argparse.ArgumentParser(add_help=False)
    add_connection_args(parent)
    parent.add_argument(
        "--no-incremental",
        action="store_true",
        help="关闭增量：始终 mvn（除非 --skip-build）并始终上传",
    )
    sub = p.add_subparsers(dest="command", required=True)

    def add_deploy_flags(sp: argparse.ArgumentParser) -> None:
        sp.add_argument(
            "--skip-build",
            action="store_true",
            help="跳过 mvn package",
        )

    deploy_p = sub.add_parser(
        "deploy",
        parents=[parent],
        help="构建（可选）并上传 JAR",
    )
    add_deploy_flags(deploy_p)

    sub.add_parser(
        "stop",
        parents=[parent],
        help="停止远端进程（app.pid + pgrep -f JAR）",
    )

    start_p = sub.add_parser(
        "start",
        parents=[parent],
        help="启动远端 JAR（nohup，app.log / app.pid）",
    )
    start_p.add_argument(
        "--debug",
        action="store_true",
        help="附加 JDWP 后启动",
    )

    restart_p = sub.add_parser(
        "restart",
        parents=[parent],
        help="stop 再 start",
    )
    restart_p.add_argument("--debug", action="store_true", help="启动时附加 JDWP")

    dd_p = sub.add_parser(
        "debug-deploy",
        parents=[parent],
        help="deploy 后以 JDWP 启动",
    )
    add_deploy_flags(dd_p)

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
    target = load_ssh_target(
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
        jdwp_port=ns.jdwp_port,
        jdwp_bind=ns.jdwp_bind,
        local_debug_port=ns.local_debug_port,
    )


def main(argv: list[str] | None = None) -> None:
    _ensure_utf8_stdio()
    argv = argv if argv is not None else sys.argv[1:]
    parser = build_parser()
    args = parser.parse_args(argv)
    c = _conn_from_ns(args)
    _ensure_password_backend(c)
    incremental = not getattr(args, "no_incremental", False)
    skip_build = getattr(args, "skip_build", False)
    debug = getattr(args, "debug", False)

    mvn_override: str | None = getattr(args, "mvn", None)

    if args.command == "deploy":
        cmd_deploy(
            c,
            skip_build=skip_build,
            incremental=incremental,
            mvn_override=mvn_override,
        )
        return
    if args.command == "stop":
        remote_stop(c, c.remote_dir, c.remote_jar_name)
        return
    if args.command == "start":
        remote_start(c, c.remote_dir, c.remote_jar_name, debug=debug)
        return
    if args.command == "restart":
        remote_stop(c, c.remote_dir, c.remote_jar_name)
        remote_start(c, c.remote_dir, c.remote_jar_name, debug=debug)
        return
    if args.command == "debug-deploy":
        cmd_deploy(
            c,
            skip_build=skip_build,
            incremental=incremental,
            mvn_override=mvn_override,
        )
        remote_stop(c, c.remote_dir, c.remote_jar_name)
        remote_start(c, c.remote_dir, c.remote_jar_name, debug=True)
        return

    raise AssertionError(f"unhandled command: {args.command}")


if __name__ == "__main__":
    main()
