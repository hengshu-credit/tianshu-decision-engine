"""Load the two published credit examples, their credentials, and the local HTTP/SFTP supplier."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compose", choices=("full", "mysql"), default="full",
                        help="full stack, or standalone MySQL with an existing engine and Redis")
    parser.add_argument("--server-jar", type=Path, help="server JAR from the same release as the engine")
    parser.add_argument("--project", help="existing Compose project name (defaults to the env configuration)")
    parser.add_argument("--env-file", type=Path, default=Path(".env.server"))
    args = parser.parse_args()
    docker = Path(__file__).resolve().parents[1]
    root = docker.parent
    env = os.environ.copy()
    jar = args.server_jar or (Path(env["EXAMPLE_SERVER_JAR"]) if env.get("EXAMPLE_SERVER_JAR") else None)
    marker = "BOOT-INF/classes/com/hengshucredit/rule/server/example/CreditExampleImport.class"

    def supports_example(path):
        if not path or not path.is_file() or not zipfile.is_zipfile(path):
            return False
        with zipfile.ZipFile(path) as archive:
            return marker in archive.namelist()

    if jar is None:
        candidates = [docker / "runtime/server.jar", *sorted((root / "rule-engine-server/target").glob("rule-engine-server-*.jar"))]
        jar = next((p for p in candidates if supports_example(p)), None)
    if jar is None and (root / "pom.xml").exists():
        subprocess.run([shutil.which("mvn") or "mvn", "-pl", "rule-engine-server", "-am", "package", "-DskipTests"],
                       cwd=root, check=True)
        jar = next((p for p in (root / "rule-engine-server/target").glob("*.jar") if supports_example(p)), None)
    if not supports_example(jar):
        raise SystemExit("请用 --server-jar 指定支持 --load-credit-example 的天枢服务端 JAR")
    env["EXAMPLE_SERVER_JAR"] = jar.resolve().as_posix()
    compose = ["docker", "compose"]
    if args.compose == "full":
        compose.extend(["--env-file", str(args.env_file.resolve().with_name(".env.client"))])
    compose.extend(["--env-file", str(args.env_file.resolve())])
    if args.project:
        compose.extend(["--project-name", args.project])
    compose.extend(["-f", str(docker / f"docker-compose.{args.compose}.yml"), "--profile", "example-data"])
    subprocess.run([*compose, "up", "-d", "--build", "example-supplier"], env=env, check=True)
    subprocess.run([*compose, "run", "--rm", "-T", "example-data-import"], env=env, check=True)


if __name__ == "__main__":
    main()
