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
SERVICE_ORDER = ('mysql', 'redis', 'kafka') + APP_SERVICES
LAYOUT_FILE = '.deployment-layout.json'
ARTIFACTS = {'server': ('server.jar', '/app/app.jar'),
             'web': ('dist', '/usr/share/nginx/html'),
             'runtime-sdk': ('runtime.jar', '/app/app.jar'),
             'sdk': ('runtime.jar', '/app/app.jar')}


class DeployError(Exception):
    pass


def validate_layout(release, target):
    if target == Path(target.anchor) or target == Path.home().resolve():
        raise DeployError('部署目标不能是文件系统根目录或用户主目录；请使用 /root/tianshu-decision-engine。')
    if release != target and release.parent not in (target / 'releases', target / 'deploy-prod/releases'):
        raise DeployError('新包必须解压到部署目录的 deploy-prod/releases/（旧平铺为 releases/）下，再指定外层目录为 --target。')


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


def compose_plan(command, target, files, project=None, containers=(), sdk=False, selected=None, env_root=None):
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
    env_root = env_root or (target / 'config' if (target / LAYOUT_FILE).is_file() else target)
    for name in envs:
        if not (env_root / name).is_file():
            raise DeployError('首次部署请先填写：' + str(env_root / name))
        args += ['--env-file', str(env_root / name)]
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
    if selected is not None:
        services = [name for name in SERVICE_ORDER if name in selected and name in config['services']]
    if not services:
        raise DeployError('没有找到可部署的应用服务。')
    # 固定解析后的项目名，保留通过 -p 创建的现有项目和命名卷。
    if not project:
        args[len(command):len(command)] = ['--project-name', config['name']]
    return {'args': args, 'config': config, 'services': services, 'counts': counts,
            'files': files, 'project': project or config['name'], 'containers': containers}


def container_groups(target, services=APP_SERVICES):
    ids = run(['docker', 'ps', '-aq', '--filter', 'label=com.docker.compose.project']).stdout.split()
    labels_output = run(['docker', 'inspect', '--format', '{{json .Config.Labels}}'] + ids).stdout if ids else ''
    containers = [{'Config': {'Labels': json.loads(line)}, '_id': identifier}
                  for identifier, line in zip(ids, labels_output.splitlines()) if line.strip()]
    groups = {}
    for item in containers:
        labels = item.get('Config', {}).get('Labels') or {}
        if labels.get('com.docker.compose.service') not in services:
            continue
        if labels.get('com.docker.compose.oneoff', '').lower() == 'true':
            continue
        files = tuple(Path(p).resolve() for p in labels.get('com.docker.compose.project.config_files', '').split(',') if p)
        if not any(file.parent == target / 'docker' for file in files):
            continue
        groups.setdefault((labels['com.docker.compose.project'], files), []).append(item)
    return groups


def discover_plans(command, target, sdk=False, client_only=False):
    if ((target / 'config').is_dir() or (target / 'deploy-prod/current').exists()) and not (target / LAYOUT_FILE).is_file():
        raise DeployError('共享目录缺少 .deployment-layout.json，不能回退读取外层 env 备份；请恢复原布局记录。')
    if (target / 'deploy-prod/docker').is_dir() and not (target / LAYOUT_FILE).is_file():
        raise DeployError('检测到旧 deploy-prod 布局；外层 env 是备份。请先执行 --adopt-layout，不能直接使用外层 env。')
    groups = container_groups(target)
    if client_only:
        groups = {key: [c for c in items if c['Config']['Labels']['com.docker.compose.service'] in ('sdk', 'runtime-sdk')]
                  for key, items in groups.items()}
        groups = {key: items for key, items in groups.items() if items}
    if (target / LAYOUT_FILE).is_file():
        state = json.loads((target / LAYOUT_FILE).read_text(encoding='utf-8'))
        for saved in state['projects']:
            selected = [s for s in saved['services'] if s in APP_SERVICES and (not client_only or s in ('sdk', 'runtime-sdk'))]
            if not selected:
                continue
            files = tuple(target / name for name in saved['files'])
            key = (saved['project'], files)
            if key not in groups:
                groups[key] = [{'Config': {'Labels': {'com.docker.compose.service': service}}}
                               for service in selected for _ in range(saved['counts'].get(service, 1))]
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
            if (target / LAYOUT_FILE).is_file() and destination.parent != target / 'deploy-prod/current':
                raise DeployError('应用制品必须位于 deploy-prod/current，请检查 config/ 中的 RUNTIME_DIR。')
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
                if destination.exists() and not (target / LAYOUT_FILE).is_file():
                    copy(destination, backup / (str(len(artifacts)) + '-' + name))
            except BaseException:
                for entry in artifacts.values():
                    shutil.rmtree(entry['stage'])
                raise
    return list(artifacts.values())


