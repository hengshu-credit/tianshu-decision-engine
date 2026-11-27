"""Pack existing single-project offline transfer ZIPs into a console-importable project set."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
import zipfile

LIMIT = 512 * 1024 * 1024


def digest(path):
    with path.open("rb") as stream:
        h = hashlib.sha256()
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
        return h.hexdigest()


def describe(path):
    with zipfile.ZipFile(path) as bundle:
        metadata = json.loads(bundle.read("manifest.json"))["metadata"]
        if metadata.get("packageKind") != "TIANSHU_RESOURCE_TRANSFER":
            raise ValueError(f"{path.name} 不是离线迁移包")
        projects = [info for info in bundle.infolist() if info.filename.startswith("resources/PROJECT/") and not info.is_dir()]
        if len(projects) != 1 or projects[0].file_size > 1024 * 1024:
            raise ValueError(f"{path.name} 必须包含一个项目定义")
        code = json.loads(bundle.read(projects[0]))["configuration"]["projectCode"]
        if not isinstance(code, str) or not code.strip():
            raise ValueError(f"{path.name} 缺少项目编码")
        return {"code": code, "sha256": digest(path)}


def pack(target, inputs, records=None, restore_published=False):
    if not target.name.endswith(".project-set.zip") or not 1 <= len(inputs) <= 16:
        raise ValueError("指定 .project-set.zip 输出文件及 1 至 16 个单项目包")
    if sum(path.stat().st_size for path in inputs) > LIMIT:
        raise ValueError("项目合集超过 512 MiB")
    members = [describe(path) for path in inputs]
    if len({row["code"] for row in members}) != len(inputs):
        raise ValueError("项目编码重复")
    if restore_published:
        for member in members:
            member["restorePublished"] = True
    if records:
        if not set(records).issubset({member["code"] for member in members}):
            raise ValueError("名单样例的项目不在合集中")
        for member in members:
            if member["code"] in records:
                member["records"] = records[member["code"]]
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=target.parent) as work:
        archive = Path(work) / "collection.zip"
        with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_STORED) as bundle:
            header = {"format": "TIANSHU_PROJECT_SET_V1", "projects": members}
            bundle.writestr(zipfile.ZipInfo("project-set.json"), json.dumps(header, ensure_ascii=False).encode())
            for i, source in enumerate(inputs, 1):
                with bundle.open(zipfile.ZipInfo(f"projects/{i}.zip"), "w") as out, source.open("rb") as stream:
                    shutil.copyfileobj(stream, out, length=1024 * 1024)
        if archive.stat().st_size > LIMIT:
            raise ValueError("项目合集超过 512 MiB")
        with zipfile.ZipFile(archive) as bundle:
            for i, member in enumerate(members, 1):
                with bundle.open(f"projects/{i}.zip") as stream:
                    h = hashlib.sha256()
                    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                        h.update(chunk)
                    if h.hexdigest() != member["sha256"]:
                        raise ValueError("源文件在打包期间发生变化")
        archive.replace(target)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("inputs", nargs="+", type=Path)
    parser.add_argument("--list-records", type=Path, help="按项目编码明确指定的演示名单记录 JSON")
    parser.add_argument("--restore-published", action="store_true", help="允许在导入预检后选择回归并恢复源端发布状态")
    args = parser.parse_args()
    pack(args.output, args.inputs, json.loads(args.list_records.read_text("utf8")) if args.list_records else None, args.restore_published)
    print(f"{digest(args.output)}  {args.output.name}")
