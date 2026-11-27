"""根据发布版 schema 和目标库的 mysqldump --no-data，生成仅补中文注释的 SQL。"""
import argparse
import re
import sys
import unicodedata
import zipfile
from pathlib import Path


IDENTIFIER = r"(?:`(?:``|[^`])+`|[A-Za-z_]\w*)"
CREATE_TABLE = re.compile(rf"^[ \t]*CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?({IDENTIFIER})\s*\(", re.I | re.M)
COLUMN = re.compile(rf"^(?:(?:--(?=\s)|\#)[^\n]*\n\s*|/\*.*?\*/\s*)*({IDENTIFIER})\s+", re.S)
CONSTRAINTS = {"PRIMARY", "UNIQUE", "KEY", "INDEX", "CONSTRAINT", "CHECK", "FOREIGN", "FULLTEXT", "SPATIAL"}
PREVIOUS_TABLE_COMMENTS = {
    "rule_system_asset": "'系统品牌图片'",
    "rule_ledger_type": "'台账事件类型，引用稳定ID'",
    "rule_ledger_revision_reference": "'发布版本对事件和素材的稳定 ID 引用'",
    "rule_ledger_draft_reference": "'当前私有草稿引用，防止被引用素材遭删除'",
}
STRING = r"'(?:''|\\.|[^'\\])*'"
# 跳过字符串、标识符和 SQL 注释。默认值、生成列表达式中的逗号和 COMMENT 不属于列属性。
TOKENS = re.compile(
    rf"""--(?=\s)[^\n]*|\#[^\n]*|/\*.*?\*/|{STRING}|"(?:\\.|""|[^"\\])*"|`(?:``|[^`])*`"""
    rf"""|\bCOMMENT\s*=?\s*(?P<value>{STRING})|\bPARTITION\b|[(),;]""",
    re.I | re.S,
)


def has_chinese(literal):
    return any(unicodedata.name(char, "").startswith(("CJK UNIFIED IDEOGRAPH-", "CJK COMPATIBILITY IDEOGRAPH-"))
               or char == "〇" for char in literal)


def identifier(token):
    return token[1:-1].replace("``", "`") if token.startswith("`") else token


def comment_match(definition):
    return next((m for m in TOKENS.finditer(definition) if m["value"] is not None), None)


def read_tables(sql):
    tables = {}
    offset = 0
    while head := CREATE_TABLE.search(sql, offset):
        name = identifier(head[1])
        if name in tables:
            raise ValueError(f"输入中重复定义了表 {name}，请使用单库结构")
        columns = {}
        start = head.end()
        depth = 1
        tokens = TOKENS.finditer(sql, start)
        for token in tokens:
            mark = token[0]
            if depth == 1 and mark in (",", ")"):
                definition = sql[start:token.start()].strip()
                column = COLUMN.match(definition)
                if not column:
                    raise ValueError(f"无法解析 {name} 的字段或约束：{definition[:80]}")
                if column[1].upper() not in CONSTRAINTS:
                    columns[identifier(column[1]).casefold()] = definition
                start = token.end()
                if mark == ")":
                    break
            elif mark == "(":
                depth += 1
            elif mark == ")":
                depth -= 1
        else:
            raise ValueError(f"表 {name} 的定义未结束")

        comment = ""
        partition = False
        for token in tokens:
            if token[0] == ";":
                offset = token.end()
                break
            if token[0].upper() == "PARTITION":
                partition = True
            if not partition and token["value"] is not None:
                comment = token["value"]
        else:
            raise ValueError(f"表 {name} 缺少语句结束符")
        tables[name] = (comment, columns)
    return tables


def migration(reference, current):
    if not (reference.keys() & current.keys()):
        raise ValueError("发布版 schema 与目标库结构中没有同名业务表")
    statements = []
    for table, (old_comment, old_columns) in current.items():
        if table not in reference:
            continue
        comment, columns = reference[table]
        changes = []
        if not has_chinese(old_comment) or (old_comment == PREVIOUS_TABLE_COMMENTS.get(table) and old_comment != comment):
            if not has_chinese(comment):
                raise ValueError(f"发布版缺少中文表注释：{table}")
            changes.append("COMMENT=" + comment)
        for name, original in old_columns.items():
            if name not in columns:
                continue
            old = comment_match(original)
            if old and has_chinese(old["value"]):
                continue
            new = comment_match(columns[name])
            if not new or not has_chinese(new["value"]):
                raise ValueError(f"发布版缺少中文字段注释：{table}.{name}")
            # MODIFY 必须重述完整定义，沿用目标库导出的类型、默认值、字符集及生成表达式等。
            if old:
                definition = original[:old.start("value")] + new["value"] + original[old.end("value"):]
            else:
                definition = original + " COMMENT " + new["value"]
            changes.append("MODIFY COLUMN " + definition)
        if changes:
            quoted = "`" + table.replace("`", "``") + "`"
            statements.append(f"ALTER TABLE {quoted}\n  " + ",\n  ".join(changes) + ";")
    return statements


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("schema", type=Path, help="发布包的 docker/runtime/server.jar 或源码 schema.sql")
    parser.add_argument("dump", type=Path, help="目标库的 UTF-8 mysqldump --no-data 文件")
    args = parser.parse_args()
    if args.schema.suffix == ".jar":
        with zipfile.ZipFile(args.schema) as jar:
            schema = jar.read("BOOT-INF/classes/sql/schema.sql").decode("utf-8-sig")
    else:
        schema = args.schema.read_text(encoding="utf-8-sig")
    statements = migration(read_tables(schema), read_tables(args.dump.read_text(encoding="utf-8-sig")))
    if statements:
        # mysqldump 使用反斜杠转义默认值中的换行和反斜杠，按相同语法重放列定义。
        sql = [
            "SET NAMES utf8mb4;",
            "SET @ts_comments_sql_mode = @@SESSION.sql_mode;",
            "SET SESSION sql_mode = TRIM(BOTH ',' FROM REPLACE(CONCAT(',', @ts_comments_sql_mode, ','),"
            " ',NO_BACKSLASH_ESCAPES,', ','));",
            *statements,
            "SET SESSION sql_mode = @ts_comments_sql_mode;",
        ]
        sys.stdout.buffer.write(("\n".join(sql) + "\n").encode("utf-8"))


if __name__ == "__main__":
    main()