def stop_apps(plans, target):
    for service in reversed(SERVICE_ORDER):
        for plan in plans:
            if service in plan['services']:
                run(plan['args'] + ['stop', service], cwd=target)


def start_apps(plans, target):
    for service in SERVICE_ORDER:
        for plan in plans:
            if service not in plan['services']:
                continue
            # 普通更新只选择应用；一次性目录迁移按基础设施、server、web、SDK 顺序恢复。
            args = ['up', '-d', '--no-deps', '--no-build', '--force-recreate', '--wait', '--wait-timeout', '300']
            if service in plan['counts']:
                args += ['--scale', service + '=' + str(plan['counts'][service])]
            run(plan['args'] + args + [service], cwd=target)


def host_path(value):
    # Docker Desktop 的 Linux daemon 返回宿主机路径；服务器原生 Linux 无需转换。
    if os.name == 'nt':
        for prefix in ('/run/desktop/mnt/host/', '/host_mnt/'):
            if value.startswith(prefix):
                drive, rest = value[len(prefix):].split('/', 1)
                return Path(drive + ':/' + rest).resolve()
    return Path(value).resolve()


def check_live_mounts(plans):
    for plan in plans:
        for item in plan['containers']:
            service = item['Config']['Labels']['com.docker.compose.service']
            actual = json.loads(run(['docker', 'inspect', '--format', '{{json .Mounts}}', item['_id']]).stdout)
            by_target = {mount['Destination']: mount for mount in actual}
            for mount in plan['config']['services'][service].get('volumes', []):
                live = by_target.get(mount['target'])
                if not live or live['Type'] != mount['type']:
                    raise DeployError('实际容器挂载与旧 Compose 不一致，停止整理：' + service)
                if mount['type'] == 'bind' and host_path(live['Source']) != Path(mount['source']).resolve():
                    raise DeployError('实际数据目录与旧 Compose 不一致，停止整理：' + service)
                if mount['type'] == 'volume':
                    expected = plan['config']['volumes'][mount['source']]['name']
                    if live['Name'] != expected:
                        raise DeployError('实际数据卷名称与旧 Compose 不一致，停止整理：' + service)


def replace_file(source, destination):
    handle, name = tempfile.mkstemp(prefix='.' + destination.name + '-recovery-', dir=destination.parent)
    os.close(handle)
    temporary = Path(name)
    try:
        shutil.copy2(source, temporary)
        temporary.replace(destination)
    finally:
        if temporary.exists():
            temporary.unlink()


def copy_container_file(container_id, destination):
    # 孤立 bind mount 的宿主机源路径已不存在，docker cp 可能无法重新解析挂载。
    # 从仍运行的容器直接读取；不分配 TTY、不做文本解码，确保 JAR 字节原样落盘。
    with destination.open('xb') as output:
        result = subprocess.run(['docker', 'exec', container_id, 'cat', '/app/app.jar'],
                                stdout=output, stderr=subprocess.PIPE, timeout=1800)
    if result.returncode:
        raise DeployError('无法从运行中的 SDK 取回 JAR；请保留旧容器，不要停止或删除。')


