"""Create installation-specific mock credentials. The supplier receives only its own key file."""
import fcntl
import io
import json
import os
import secrets
from pathlib import Path

import paramiko
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa

STATE = Path(os.environ.get("EXAMPLE_STATE_DIR", "/state"))


def read_supplier_keys():
    return json.loads((STATE / "supplier.json").read_bytes())


def write_private(path, value):
    tmp = path.with_suffix(".new")
    with tmp.open("w", encoding="utf8") as out:
        json.dump(value, out)
        out.flush()
        os.fsync(out.fileno())
    os.replace(tmp, path)


def provision():
    import pymysql

    os.umask(0o077)
    STATE.mkdir(mode=0o700, parents=True, exist_ok=True)
    with (STATE / ".lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        path = STATE / "installation.json"
        public_dir = Path(os.environ.get("SUPPLIER_STATE_DIR", "/supplier-state"))
        supplier_file = public_dir / "supplier.json"
        if not path.exists() and supplier_file.exists():
            raise RuntimeError("供应商凭据卷已存在，请恢复配套的安装凭据卷")
        if not path.exists():
            keys = {n: secrets.token_urlsafe(32) for n in
                    ("court", "black", "late", "multi", "income", "credit", "pboc", "sftp", "engine")}
            keys["fraud"] = rsa.generate_private_key(public_exponent=65537, key_size=2048).private_bytes(
                serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()).decode()
            host = paramiko.RSAKey.generate(3072)
            pem = io.StringIO()
            host.write_private_key(pem)
            keys["sftp_host_key"] = pem.getvalue()
            keys.update(db_user="hs_example_" + secrets.token_hex(8), db_password=secrets.token_urlsafe(32),
                        credit_api=secrets.token_urlsafe(32), host_public=host.get_name() + " " + host.get_base64())
            write_private(path, keys)
        keys = json.loads(path.read_bytes())
        supplier_keys = {k: v for k, v in keys.items() if k not in ("db_user", "db_password", "credit_api", "host_public")}
        if supplier_file.exists() and json.loads(supplier_file.read_bytes()) != supplier_keys:
            raise RuntimeError("供应商凭据卷与安装凭据不匹配")
        host = os.environ["MYSQL_HOST"]
        port = int(os.environ.get("MYSQL_PORT", 3306))
        # The demo's parameterized SELECTs use literal synthetic rows. USAGE suffices.
        with pymysql.connect(host=host, port=port, user=os.environ["MYSQL_ADMIN_USER"],
                             password=os.environ["MYSQL_ADMIN_PASSWORD"], autocommit=True) as admin, admin.cursor() as c:
            c.execute("SELECT @@mandatory_roles")
            if c.fetchone()[0]:
                raise RuntimeError("MySQL mandatory roles must be reviewed before creating the demo reader")
            c.execute("CREATE USER IF NOT EXISTS %s@'%%' IDENTIFIED BY %s",
                      (keys["db_user"], keys["db_password"]))
            c.execute("SHOW GRANTS FOR %s@'%%'", (keys["db_user"],))
            grants = [row[0] for row in c.fetchall()]
            if len(grants) != 1 or not grants[0].startswith("GRANT USAGE ON *.* TO "):
                raise RuntimeError("Existing demo reader has unexpected privileges")
        with pymysql.connect(host=host, port=port, user=keys["db_user"], password=keys["db_password"],
                             database="information_schema") as reader, reader.cursor() as c:
            c.execute("SELECT 1")
            assert c.fetchone() == (1,)
        public_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
        if not supplier_file.exists():
            write_private(supplier_file, supplier_keys)
        print("演示凭据与数据库只读账号已就绪")


if __name__ == "__main__":
    provision()
