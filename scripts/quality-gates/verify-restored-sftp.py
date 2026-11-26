"""Cold-read an isolated SFTP restore and compare it with its volume backup and restored DB."""
import argparse
import base64
import hashlib
import io
import json
import os
import re
import socket
import stat
import sys
import tarfile
from pathlib import PurePosixPath

import paramiko


def digest(stream):
    sha = hashlib.sha256()
    for block in iter(lambda: stream.read(256 * 1024), b""):
        sha.update(block)
    return sha.hexdigest()


def fingerprint(key):
    return "SHA256:" + base64.b64encode(hashlib.sha256(key.asbytes()).digest()).decode().rstrip("=")


def archive_inventory(path, expected_fingerprint):
    files = {}
    directories = set()
    key_found = False
    with tarfile.open(path, "r|*") as archive:
        for entry in archive:
            relative = PurePosixPath(entry.name)
            if relative.is_absolute() or ".." in relative.parts:
                raise ValueError("Invalid archive path")
            if relative == PurePosixPath("host-key"):
                if key_found or not entry.isfile() or not 0 < entry.size <= 65536:
                    raise ValueError("Invalid or duplicate backup host-key")
                with archive.extractfile(entry) as stream:
                    private_key = stream.read().decode("ascii")
                for key_type in (paramiko.ECDSAKey, paramiko.Ed25519Key, paramiko.RSAKey):
                    try:
                        public_key = key_type.from_private_key(io.StringIO(private_key))
                    except paramiko.SSHException:
                        continue
                    if fingerprint(public_key) != expected_fingerprint:
                        raise ValueError("Backup host-key differs from trusted deployment")
                    key_found = True
                    break
            if not relative.parts or relative.parts[0] != "files":
                continue
            remote = "/" + "/".join(relative.parts[1:])
            if remote in files or remote in directories:
                raise ValueError("Duplicate SFTP archive member")
            if entry.isdir():
                directories.add(remote)
            elif entry.isfile():
                with archive.extractfile(entry) as source:
                    files[remote] = digest(source)
            else:
                raise ValueError("Unexpected entry type in SFTP archive")
    if not key_found or "/managed" not in directories:
        raise ValueError("Backup lacks host-key or files/managed")
    return files, directories


def read_store(sftp):
    files, directories = {}, {"/"}
    pending = ["/"]
    while pending:
        parent = pending.pop()
        for entry in sftp.listdir_attr(parent):
            if entry.filename in {".", ".."} or "/" in entry.filename:
                raise ValueError("Invalid restored directory entry")
            path = parent.rstrip("/") + "/" + entry.filename
            if stat.S_ISDIR(entry.st_mode):
                directories.add(path)
                pending.append(path)
            elif stat.S_ISREG(entry.st_mode):
                with sftp.open(path, "rb") as stream:
                    files[path] = digest(stream)
            else:
                raise ValueError("Unexpected entry type in restored SFTP")
    return files, directories


def verify_references(files, rows):
    counts = {}
    for line in rows:
        kind, raw_path, expected = line.rstrip("\r\n").split("\t")
        path = bytes.fromhex(raw_path).decode("utf-8")
        if kind in {"model", "model-version", "snapshot", "artifact"}:
            namespace = "models" if kind in {"model", "model-version"} else "packages"
            if not path.startswith("ts-file-v1:" + namespace + "/"):
                raise ValueError("Invalid restored " + kind + " reference")
            key = path.removeprefix("ts-file-v1:")
            match = re.search(r"--([a-f0-9]{64})\.(onnx|pmml|zip)$", key)
            if not match or expected not in ("", "NULL", match[1]):
                raise ValueError("Database digest differs from " + kind + " reference")
            expected = match[1]
            binding = hashlib.sha256(expected.encode("ascii")).hexdigest()
            if files.get("/managed/" + key) != binding:
                raise ValueError("Missing or mismatched restored " + kind + " binding")
            path = "objects/" + expected[:2] + "/" + expected
        if not re.fullmatch("[0-9a-f]{64}", expected) or files.get("/managed/" + path) != expected:
            raise ValueError("Missing or mismatched restored " + kind + " payload")
        counts[kind] = counts.get(kind, 0) + 1
    return counts


def main():
    args = argparse.ArgumentParser()
    for name in ("archive", "host", "user", "fingerprint"):
        args.add_argument("--" + name, required=True)
    args.add_argument("--port", type=int, required=True)
    options = args.parse_args()
    expected_files, expected_dirs = archive_inventory(options.archive, options.fingerprint)
    with socket.create_connection((options.host, options.port), timeout=15) as sock:
        with paramiko.Transport(sock) as transport:
            transport.start_client(timeout=15)
            if fingerprint(transport.get_remote_server_key()) != options.fingerprint:
                raise ValueError("Restored SSH host fingerprint differs from trusted deployment")
            transport.auth_password(options.user, os.environ["TIANSHU_RESTORE_SFTP_PASSWORD"], fallback=False)
            with paramiko.SFTPClient.from_transport(transport) as sftp:
                sftp.get_channel().settimeout(60)
                files, directories = read_store(sftp)
    if files != expected_files or directories != expected_dirs:
        raise ValueError("Restored SFTP inventory or contents differ from backup")
    print(json.dumps(dict(files=len(files), directories=len(directories), fingerprint=options.fingerprint,
                          references=verify_references(files, sys.stdin))))


if __name__ == "__main__":
    main()