def recover_orphan_sdk(command, legacy, plans, indexes, archive):
    """恢复迁移目录后仅存于容器挂载中的 SDK，绝不以发布目录的另一版 JAR 代替。"""
    sdk_plans = [p for p in plans if any(s in ('runtime-sdk', 'sdk') for s in p['services'])]
    if len(indexes) != 1 or len(sdk_plans) != 1:
        raise DeployError('存在多套 SDK 制品，无法自动合并到同一 current；请先核对各自版本。')
    index = indexes[0]
    plan = plans[index]
    if plan['services'] != ['runtime-sdk'] or plan['files'] != [legacy / 'docker/docker-compose.client.yml']:
        raise DeployError('旧 SDK 使用非标准 Compose，不能自动修复失效挂载。')
    env_file = legacy / '.env.client'
    canonical = legacy / 'docker/runtime/runtime.jar'
    if not env_file.is_file() or env_file.resolve() != env_file or not canonical.is_file() or canonical.resolve() != canonical:
        raise DeployError('缺少真实的内层 SDK 配置/制品，不能修复失效挂载。')
    containers = plan['containers']
    if not containers:
        raise DeployError('缺少旧 SDK 容器，无法取回正在运行的 JAR。')

    def mounted_digest(item):
        result = run(['docker', 'exec', item['_id'], 'sha256sum', '/app/app.jar'])
        match = re.fullmatch(r'([0-9a-fA-F]{64})\s+\*?/app/app.jar', result.stdout.strip())
        if not match:
            raise DeployError('无法确认容器内 SDK JAR 的摘要。')
        return match[1].lower()

    digests = {mounted_digest(item) for item in containers}
    if len(digests) != 1:
        raise DeployError('SDK 各副本正在运行不同 JAR，不能自动选择版本。')
    expected = digests.pop()
    # 修复路径不能顺带改变正在运行的凭据/参数；只比较配置指定的键，不输出值。
    configured = plan['config']['services']['runtime-sdk'].get('environment', {})
    for item in containers:
        values = json.loads(run(['docker', 'inspect', '--format', '{{json .Config.Env}}', item['_id']]).stdout)
        live = dict(value.split('=', 1) for value in values or [] if '=' in value)
        changed = [key for key, value in configured.items() if value is not None
                   and live.get(key) != (str(value).lower() if isinstance(value, bool) else str(value))]
        if changed:
            raise DeployError('SDK 运行参数与内层配置不同，请先核对这些键：' + ', '.join(sorted(changed)))
    recovery = archive / 'sdk-mount-recovery'
    recovery.mkdir(mode=0o700)
    (recovery / 'env-original').mkdir(mode=0o700)
    candidate_env_root = recovery / 'env-fixed'
    candidate_env_root.mkdir(mode=0o700)
    captured = recovery / 'runtime-from-container.jar'
    copy_container_file(containers[0]['_id'], captured)
    if not captured.is_file() or captured.is_symlink() or digest(captured) != expected:
        raise DeployError('取回的 SDK JAR 摘要不一致，未停止服务或替换原文件。')
    if any(mounted_digest(item) != expected for item in containers):
        raise DeployError('取回期间 SDK JAR 已变化，未停止服务。')
    original_jar = recovery / 'runtime-before-recovery.jar'
    original_env = recovery / 'env-original/.env.client'
    shutil.copy2(canonical, original_jar)
    shutil.copy2(env_file, original_env)
    original_env.chmod(0o600)
    before_digest = digest(original_jar)
    lines = [line for line in env_file.read_text(encoding='utf-8').splitlines()
             if not re.match(r'^\s*(?:export\s+)?RUNTIME_DIR\s*=', line)]
    candidate_env = candidate_env_root / '.env.client'
    candidate_env.write_text('\n'.join(lines) + '\nRUNTIME_DIR=./runtime\n', encoding='utf-8')
    candidate_env.chmod(0o600)
    normalized = compose_plan(command, legacy, plan['files'], plan['project'], containers,
                              selected=plan['services'], env_root=candidate_env_root)
    mounts = normalized['config']['services']['runtime-sdk'].get('volumes', [])
    if not any(m.get('type') == 'bind' and m.get('target') == '/app/app.jar'
               and Path(m['source']).resolve() == canonical for m in mounts):
        raise DeployError('修正 RUNTIME_DIR 后 SDK 仍未指向内层制品，未停止服务。')
    if normalized['config']['services']['runtime-sdk'].get('environment', {}) != configured:
        raise DeployError('路径修复意外改变了 SDK 运行参数，未停止服务。')
    if digest(canonical) != before_digest or env_file.read_bytes() != original_env.read_bytes():
        raise DeployError('SDK 文件或配置在检查期间发生变化，未停止服务。')
    record = {'container_ids': [item['_id'] for item in containers],
              'running_sha256': expected, 'previous_local_sha256': before_digest,
              'runtime_dir': './runtime'}
    (recovery / 'recovery.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    try:
        replace_file(captured, canonical)
        replace_file(candidate_env, env_file)
    except BaseException:
        replace_file(original_jar, canonical)
        replace_file(original_env, env_file)
        raise
    normalized['args'] = [str(env_file) if arg == str(candidate_env) else arg for arg in normalized['args']]
    plans[index] = normalized
    print('已取回正在运行的 SDK JAR 并修复旧挂载路径，SHA256=' + expected + '；原 JAR 和配置保留在 ' + str(recovery), flush=True)


def adopt_layout(target, command):
    """一次性整理用户已有 deploy-prod 布局，不安装新版应用或执行 SQL。"""
    legacy = target / 'deploy-prod'
    if (target / LAYOUT_FILE).exists():
        print('目录已整理，无需重复执行 --adopt-layout。')
        return
    if (legacy / '.deploy.lock').exists():
        raise DeployError('旧 deploy-prod 中存在部署锁，请先确认旧更新已经结束。')
    for destination in (target / 'docker', target / 'config', legacy / 'current', legacy / 'previous'):
        if destination.exists() or destination.is_symlink():
            raise DeployError('目标已存在，不能自动覆盖：' + str(destination))
    for source in (legacy, legacy / 'docker', legacy / 'docker/runtime'):
        if not source.is_dir() or source.resolve() != source.absolute():
            raise DeployError('旧部署目录不存在或含符号链接：' + str(source))
    if not (legacy / '.env.server').is_file():
        raise DeployError('缺少 deploy-prod 内正在使用的 .env.server；不能使用外层备份代替。')
    groups = container_groups(legacy, SERVICE_ORDER)
    if not groups:
        raise DeployError('未找到旧 deploy-prod 对应的容器，无法确认原项目名和数据卷；请先核对原部署。')
    old_plans = []
    for (project, files), items in groups.items():
        if any(file.parent != legacy / 'docker' for file in files):
            raise DeployError('存在部署目录外的 Compose override，请先单独整理：' + project)
        selected = [item['Config']['Labels']['com.docker.compose.service'] for item in items]
        old_plans.append(compose_plan(command, legacy, list(files), project, items, selected=selected))
    check_live_mounts(old_plans)
    orphan_sdk_indexes = []
    for plan_index, plan in enumerate(old_plans):
        for service in plan['services']:
            for mount in plan['config']['services'][service].get('volumes', []):
                if mount['type'] != 'bind':
                    continue
                source = Path(mount['source']).absolute()
                if legacy in source.parents:
                    if legacy / 'docker' not in source.parents or source.resolve() != source:
                        raise DeployError('旧目录存在额外挂载或符号链接，不能自动搬迁：' + service)
            if service not in ARTIFACTS:
                continue
            artifact, destination = ARTIFACTS[service]
            mounts = plan['config']['services'][service].get('volumes', [])
            expected = legacy / 'docker/runtime' / artifact
            if not any(m.get('target') == destination and m.get('type') == 'bind'
                       and Path(m['source']).resolve() == expected for m in mounts):
                orphan_source = target / 'docker/runtime/runtime.jar'
                if service == 'runtime-sdk' and not orphan_source.exists() and any(
                        m.get('target') == destination and m.get('type') == 'bind'
                        and Path(m['source']).resolve() == orphan_source for m in mounts):
                    orphan_sdk_indexes.append(plan_index)
                else:
                    actual = [m.get('source') for m in mounts if m.get('target') == destination]
                    raise DeployError('旧应用使用自定义制品路径，不能自动搬迁：' + service
                                      + '；期望 ' + str(expected) + '；实际 ' + str(actual))
    archive_root = target / 'legacy'
    archive_root.mkdir(exist_ok=True)
    archive = Path(tempfile.mkdtemp(prefix='before-layout-', dir=archive_root))
    archive.chmod(0o700)
    if orphan_sdk_indexes:
        recover_orphan_sdk(command, legacy, old_plans, orphan_sdk_indexes, archive)
    config = archive / 'config-next'
    config.mkdir(mode=0o700)
    for name in ('.env.server', '.env.client'):
        source = legacy / name
        if source.is_file():
            text = source.read_text(encoding='utf-8')
            lines = [line for line in text.splitlines() if not re.match(r'^\s*(?:export\s+)?RUNTIME_DIR\s*=', line)]
            (config / name).write_text('\n'.join(lines) + '\nRUNTIME_DIR=../deploy-prod/current\n', encoding='utf-8')
            (config / name).chmod(0o600)
    moved = []
    new_plans = []
    added_build_info = False
    started_new = False
    state_written = False

    def move(source, destination):
        source.rename(destination)
        moved.append((source, destination))

    print('一次性目录整理：停止旧应用及本机相关基础设施，保留原项目名、卷和数据。', flush=True)
    try:
        stop_apps(old_plans, legacy)
        move(legacy / 'docker', target / 'docker')
        move(target / 'docker/runtime', legacy / 'current')
        move(config, target / 'config')
        if (legacy / 'BUILD-INFO.txt').is_file() and not (legacy / 'current/BUILD-INFO.txt').exists():
            shutil.copy2(legacy / 'BUILD-INFO.txt', legacy / 'current/BUILD-INFO.txt')
            added_build_info = True
        for old in old_plans:
            files = [target / 'docker' / file.name for file in old['files']]
            new = compose_plan(command, target, files, old['project'], old['containers'],
                               selected=old['services'], env_root=target / 'config')
            # 拒绝 YAML 中仍写死旧绝对路径的挂载，避免 MySQL/Redis 创建一个空数据目录。
            for service in old['services']:
                updated = {m['target']: m for m in new['config']['services'][service].get('volumes', [])}
                for mount in old['config']['services'][service].get('volumes', []):
                    if mount['type'] != 'bind':
                        continue
                    before = Path(mount['source'])
                    expected = before
                    if legacy / 'docker/runtime' in before.parents:
                        expected = legacy / 'current' / before.relative_to(legacy / 'docker/runtime')
                    elif legacy / 'docker' in before.parents:
                        expected = target / 'docker' / before.relative_to(legacy / 'docker')
                    after = updated.get(mount['target'], {})
                    if after.get('type') != 'bind' or Path(after.get('source', '')) != expected:
                        raise DeployError('迁移后的挂载路径不匹配，请先调整写死的旧路径：' + service)
            new_plans.append(new)
        started_new = True
        start_apps(new_plans, target)
        # 旧配置、文档和源码按原样归档；外层两份 env 备份完全不动。
        for item in list(legacy.iterdir()):
            if item.name not in ('current', 'previous', 'releases', 'history', '.deploy.lock'):
                move(item, archive / item.name)
        state = {'version': 1, 'projects': [
            {'project': p['project'], 'files': [f.relative_to(target).as_posix() for f in p['files']],
             'services': p['services'], 'counts': p['counts']} for p in new_plans]}
        state_file = archive / 'layout.json'
        state_file.write_text(json.dumps(state, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        state_file.rename(target / LAYOUT_FILE)
        state_written = True
        print('目录整理完成。生效配置位于 config/，共享服务位于 docker/，当前应用位于 deploy-prod/current/。', flush=True)
    except BaseException:
        # 只有确认新容器已停止，才能把数据目录搬回去。
        if started_new:
            stop_apps(new_plans, target)
        if state_written:
            (target / LAYOUT_FILE).unlink()
        if added_build_info:
            (legacy / 'current/BUILD-INFO.txt').unlink()
        for source, destination in reversed(moved):
            destination.rename(source)
        start_apps(old_plans, legacy)
        print('目录整理失败，已恢复旧目录与旧部署。外层 env 备份未修改。', file=sys.stderr)
        raise


def deploy(release, target, command, sdk=False, client_only=False, migration=None):
    validate_layout(release, target)
    plans = discover_plans(command, target, sdk, client_only)
    versioned = (target / LAYOUT_FILE).is_file()
    app_root = target / 'deploy-prod'
    backup_root = app_root / 'history' if versioned else target / 'backups'
    backup_root.mkdir(parents=True, exist_ok=True)
    backup = Path(tempfile.mkdtemp(prefix='update-', dir=backup_root))
    backup.chmod(0o700)
    if versioned:
        shutil.copytree(app_root / 'current', backup / 'previous')
    artifacts = prepare_artifacts(release, target, plans, backup)
    shutil.copy2(release / 'BUILD-INFO.txt', backup / 'new-BUILD-INFO.txt')
    if versioned:
        destination = app_root / 'current/BUILD-INFO.txt'
        stage = Path(tempfile.mkdtemp(prefix='.build-info-update-', dir=destination.parent))
        shutil.copy2(release / 'BUILD-INFO.txt', stage / 'new')
        artifacts.append({'destination': destination, 'stage': stage, 'installed': False})
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
        if versioned:
            previous = app_root / 'previous'
            if previous.exists():
                previous.rename(backup / 'older-previous')
            try:
                (backup / 'previous').rename(previous)
            except BaseException:
                if (backup / 'older-previous').exists():
                    (backup / 'older-previous').rename(previous)
                raise
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
    parser.add_argument('--adopt-layout', action='store_true', help='一次性整理旧 deploy-prod：共享服务和生效配置移到外层，保留原应用版本')
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
    if options.adopt_layout and migration:
        raise DeployError('--adopt-layout 只整理目录；数据库迁移请在后续应用更新时执行。')
    if migration and not migration.is_file():
        raise DeployError('迁移脚本不存在：' + str(migration))
    if not options.adopt_layout and (release / 'migrations/upgrade.sh').exists() and migration is None:
        raise DeployError('本版包含迁移入口，请检查后添加 --migration-script ' + str(release / 'migrations/upgrade.sh'))
    lock = target / '.deploy.lock'
    try:
        lock.mkdir()
    except FileExistsError:
        raise DeployError('已有部署锁 .deploy.lock；请确认没有其他更新运行。')
    try:
        if options.adopt_layout:
            adopt_layout(target, command)
        else:
            deploy(release, target, command, options.sdk, options.client_only, migration)
    finally:
        lock.rmdir()


if __name__ == '__main__':
    try:
        main()
    except (DeployError, OSError, ValueError, subprocess.TimeoutExpired) as error:
        print('错误：' + str(error), file=sys.stderr)
        sys.exit(1)
