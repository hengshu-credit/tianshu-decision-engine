#!/usr/bin/env python3
"""发布包的 Linux 应用部署入口；只使用 Python 3 标准库。"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile


APP_SERVICES = ('server', 'web', 'runtime-sdk', 'sdk')
ARTIFACTS = {'server': ('server.jar', '/app/app.jar'),
             'web': ('dist', '/usr/share/nginx/html'),
             'runtime-sdk': ('runtime.jar', '/app/app.jar'),
             'sdk': ('runtime.jar', '/app/app.jar')}


class DeployError(Exception):
    pass


def validate_layout(release, target):
    if target == Path(target.anchor) or target == Path.home().resolve():
        raise DeployError('部署目标不能是文件系统根目录或用户主目录；请使用 /root/tianshu-decision-engine。')
    if release != target and release.parent != target / 'releases':
        raise DeployError('新包必须解压到部署目录下独立的 releases/ 子目录，再指定该部署目录为 --target。')


def run(command, cwd=None, check=True, env=None):
    result = subprocess.run(command, cwd=cwd, env=env, text=True, encoding='utf-8',
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=1800)
    if check and result.returncode:
        # Compose config / inspect 中有凭据，不把完整输出写入终端或日志。
        raise DeployError('命令失败，请检查原配置及服务日志：' + ' '.join(map(str, command)))
    return result


def detect_compose():
    for command in (['docker', 'compose'], ['docker-compose']):
        try:
            result = run(command + ['version', '--short'], check=False)
        except FileNotFoundError:
            continue
        version = re.search(r'v?(\d+)\.', result.stdout)
        if result.returncode == 0 and version and int(version.group(1)) >= 2:
            return command
    raise DeployError('需要现代 Compose v2+：docker compose 或 docker-compose（不支持 Python v1）。')


def digest(file):
    value = hashlib.sha256()
    with file.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            value.update(chunk)
    return value.hexdigest()


def verify_release(release):
    manifest = release / 'SHA256SUMS.txt'
    if not manifest.is_file():
        raise DeployError('缺少 SHA256SUMS.txt，请使用完整的新发布包。')
    verified = set()
    for line in manifest.read_text(encoding='utf-8').splitlines():
        match = re.fullmatch(r'([0-9a-fA-F]{64})  (.+)', line)
        if not match:
            raise DeployError('发布包校验清单格式错误。')
        relative = Path(match[2])
        file = release / relative
        if relative.is_absolute() or '..' in relative.parts or file.resolve() != file.absolute():
            raise DeployError('发布包清单包含越界路径或符号链接。')
        if match[2] in ('.env.server', '.env.client'):
            # 显式附带配置的私有包首次安装后，允许部署方编辑这两份配置。
            continue
        if not file.is_file() or digest(file) != match[1].lower():
            raise DeployError('发布包文件缺失或校验失败：' + match[2])
        verified.add(match[2])
    for name in ('deploy.py', 'docker/runtime/server.jar', 'docker/runtime/runtime.jar',
                 'docker/runtime/dist/index.html', 'BUILD-INFO.txt'):
        if name not in verified:
            raise DeployError('发布包清单缺少：' + name)
    # 不复制清单之外的前端文件，防止重复解压留下旧资源。
    frontend = list((release / 'docker/runtime/dist').rglob('*'))
    if any(file.is_symlink() for file in frontend):
        raise DeployError('dist 不允许符号链接。')
    actual = {file.relative_to(release).as_posix() for file in frontend if file.is_file()}
    if not actual.issubset(verified):
        raise DeployError('dist 存在未校验的文件，请解压到一个新的空目录。')


def compose_plan(command, target, files, project=None, containers=(), sdk=False):
    names = [file.name for file in files]
    if any(name in ('docker-compose.full.yml', 'docker-compose.yml') for name in names):
        envs = ['.env.client', '.env.server']
    elif 'docker-compose.client.yml' in names:
        envs = ['.env.client']
    else:
        envs = ['.env.server']
    args = list(command)
    if project:
        args += ['--project-name', project]
    for name in envs:
        if not (target / name).is_file():
            raise DeployError('首次部署请先填写：' + str(target / name))
        args += ['--env-file', str(target / name)]
    for file in files:
        if not file.is_file():
            raise DeployError('原 Compose 文件不存在：' + str(file))
        args += ['-f', str(file)]
    config = json.loads(run(args + ['config', '--format', 'json'], cwd=target).stdout)
    counts = {}
    for item in containers:
        name = item['Config']['Labels']['com.docker.compose.service']
        counts[name] = counts.get(name, 0) + 1
    services = [name for name in APP_SERVICES if name in config['services']
                and (name in ('server', 'web') or name in counts or sdk or envs == ['.env.client'])]
    if not services:
        raise DeployError('没有找到可部署的应用服务。')
    # 固定解析后的项目名，保留通过 -p 创建的现有项目和命名卷。
    if not project:
        args[len(command):len(command)] = ['--project-name', config['name']]
    return {'args': args, 'config': config, 'services': services, 'counts': counts}


def discover_plans(command, target, sdk=False, client_only=False):
    ids = run(['docker', 'ps', '-aq', '--filter', 'label=com.docker.compose.project']).stdout.split()
    labels_output = run(['docker', 'inspect', '--format', '{{json .Config.Labels}}'] + ids).stdout if ids else ''
    containers = [{'Config': {'Labels': json.loads(line)}} for line in labels_output.splitlines() if line.strip()]
    groups = {}
    for item in containers:
        labels = item.get('Config', {}).get('Labels') or {}
        if labels.get('com.docker.compose.service') not in APP_SERVICES:
            continue
        if labels.get('com.docker.compose.oneoff', '').lower() == 'true':
            continue
        files = tuple(Path(p).resolve() for p in labels.get('com.docker.compose.project.config_files', '').split(',') if p)
        if not any(file.parent == target / 'docker' for file in files):
            continue
        if client_only and labels['com.docker.compose.service'] in ('server', 'web'):
            continue
        groups.setdefault((labels['com.docker.compose.project'], files), []).append(item)
    plans = [compose_plan(command, target, list(files), project, items, sdk)
             for (project, files), items in groups.items()]
    if client_only:
        for plan in plans:
            plan['services'] = [s for s in plan['services'] if s in ('sdk', 'runtime-sdk')]
    if not plans:
        kind = 'client' if client_only else 'server'
        plans.append(compose_plan(command, target, [target / f'docker/docker-compose.{kind}.yml']))
    if sdk and not any(any(s in p['services'] for s in ('sdk', 'runtime-sdk')) for p in plans):
        plans.append(compose_plan(command, target, [target / 'docker/docker-compose.client.yml']))
    seen = set()
    for plan in plans:
        for service in plan['services']:
            if service in seen:
                raise DeployError('同一目录发现多套应用部署，请先明确并分开安装目录。')
            seen.add(service)
    return sorted(plans, key=lambda p: 'server' not in p['services'])


def prepare_artifacts(release, target, plans, backup):
    artifacts = {}
    for plan in plans:
        for service in plan['services']:
            name, mount = ARTIFACTS[service]
            volumes = plan['config']['services'][service].get('volumes', [])
            matches = [v for v in volumes if v.get('target') == mount and v.get('type') == 'bind']
            if len(matches) != 1:
                raise DeployError('应用必须使用明确的制品挂载：' + service)
            destination = Path(matches[0]['source']).absolute()
            if destination.resolve() != destination or (name == 'dist' and destination.name != 'dist'):
                raise DeployError('请使用真实制品路径，前端目录名必须为 dist：' + str(destination))
            source = release / 'docker/runtime' / name
            if destination == source:
                if plan['counts']:
                    raise DeployError('已有部署不能直接解压覆盖运行目录，请把新包解压到独立目录。')
                continue
            if destination in artifacts:
                continue
            destination.parent.mkdir(parents=True, exist_ok=True)
            stage = Path(tempfile.mkdtemp(prefix='.' + name + '-update-', dir=destination.parent))
            item = {'destination': destination, 'stage': stage, 'installed': False}
            artifacts[destination] = item
            try:
                copy = shutil.copytree if source.is_dir() else shutil.copy2
                copy(source, stage / 'new')
                if destination.exists():
                    copy(destination, backup / (str(len(artifacts)) + '-' + name))
            except BaseException:
                for entry in artifacts.values():
                    shutil.rmtree(entry['stage'])
                raise
    return list(artifacts.values())


def stop_apps(plans, target):
    for plan in reversed(plans):
        run(plan['args'] + ['stop'] + list(reversed(plan['services'])), cwd=target)


def start_apps(plans, target):
    for plan in plans:
        # 分步等 server 就绪，再启动 web / SDK；--no-deps 保证不操作共享基础设施。
        for service in plan['services']:
            args = ['up', '-d', '--no-deps', '--no-build', '--force-recreate', '--wait', '--wait-timeout', '300']
            if service in plan['counts']:
                args += ['--scale', service + '=' + str(plan['counts'][service])]
            run(plan['args'] + args + [service], cwd=target)


def deploy(release, target, command, sdk=False, client_only=False, migration=None):
    validate_layout(release, target)
    plans = discover_plans(command, target, sdk, client_only)
    backup_root = target / 'backups'
    backup_root.mkdir(exist_ok=True)
    backup = Path(tempfile.mkdtemp(prefix='update-', dir=backup_root))
    backup.chmod(0o700)
    artifacts = prepare_artifacts(release, target, plans, backup)
    shutil.copy2(release / 'BUILD-INFO.txt', backup / 'new-BUILD-INFO.txt')
    print('[2/5] 配置与新制品检查完成；备份：' + str(backup), flush=True)
    try:
        stop_apps(plans, target)
        print('[3/5] 旧应用已停止，共享 MySQL / Redis / Kafka 保持运行。', flush=True)
        if migration:
            environment = dict(os.environ, TIANSHU_RELEASE_DIR=str(release),
                               TIANSHU_DEPLOY_DIR=str(target), TIANSHU_BACKUP_DIR=str(backup))
            run(['bash', str(migration)], cwd=target, env=environment)
        for item in artifacts:
            dest, stage = item['destination'], item['stage']
            if dest.exists():
                dest.rename(stage / 'old')
            try:
                (stage / 'new').rename(dest)
            except BaseException:
                if (stage / 'old').exists():
                    (stage / 'old').rename(dest)
                raise
            item['installed'] = True
        print('[4/5] 应用制品已替换，正在重启并等待健康检查。', flush=True)
        start_apps(plans, target)
        print('[5/5] 部署完成，应用均已就绪。原 env、配置和共享数据保留。', flush=True)
    except BaseException:
        # 启动阶段可能执行数据库 DDL，不自动启动旧版或声称数据库已回退。
        try:
            stop_apps(plans, target)
        except Exception:
            print('停止应用失败，请检查容器状态后使用备份恢复。', file=sys.stderr)
            raise
        for item in reversed(artifacts):
            if item['installed']:
                item['destination'].rename(item['stage'] / 'failed')
                if (item['stage'] / 'old').exists():
                    (item['stage'] / 'old').rename(item['destination'])
        print('部署失败；原制品已保留/恢复，应用保持停止。请检查迁移与数据库兼容性后重新部署。', file=sys.stderr)
        raise
    finally:
        for item in artifacts:
            # 只清理本次创建的暂存目录；回退异常时保留 old 供人工恢复。
            if not (item['stage'] / 'old').exists() or sys.exc_info()[0] is None:
                shutil.rmtree(item['stage'])


def main():
    parser = argparse.ArgumentParser(description='校验、备份、更新 JAR/dist 并重启应用；保留共享基础设施。')
    parser.add_argument('--target', type=Path, default=Path.cwd(), help='固定安装目录，默认当前目录')
    parser.add_argument('--sdk', action='store_true', help='首次同时启动 SDK；已有 SDK 自动更新')
    parser.add_argument('--client-only', action='store_true', help='仅部署独立 SDK 机器')
    parser.add_argument('--migration-script', type=Path, help='在停应用后、替换前执行指定 Bash 迁移脚本；脚本须先完成数据库备份')
    options = parser.parse_args()
    release = Path(__file__).resolve().parent
    target = options.target.resolve()
    validate_layout(release, target)
    verify_release(release)
    command = detect_compose()
    print('[1/5] 发布包校验通过；使用 ' + ' '.join(command), flush=True)
    if not target.is_dir():
        raise DeployError('首次部署请先将包解压到安装目录并填写 env。')
    migration = options.migration_script.resolve() if options.migration_script else None
    if migration and not migration.is_file():
        raise DeployError('迁移脚本不存在：' + str(migration))
    if (release / 'migrations/upgrade.sh').exists() and migration is None:
        raise DeployError('本版包含迁移入口，请检查后添加 --migration-script ' + str(release / 'migrations/upgrade.sh'))
    lock = target / '.deploy.lock'
    try:
        lock.mkdir()
    except FileExistsError:
        raise DeployError('已有部署锁 .deploy.lock；请确认没有其他更新运行。')
    try:
        deploy(release, target, command, options.sdk, options.client_only, migration)
    finally:
        lock.rmdir()


if __name__ == '__main__':
    try:
        main()
    except (DeployError, OSError, ValueError, subprocess.TimeoutExpired) as error:
        print('错误：' + str(error), file=sys.stderr)
        sys.exit(1)
