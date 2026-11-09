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
            self.assertIn('{{json .Config.Labels}}', command)
            output = '\n'.join(json.dumps(c['Config']['Labels']) for c in self.containers)
        elif 'config' in command:
            services = ['runtime-sdk'] if any('docker-compose.client.yml' in str(x) for x in command) else ['server', 'web']
            config = {'name': 'env-default-project', 'services': {}}
            for service in services:
                name, mount = deployment.ARTIFACTS[service]
                config['services'][service] = {'volumes': [{'type': 'bind', 'source': str(self.target / 'docker/runtime' / name), 'target': mount}]}
            config['services']['mysql'] = {'image': 'must-not-start'}
            output = json.dumps(config)
        elif 'up' in command and self.fail_start and command[-1] == 'web':
            raise deployment.DeployError('readiness failed')
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
