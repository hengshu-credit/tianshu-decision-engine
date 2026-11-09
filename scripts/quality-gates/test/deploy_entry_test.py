import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('deployment', ROOT / 'scripts/deploy.py')
deployment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deployment)


class DeployTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='tianshu deploy ')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        self.target = self.root / 'installation'
        self.release = self.target / 'releases/release'
        self.commands = []
        self.fail_start = False
        self.fail_migration = False
        self.layout_mode = False
        self.fail_layout_service = None
        self.fail_new_config = False
        self.keep_old_data_path = False
        self.files = {
            '.env.server': 'secret-server', '.env.client': 'secret-client',
            'uploaded-package.zip': 'keep-uploaded-archive',
            'docker/docker-compose.server.yml': 'original-compose',
            'docker/docker-compose.client.yml': 'original-client-compose',
            'docker/nginx/default.conf': 'original-nginx',
            'docker/rule-engine-mysql/data/keep': 'mysql-data',
            'docker/rule-engine-redis/data/keep': 'redis-data',
            'docker/rule-engine-kafka/start-kafka.sh': 'original-kafka',
            'docker/runtime/server.jar': 'old-server',
            'docker/runtime/runtime.jar': 'old-runtime',
            'docker/runtime/dist/index.html': 'old-index',
            'docker/runtime/dist/obsolete.js': 'old-asset',
        }
        for name, content in self.files.items():
            self.write(self.target / name, content)
        for name, content in {'deploy.py': 'entry', 'BUILD-INFO.txt': 'version',
                              'docker/runtime/server.jar': 'new-server',
                              'docker/runtime/runtime.jar': 'new-runtime',
                              'docker/runtime/dist/index.html': 'new-index',
                              'docker/runtime/dist/new.js': 'new-asset'}.items():
            self.write(self.release / name, content)
        lines = [hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.relative_to(self.release).as_posix()
                 for p in self.release.rglob('*') if p.is_file()]
        self.write(self.release / 'SHA256SUMS.txt', '\n'.join(lines) + '\n')
        self.containers = [self.container('server'), self.container('server'),
                           self.container('web'), self.container('runtime-sdk', client=True)]

    @staticmethod
    def write(file, text):
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(text, encoding='utf-8')

    def container(self, service, client=False):
        return {'Config': {'Labels': {'com.docker.compose.service': service,
                                      'com.docker.compose.project': 'original-client' if client else 'original-server',
                                      'com.docker.compose.project.config_files': str(self.target / ('docker/docker-compose.client.yml' if client else 'docker/docker-compose.server.yml'))}}}

    def fake_run(self, command, cwd=None, check=True, env=None):
        self.commands.append(command)
        output = ''
        if command[1:3] == ['ps', '-aq']:
            output = ' '.join(str(i) for i in range(len(self.containers)))
        elif command[1] == 'inspect':
            if '{{json .Mounts}}' in command:
                output = json.dumps(self.live_mounts[int(command[-1])])
            else:
                self.assertIn('{{json .Config.Labels}}', command)
                output = '\n'.join(json.dumps(c['Config']['Labels']) for c in self.containers)
        elif 'config' in command:
            services = ['runtime-sdk'] if any('docker-compose.client.yml' in str(x) for x in command) else ['server', 'web']
            config = {'name': 'env-default-project', 'services': {}}
            base = Path(command[command.index('-f') + 1]).parent.parent
            if self.layout_mode and self.fail_new_config and base == self.target:
                raise deployment.DeployError('new config failed')
            runtime = self.target / 'docker/runtime'
            if self.layout_mode:
                runtime = self.target / 'deploy-prod/current' if base == self.target else base / 'docker/runtime'
            for service in services:
                name, mount = deployment.ARTIFACTS[service]
                config['services'][service] = {'volumes': [{'type': 'bind', 'source': str(runtime / name), 'target': mount}]}
            if self.layout_mode and 'server' in services:
                config['services']['mysql'] = {'volumes': [{'type': 'bind', 'source': str(base / 'docker/rule-engine-mysql/data'), 'target': '/var/lib/mysql'}]}
                if self.keep_old_data_path and base == self.target:
                    config['services']['mysql']['volumes'][0]['source'] = str(self.target / 'deploy-prod/docker/rule-engine-mysql/data')
                config['services']['redis'] = {'volumes': [{'type': 'bind', 'source': str(base / 'docker/rule-engine-redis/data'), 'target': '/data'}]}
                config['services']['kafka'] = {'volumes': [{'type': 'volume', 'source': 'kafka-data', 'target': '/var/lib/kafka/data'}]}
                config['volumes'] = {'kafka-data': {'name': 'original-server_kafka-data'}}
            else:
                config['services']['mysql'] = {'image': 'must-not-start'}
            output = json.dumps(config)
        elif 'up' in command and self.fail_start and command[-1] == 'web':
            raise deployment.DeployError('readiness failed')
        elif 'up' in command and self.layout_mode:
            base = Path(command[command.index('-f') + 1]).parent.parent
            if command[-1] == self.fail_layout_service and base == self.target:
                raise deployment.DeployError('infrastructure start failed')
            for item in self.containers:
                labels = item['Config']['Labels']
                if labels['com.docker.compose.service'] == command[-1]:
                    labels['com.docker.compose.project.config_files'] = str(base / 'docker' / Path(labels['com.docker.compose.project.config_files']).name)
        elif command[0] == 'bash':
            self.assertEqual((self.target / 'docker/runtime/server.jar').read_text(), 'old-server')
            self.assertTrue(any('stop' in c for c in self.commands))
            self.assertEqual(env['TIANSHU_RELEASE_DIR'], str(self.release))
            if self.fail_migration:
                raise deployment.DeployError('migration failed')
        return subprocess.CompletedProcess(command, 0, output, '')

    def deploy(self, **kwargs):
        with patch.object(deployment, 'run', side_effect=self.fake_run):
            deployment.deploy(self.release, self.target, ['docker', 'compose'], **kwargs)

    def make_nested_legacy(self):
        self.layout_mode = True
        legacy = self.target / 'deploy-prod'
        legacy.mkdir()
        (self.target / 'docker').rename(legacy / 'docker')
        (self.target / 'releases').rename(legacy / 'releases')
        self.release = legacy / 'releases/release'
        self.write(legacy / '.env.server', 'MYSQL_PASSWORD=active-secret\nRUNTIME_DIR=./runtime\n')
        self.write(legacy / '.env.client', 'SDK_ACCESS_TOKEN=active-sdk\nRUNTIME_DIR=./runtime\n')
        self.write(legacy / 'BUILD-INFO.txt', 'old-version')
        self.write(legacy / 'docs/keep', 'old-docs')
        self.containers.extend([self.container(s) for s in ('mysql', 'redis', 'kafka')])
        self.live_mounts = []
        for item in self.containers:
            labels = item['Config']['Labels']
            labels['com.docker.compose.project.config_files'] = str(legacy / 'docker' / Path(labels['com.docker.compose.project.config_files']).name)
            service = labels['com.docker.compose.service']
            if service in deployment.ARTIFACTS:
                name, dest = deployment.ARTIFACTS[service]
                mounts = [{'Type': 'bind', 'Source': str(legacy / 'docker/runtime' / name), 'Destination': dest}]
            elif service == 'kafka':
                mounts = [{'Type': 'volume', 'Name': 'original-server_kafka-data', 'Destination': '/var/lib/kafka/data'}]
            else:
                dest = '/var/lib/mysql' if service == 'mysql' else '/data'
                mounts = [{'Type': 'bind', 'Source': str(legacy / 'docker' / ('rule-engine-' + service) / 'data'), 'Destination': dest}]
            self.live_mounts.append(mounts)
        return legacy

    def adopt(self):
        with patch.object(deployment, 'run', side_effect=self.fake_run):
            deployment.adopt_layout(self.target, ['docker', 'compose'])

    def test_adopt_uses_active_inner_env_and_preserves_outer_backup_and_data(self):
        legacy = self.make_nested_legacy()
        self.adopt()
        self.assertEqual((self.target / '.env.server').read_text(), 'secret-server')
        self.assertEqual((self.target / '.env.client').read_text(), 'secret-client')
        active = (self.target / 'config/.env.server').read_text()
        self.assertIn('MYSQL_PASSWORD=active-secret', active)
        self.assertIn('RUNTIME_DIR=../deploy-prod/current', active)
        self.assertEqual((self.target / 'docker/rule-engine-mysql/data/keep').read_text(), 'mysql-data')
        self.assertEqual((self.target / 'docker/rule-engine-redis/data/keep').read_text(), 'redis-data')
        self.assertEqual((legacy / 'current/server.jar').read_text(), 'old-server')
        self.assertEqual((legacy / 'current/BUILD-INFO.txt').read_text(), 'old-version')
        self.assertEqual(set(p.name for p in legacy.iterdir()), {'current', 'releases'})
        archive = next((self.target / 'legacy').glob('before-layout-*'))
        self.assertIn('active-secret', (archive / '.env.server').read_text())
        self.assertEqual((archive / 'docs/keep').read_text(), 'old-docs')
        state = json.loads((self.target / deployment.LAYOUT_FILE).read_text())
        self.assertEqual({p['project'] for p in state['projects']}, {'original-server', 'original-client'})
        self.assertEqual([c[-1] for c in self.commands if 'up' in c], ['mysql', 'redis', 'kafka', 'server', 'web', 'runtime-sdk'])
        self.assertFalse(any('down' in c or 'rm' in c for c in self.commands))

    def test_nested_legacy_is_not_started_using_outer_env_backups(self):
        self.make_nested_legacy()
        self.assertRaises(deployment.DeployError, self.deploy)
        self.assertFalse(self.commands)

    def test_missing_layout_record_never_falls_back_to_outer_env_backup(self):
        self.make_nested_legacy()
        self.adopt()
        (self.target / deployment.LAYOUT_FILE).unlink()
        self.commands.clear()
        self.assertRaises(deployment.DeployError, self.deploy)
        self.assertFalse(self.commands)

    def test_adopt_refuses_changed_live_mounts_before_stopping(self):
        self.make_nested_legacy()
        self.live_mounts[-1][0]['Name'] = 'different-kafka-volume'
        self.assertRaises(deployment.DeployError, self.adopt)
        self.assertFalse(any('stop' in c for c in self.commands))
        self.assertFalse((self.target / 'docker').exists())

    def test_adopt_failure_restores_old_paths_and_restarts_original_projects(self):
        legacy = self.make_nested_legacy()
        self.fail_layout_service = 'redis'
        self.assertRaises(deployment.DeployError, self.adopt)
        self.assertTrue((legacy / 'docker/runtime/server.jar').is_file())
        self.assertFalse((legacy / 'docker/runtime/BUILD-INFO.txt').exists())
        self.assertEqual((legacy / 'docker/rule-engine-mysql/data/keep').read_text(), 'mysql-data')
        self.assertFalse((self.target / 'config').exists())
        self.assertFalse((self.target / 'docker').exists())
        self.assertFalse((self.target / deployment.LAYOUT_FILE).exists())
        self.assertEqual((self.target / '.env.server').read_text(), 'secret-server')
        self.assertIn('original-client', [c for c in self.commands if 'up' in c][-1])

    def test_adopt_config_failure_after_move_restores_without_using_missing_old_files(self):
        legacy = self.make_nested_legacy()
        self.fail_new_config = True
        self.assertRaises(deployment.DeployError, self.adopt)
        self.assertTrue((legacy / 'docker/runtime/server.jar').is_file())
        self.assertTrue((legacy / '.env.server').is_file())
        self.assertFalse((self.target / 'config').exists())
        self.assertFalse((self.target / deployment.LAYOUT_FILE).exists())
        for command in (c for c in self.commands if 'up' in c):
            self.assertEqual(Path(command[command.index('-f') + 1]).parent, legacy / 'docker')

    def test_adopt_rejects_hardcoded_old_data_path_before_starting_new_containers(self):
        legacy = self.make_nested_legacy()
        self.keep_old_data_path = True
        self.assertRaises(deployment.DeployError, self.adopt)
        self.assertEqual((legacy / 'docker/rule-engine-mysql/data/keep').read_text(), 'mysql-data')
        self.assertFalse((self.target / deployment.LAYOUT_FILE).exists())
        for command in (c for c in self.commands if 'up' in c):
            self.assertEqual(Path(command[command.index('-f') + 1]).parent, legacy / 'docker')

    def test_versioned_updates_previous_only_after_success_and_never_restarts_infrastructure(self):
        legacy = self.make_nested_legacy()
        self.adopt()
        self.commands.clear()
        self.deploy()
        self.assertEqual((legacy / 'current/server.jar').read_text(), 'new-server')
        self.assertEqual((legacy / 'previous/server.jar').read_text(), 'old-server')
        self.assertEqual((legacy / 'previous/BUILD-INFO.txt').read_text(), 'old-version')
        self.assertEqual((legacy / 'current/BUILD-INFO.txt').read_text(), 'version')
        self.assertEqual([c[-1] for c in self.commands if 'up' in c], ['server', 'web', 'runtime-sdk'])
        for command in (c for c in self.commands if 'up' in c):
            self.assertEqual(Path(command[command.index('--env-file') + 1]).parent, self.target / 'config')
        self.write(self.release / 'docker/runtime/server.jar', 'third-server')
        self.commands.clear()
        self.fail_start = True
        self.assertRaises(deployment.DeployError, self.deploy)
        self.assertEqual((legacy / 'current/server.jar').read_text(), 'new-server')
        self.assertEqual((legacy / 'previous/server.jar').read_text(), 'old-server')
        self.fail_start = False
        self.deploy()
        self.assertEqual((legacy / 'current/server.jar').read_text(), 'third-server')
        self.assertEqual((legacy / 'previous/server.jar').read_text(), 'new-server')
        self.assertTrue(any(p.read_text() == 'old-server' for p in (legacy / 'history').glob('*/older-previous/server.jar')))

    def test_auto_selects_both_compose_commands_and_rejects_v1(self):
        for plugin in (True, False):
            def version(command, **kwargs):
                available = plugin or command[0] == 'docker-compose'
                return subprocess.CompletedProcess(command, 0 if available else 1, 'v5.1.4' if available else '', '')
            with patch.object(deployment, 'run', side_effect=version):
                self.assertEqual(deployment.detect_compose(), ['docker', 'compose'] if plugin else ['docker-compose'])
        with patch.object(deployment, 'run', return_value=subprocess.CompletedProcess([], 0, '1.29.2', '')):
            self.assertRaises(deployment.DeployError, deployment.detect_compose)

    def test_deployment_layout_keeps_release_under_installation(self):
        deployment.validate_layout(self.release, self.target)
        deployment.validate_layout(self.target, self.target)
        self.assertRaises(deployment.DeployError, deployment.validate_layout, self.root / 'release', self.target)
        self.assertRaises(deployment.DeployError, deployment.validate_layout, self.target / 'docker/runtime', self.target)
        self.assertRaises(deployment.DeployError, deployment.validate_layout, self.release, Path.home().resolve())
        self.assertRaises(deployment.DeployError, deployment.validate_layout, self.release, Path(self.target.anchor))

    def test_outside_release_fails_before_docker_or_file_updates(self):
        with patch.object(deployment, 'run', side_effect=self.fake_run):
            self.assertRaises(deployment.DeployError, deployment.deploy, self.root / 'release', self.target, ['docker', 'compose'])
        self.assertFalse(self.commands)
        self.assertFalse((self.target / 'backups').exists())

    def test_integrity_and_extra_frontend_files(self):
        deployment.verify_release(self.release)
        self.write(self.release / 'docker/runtime/dist/stale.js', 'unexpected')
        self.assertRaises(deployment.DeployError, deployment.verify_release, self.release)
        (self.release / 'docker/runtime/dist/stale.js').unlink()
        self.write(self.release / 'docker/runtime/server.jar', 'tampered')
        self.assertRaises(deployment.DeployError, deployment.verify_release, self.release)

    def test_integrity_rejects_manifest_traversal(self):
        with (self.release / 'SHA256SUMS.txt').open('a') as stream:
            stream.write('0' * 64 + '  ../outside\n')
        self.assertRaises(deployment.DeployError, deployment.verify_release, self.release)

    def test_private_package_allows_editing_env_but_still_checks_artifacts(self):
        self.write(self.release / '.env.server', 'initial')
        with (self.release / 'SHA256SUMS.txt').open('a') as stream:
            stream.write(hashlib.sha256(b'initial').hexdigest() + '  .env.server\n')
        self.write(self.release / '.env.server', 'production-configuration')
        deployment.verify_release(self.release)
        self.write(self.release / 'docker/runtime/runtime.jar', 'broken')
        self.assertRaises(deployment.DeployError, deployment.verify_release, self.release)

    def test_update_preserves_projects_replicas_env_infrastructure_and_backup(self):
        self.deploy()
        for name, content in self.files.items():
            if not name.startswith('docker/runtime/'):
                self.assertEqual((self.target / name).read_text(), content)
        self.assertEqual((self.target / 'docker/runtime/server.jar').read_text(), 'new-server')
        self.assertEqual((self.target / 'docker/runtime/runtime.jar').read_text(), 'new-runtime')
        self.assertFalse((self.target / 'docker/runtime/dist/obsolete.js').exists())
        self.assertEqual((self.target / 'docker/runtime/dist/new.js').read_text(), 'new-asset')
        self.assertEqual((self.release / 'docker/runtime/server.jar').read_text(), 'new-server')
        backups = list((self.target / 'backups').glob('update-*'))
        self.assertEqual(len(backups), 1)
        self.assertTrue(any(p.read_text() == 'old-server' for p in backups[0].glob('*server.jar')))
        updates = [c for c in self.commands if 'up' in c]
        self.assertEqual([c[-1] for c in updates], ['server', 'web', 'runtime-sdk'])
        for command in updates:
            self.assertIn('--no-deps', command)
            self.assertIn('--wait', command)
            self.assertIn('--force-recreate', command)
            self.assertNotIn('--remove-orphans', command)
        self.assertIn('server=2', updates[0])
        self.assertIn('original-server', updates[0])
        self.assertIn('original-client', updates[-1])

    def test_readiness_failure_restores_files_and_leaves_apps_stopped(self):
        self.fail_start = True
        self.assertRaises(deployment.DeployError, self.deploy)
        for name, content in self.files.items():
            self.assertEqual((self.target / name).read_text(), content)
        self.assertIn('stop', self.commands[-1])
        self.assertFalse((self.target / 'docker/runtime/dist/new.js').exists())

    def test_migration_runs_between_stop_and_replace_failure_prevents_start(self):
        self.fail_migration = True
        self.assertRaises(deployment.DeployError, self.deploy, migration=self.release / 'migration.sh')
        self.assertFalse(any('up' in c for c in self.commands))
        for name, content in self.files.items():
            self.assertEqual((self.target / name).read_text(), content)

    def test_missing_env_and_invalid_mount_fail_before_stopping(self):
        (self.target / '.env.server').unlink()
        self.assertRaises(deployment.DeployError, self.deploy)
        self.assertFalse(any('stop' in c for c in self.commands))

    def test_client_only_does_not_touch_server(self):
        self.deploy(client_only=True)
        self.assertEqual((self.target / 'docker/runtime/server.jar').read_text(), 'old-server')
        self.assertEqual([c[-1] for c in self.commands if 'up' in c], ['runtime-sdk'])

    def test_fresh_server_install_does_not_start_optional_sdk(self):
        self.containers = []
        self.deploy()
        updates = [c for c in self.commands if 'up' in c]
        self.assertEqual([c[-1] for c in updates], ['server', 'web'])
        self.assertIn('env-default-project', updates[0])
        self.assertEqual((self.target / 'docker/runtime/runtime.jar').read_text(), 'old-runtime')


if __name__ == '__main__':
    unittest.main()
