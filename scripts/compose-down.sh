#!/usr/bin/env bash
# 在天枢安装目录运行；只操作归属本安装的 Compose 项目。
set -euo pipefail
exec python3 - "$@" <<'PY'
import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys

APP = {'server', 'web', 'sdk', 'runtime-sdk'}
BASE_FILES = {'docker-compose.yml', 'docker-compose.full.yml', 'docker-compose.server.yml',
              'docker-compose.client.yml', 'docker-compose.mysql.yml', 'docker-compose.redis.yml',
              'docker-compose.kafka.yml'}


def output(args):
    return subprocess.check_output(args, text=True)


def detect_compose():
    # 独立交付的停机脚本也要在传项目参数前探测入口，与 deploy.py 的版本要求一致。
    for command in (['docker', 'compose'], ['docker-compose']):
        try:
            probe = subprocess.run([*command, 'version', '--short'], capture_output=True, text=True)
        except FileNotFoundError:
            continue
        version = re.match(r'v?(\d+)\.', probe.stdout.strip())
        if probe.returncode == 0 and version and int(version.group(1)) >= 2:
            return command
    raise ValueError('找不到可用的 Compose v2+。请检查 docker compose version 或 docker-compose version。')


def commands(root, all_services):
    layout = (root / '.deployment-layout.json').is_file()
    if not layout and (root / 'config').is_dir():
        raise ValueError('缺少 .deployment-layout.json，请先核对生效配置目录。')
    installs = [root]
    if not layout and (root / 'deploy-prod/docker').is_dir():
        installs.append(root / 'deploy-prod')
    bases = {install / 'docker' / name: install for install in installs for name in BASE_FILES}
    if not any(file.is_file() for file in bases):
        raise ValueError('请在包含天枢 docker/ 部署定义的安装目录运行。')

    ids = output(['docker', 'ps', '-aq', '--filter', 'label=com.docker.compose.project']).split()
    if not ids:
        return []
    # 仅读 Compose 标签。沿用创建容器时的项目名、配置文件顺序及 override。
    labels = output(['docker', 'inspect', '--format', '{{json .Config.Labels}}', *ids])
    groups = {}
    outside = set()
    owners = {}
    for line in labels.splitlines():
        item = json.loads(line)
        prefix = 'com.docker.compose.'
        project = item[prefix + 'project']
        files = tuple(Path(s).resolve() for s in item.get(prefix + 'project.config_files', '').split(',') if s)
        if not files or files[0] not in bases:
            outside.add(project)
            continue
        if item.get(prefix + 'oneoff', '').lower() == 'true':
            continue
        service = item[prefix + 'service']
        if not all_services and service not in APP:
            continue
        recorded = item.get(prefix + 'project.environment_file', '')
        envs = tuple(Path(s).resolve() for s in recorded.split(',') if s)
        if not envs:
            directory = root / 'config' if layout else bases[files[0]]
            names = ['.env.client'] if files[0].name == 'docker-compose.client.yml' else ['.env.server']
            if files[0].name in ('docker-compose.yml', 'docker-compose.full.yml'):
                names = ['.env.client', '.env.server']
            envs = tuple(directory / name for name in names)
        directory = item.get(prefix + 'project.working_dir') or str(files[0].parent)
        group = project, directory, files, envs
        if owners.setdefault((project, service), group) != group:
            raise ValueError(f'{project}/{service} 有多套启动配置，请先核对容器归属。')
        groups.setdefault(group, set()).add(service)

    if not groups:
        return []
    compose = detect_compose()
    down_help = output([*compose, 'down', '--help'])
    selective_down = re.search(r'\[SERVICES?(?:\.\.\.)?\]', down_help, re.IGNORECASE)
    result = []
    for (project, directory, files, envs), services in sorted(groups.items(), key=lambda pair: not bool(pair[1] & APP)):
        if project in outside:
            raise ValueError(f'项目名 {project} 同时被其他部署定义使用，请先核对归属。')
        args = [*compose, '--project-name', project, '--project-directory', directory]
        for flag, paths in [('--env-file', envs), ('-f', files)]:
            for path in paths:
                if not path.is_file():
                    raise ValueError(f'缺少原部署文件：{path}')
                args.extend([flag, str(path)])
        args.extend(['--profile', '*'])
        defined = set(output([*args, 'config', '--services']).splitlines())
        if services - defined:
            raise ValueError(f'{project} 的容器和当前 Compose 服务定义不一致。')
        selected = sorted(services)
        if selective_down:
            result.append([*args, 'down', '--remove-orphans=false', *selected])
        else:
            # 早期 v2 的 down 只能关闭整组，逐服务停止并移除，保留共享设施和网络。
            result.append([*args, 'stop', *selected])
            result.append([*args, 'rm', '--force', *selected])
    return result


def main():
    parser = argparse.ArgumentParser(prog='compose-down.sh', description='停止并移除本安装的应用容器，保留数据卷。')
    parser.add_argument('--all', action='store_true', help='同时关闭本安装 Compose 管理的基础设施及其他服务')
    parser.add_argument('--dry-run', action='store_true', help='仅显示将执行的命令')
    options = parser.parse_args()
    root = Path.cwd().resolve()
    lock = root / '.deploy.lock'
    if not options.dry_run:
        try:
            lock.mkdir()
        except FileExistsError:
            raise ValueError('安装目录已有部署锁，请先核对正在执行的操作。') from None
    try:
        planned = commands(root, options.all)
        if not planned:
            print('本安装没有匹配的容器。')
        for args in planned:
            print(' '.join(shlex.quote(str(arg)) for arg in args), flush=True)
            if not options.dry_run:
                subprocess.run(args, check=True)
    finally:
        if not options.dry_run:
            lock.rmdir()


try:
    main()
except (OSError, ValueError, subprocess.CalledProcessError) as error:
    sys.exit(str(error))
PY
