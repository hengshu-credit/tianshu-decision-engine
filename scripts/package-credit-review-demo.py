"""Build a native transfer package for the HENGSHUCREDIT approval-tail demonstration."""

import argparse
import copy
import hashlib
import json
from pathlib import Path
import uuid
import zipfile

BASE = "CREDIT_ADMISSION"
PLUS = "CREDIT_ADMISSION_PLUS"
EXP = "CREDIT_LIMIT_30_70"
TIERS = ["未授信", "标准", "优享", "待复核"]
TERMS = [0, 3, 6, 9, 12, 18, 24, 36]
CHILDREN = [
    (
        "POST_TIER_TABLE",
        "授信结论分层",
        "TABLE",
        "post_credit_tier",
        "授信结论层级",
        "STRING",
    ),
    (
        "POST_REVIEW_CROSS",
        "复核周期交叉表",
        "CROSS",
        "post_review_days",
        "复核间隔天数",
        "NUMBER",
    ),
    (
        "POST_FOLLOW_SCORE",
        "贷后跟踪评分",
        "SCORE_ADV",
        "post_follow_score",
        "贷后跟踪分",
        "NUMBER",
    ),
    (
        "POST_FOLLOW_SCRIPT",
        "贷后复核动作",
        "SCRIPT",
        "post_follow_action",
        "贷后复核动作",
        "STRING",
    ),
    (
        "POST_LIMIT_GUARD",
        "最终额度非正拒绝",
        "TABLE",
        "post_limit_blocked",
        "最终额度非正",
        "BOOLEAN",
    ),
]


def encode(value):
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    ).encode()


def digest(content):
    return hashlib.sha256(content).hexdigest()


def uid(label):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "tianshu:credit-tail:" + label))


def literal(value):
    kind = (
        "BOOLEAN"
        if isinstance(value, bool)
        else "NUMBER" if isinstance(value, (int, float)) else "STRING"
    )
    text = str(value).lower() if isinstance(value, bool) else str(value)
    return dict(kind="LITERAL", value=text, valueType=kind)


def group(*leaves):
    return dict(type="group", op="AND", children=list(leaves))


class CreditDemo:
    def __init__(self, archive):
        with zipfile.ZipFile(archive) as z:
            self.manifest = json.loads(z.read("manifest.json"))
            self.files = {
                c["path"]: z.read(c["path"]) for c in self.manifest["components"]
            }
        manifest = dict(self.manifest)
        declared = manifest.pop("artifactDigest")
        if declared != digest(encode(manifest)):
            raise ValueError("源包清单摘要不一致")
        for part in manifest["components"]:
            blob = self.files[part["path"]]
            if len(blob) != part["size"] or digest(blob) != part["digest"]:
                raise ValueError("源组件校验失败：" + part["path"])
        self.rows = {
            r["key"]: r
            for n, b in self.files.items()
            if n.startswith("resources/")
            for r in [json.loads(b)]
        }
        self.project = self.find("PROJECT", "projectCode", "HENGSHUCREDIT")
        self.vars = {}
        for row in self.rows.values():
            if row["key"].startswith("VARIABLE:"):
                code = row["configuration"]["varCode"]
                if code in self.vars:
                    raise ValueError(f"演示变量 {code} 在源包中不唯一，请核对资源 ID")
                self.vars[code] = row
        self.parent = self.find("RULE", "ruleCode", BASE)
        self.rules = {}
        self.changed = set()

    def find(self, typ, attr, value):
        found = [
            r
            for r in self.rows.values()
            if r["key"].startswith(typ + ":") and r["configuration"].get(attr) == value
        ]
        if len(found) != 1:
            raise ValueError(f"需要唯一的 {typ} {value}")
        return found[0]

    def add(self, typ, config):
        number = (
            max(int(k.split(":")[1]) for k in self.rows if k.startswith(typ + ":")) + 1
            if any(k.startswith(typ + ":") for k in self.rows)
            else 1
        )
        config["id"] = number
        row = dict(
            key=f"{typ}:{number}",
            configuration=config,
            pageSettings=[],
            references=[],
            versionReferences=[],
            requiredEnvironmentFields=[],
        )
        self.rows[row["key"]] = row
        self.changed.add(row["key"])
        return row

    def ref(self, owner, path, target):
        c = target["configuration"]
        owner["references"].append(
            dict(
                path=path,
                targetKey=target["key"],
                targetCode=c.get("varCode")
                or c.get("ruleCode")
                or c.get("projectCode"),
                targetName=c.get("varLabel")
                or c.get("ruleName")
                or c.get("projectName"),
                external=False,
                childPath=None,
            )
        )

    def operand(self, code):
        c = self.vars[code]["configuration"]
        return dict(
            kind="REFERENCE",
            refType="VARIABLE",
            refId=c["id"],
            code=code,
            value=c["scriptName"],
            label=c["varLabel"],
            valueType=c["varType"],
            resolved=True,
            relativePath="",
            sourceType="variable",
            varSource=c["varSource"],
        )

    def field(self, code):
        v = self.operand(code)
        return dict(
            varCode=code,
            varLabel=v["label"],
            varType=v["valueType"],
            _varId=v["refId"],
            _refType="VARIABLE",
            operand=v,
        )

    def condition(self, code, op, value):
        return dict(
            type="leaf",
            leftOperand=self.operand(code),
            operator=op,
            rightOperand=literal(value),
        )

    def assign(self, code, value):
        return dict(type="assign", targetOperand=self.operand(code), valueOperand=value)

    def table(self, output, entries):
        return dict(
            hitPolicy="FIRST",
            conditions=[],
            actions=[],
            rules=[
                dict(
                    conditionRoot=group(*conditions),
                    actions=[self.assign(output, literal(value))],
                )
                for conditions, value in entries
            ],
        )

    def design(self, index, output):
        cond = self.condition
        if index == 0:
            return self.table(
                output,
                [
                    ([cond("result", "!=", "0")], "未授信"),
                    ([cond("credit_limit", ">=", 20000)], "优享"),
                    ([cond("credit_limit", ">", 0)], "标准"),
                    ([cond("credit_limit", "<=", 0)], "待复核"),
                ],
            ), ["result", "credit_limit"]
        if index == 1:
            cells = [
                [
                    (
                        0
                        if tier in ("未授信", "待复核") or not term
                        else (
                            (60 if tier == "优享" else 30)
                            if term >= 12
                            else (30 if tier == "优享" else 15)
                        )
                    )
                    for term in TERMS
                ]
                for tier in TIERS
            ]
            return dict(
                rowVar=self.field("post_credit_tier"),
                colVar=self.field("credit_period"),
                resultVar=self.field(output),
                rowHeaders=TIERS,
                colHeaders=TERMS,
                cells=cells,
                rowHeaderOperands=list(map(literal, TIERS)),
                colHeaderOperands=list(map(literal, TERMS)),
                cellOperands=[list(map(literal, row)) for row in cells],
            ), ["post_credit_tier", "credit_period"]
        if index == 2:
            dims = []
            for code, label, threshold, high, low in [
                ("credit_limit", "额度维度", 20000, 40, 20),
                ("credit_period", "期限维度", 12, 30, 10),
            ]:
                dims.append(
                    dict(
                        **self.field(code),
                        dimLabel=label,
                        dimensionWeight=1,
                        rules=[
                            dict(
                                conditions=[
                                    cond("result", "==", "0"),
                                    cond(code, ">=", threshold),
                                ],
                                score=high,
                            ),
                            dict(
                                conditions=[
                                    cond("result", "==", "0"),
                                    cond(code, ">", 0),
                                ],
                                score=low,
                            ),
                        ],
                    )
                )
            return dict(
                initialScore=0,
                resultVar=self.field(output),
                dimensionGroups=[
                    dict(groupLabel="授信敞口", groupWeight=1, dimensions=dims)
                ],
                thresholds=[],
            ), ["result", "credit_limit", "credit_period"]
        if index == 3:
            inputs = ["post_review_days", "post_follow_score"]
            script = 'post_follow_action = "不启动贷后复核";\nif (post_review_days != null && post_review_days > 0) {\n    if (post_follow_score >= 60) { post_follow_action = "重点复核"; } else { post_follow_action = "常规复核"; }\n}\nreturn {"post_follow_action": post_follow_action};'
            return (
                dict(
                    script=script,
                    scriptVarRefs=[
                        dict(
                            refCode=v,
                            varId=self.operand(v)["refId"],
                            refType="VARIABLE",
                        )
                        for v in [*inputs, output]
                    ],
                ),
                inputs,
            )
        return self.table(
            output,
            [
                ([cond("credit_limit", "<=", 0)], True),
                ([cond("credit_limit", ">", 0)], False),
            ],
        ), ["credit_limit"]

    def fields(self, codes, rule):
        return [
            dict(
                definitionId=rule,
                refType="VARIABLE",
                varId=self.operand(code)["refId"],
                fieldName=code,
                scriptName=code,
                fieldLabel=self.operand(code)["label"],
                fieldType=self.operand(code)["valueType"],
                sortOrder=i,
                status=1,
            )
            for i, code in enumerate(codes)
        ]

    def model_references(self, rule, obj, path):
        if isinstance(obj, dict):
            for k, v in obj.items():
                dest = path + "/" + k
                if k in ("refId", "_varId", "varId") and isinstance(v, int):
                    self.ref(rule, dest, self.rows["VARIABLE:" + str(v)])
                elif k == "ruleId" and isinstance(v, int):
                    self.ref(rule, dest, self.rows["RULE:" + str(v)])
                else:
                    self.model_references(rule, v, dest)
        elif isinstance(obj, list):
            for n, item in enumerate(obj):
                self.model_references(rule, item, path + "/" + str(n))

    @staticmethod
    def testcase(title, inputs, expected, n):
        return dict(
            scenarioName=title,
            description="衡枢真信授信收尾回归",
            requestJson=encode(dict(params=inputs)).decode(),
            responseJson=encode(
                dict(data=dict(success=True, result=expected, errorMessage=None))
            ).decode(),
            expectationJson='{"version":1,"mode":"SNAPSHOT"}',
            includeInDoc=1,
            sortOrder=n,
            status=1,
        )

    def configure_income_cache(self):
        # The provider report has its own business identity. Ordinary orders still query by application ID.
        code = "income_report_id"
        if code in self.vars:
            raise ValueError("收入报告编号已存在，请检查输入包")
        var = self.add(
            "VARIABLE",
            dict(
                scope="PROJECT",
                projectId=self.project["configuration"]["id"],
                varCode=code,
                varLabel="收入报告编号",
                scriptName=code,
                varType="STRING",
                varSource="INPUT",
                recordResult=False,
                status=1,
                sortOrder=0,
                options=[],
                description="同一申请人的同一份供应商收入报告可复用。留空时按当前进件号查询。",
            ),
        )
        self.ref(var, "/projectId", self.project)
        self.vars[code] = var
        api = self.find("EXTERNAL_API", "apiCode", "HS_INCOME")
        config = api["configuration"]
        fields = config["executionConfig"]["requestFields"]
        matches = [
            (i, f)
            for i, f in enumerate(fields)
            if f["location"] == "JSON" and f["path"] == "request_id"
        ]
        if len(matches) != 1:
            raise ValueError("收入接口缺少唯一的供应商查询编号")
        index, field = matches[0]
        old = field["value"]
        if old.get("refId") != self.vars["request_id"]["configuration"]["id"]:
            raise ValueError("收入接口原有进件号映射已变化")
        prefix = f"/executionConfig/requestFields/{index}"
        api["references"] = [
            r for r in api["references"] if r["path"] != prefix + "/value/refId"
        ]
        field["defaultValue"] = old
        self.ref(api, prefix + "/defaultValue/refId", self.vars["request_id"])
        field["value"] = self.operand(code)
        self.ref(api, prefix + "/value/refId", var)
        config.update(
            responseCacheSeconds=300,
            responseCacheRedisEnabled=1,
            cacheKeyConfig=encode(
                dict(components=[dict(path="__apiFields." + f["id"]) for f in fields])
            ).decode(),
        )
        self.changed.add(api["key"])
        parent = self.parent["configuration"]
        n = len(parent["inputFieldsJson"])
        extra = self.fields([code], parent["id"])[0]
        extra["sortOrder"] = n
        parent["inputFieldsJson"].append(extra)
        self.ref(self.parent, f"/inputFieldsJson/{n}/varId", var)

    def add_children(self):
        pid = self.project["configuration"]["id"]
        cases = [
            [
                ({"result": "1", "credit_limit": 20000}, "未授信"),
                ({"result": "0", "credit_limit": 19999}, "标准"),
                ({"result": "0", "credit_limit": 20000}, "优享"),
                ({"result": "0", "credit_limit": 0}, "待复核"),
            ],
            [
                ({"post_credit_tier": "未授信", "credit_period": 0}, 0),
                ({"post_credit_tier": "标准", "credit_period": 6}, 15),
                ({"post_credit_tier": "优享", "credit_period": 12}, 60),
            ],
            [
                ({"result": "1", "credit_limit": 20000, "credit_period": 12}, 0),
                ({"result": "0", "credit_limit": 19999, "credit_period": 6}, 30),
                ({"result": "0", "credit_limit": 20000, "credit_period": 12}, 70),
            ],
            [
                ({"post_review_days": 0, "post_follow_score": 70}, "不启动贷后复核"),
                ({"post_review_days": 15, "post_follow_score": 59}, "常规复核"),
                ({"post_review_days": 60, "post_follow_score": 60}, "重点复核"),
            ],
            [
                ({"credit_limit": -1}, True),
                ({"credit_limit": 0}, True),
                ({"credit_limit": 1}, False),
            ],
        ]
        for i, (code, name, typ, output, label, vtype) in enumerate(CHILDREN):
            if output in self.vars or any(
                x["configuration"].get("ruleCode") == code for x in self.rows.values()
            ):
                raise ValueError("目标代码已存在，请检查版本：" + code)
            var = self.add(
                "VARIABLE",
                dict(
                    scope="PROJECT",
                    projectId=pid,
                    varCode=output,
                    varLabel=label,
                    scriptName=output,
                    varType=vtype,
                    varSource="COMPUTED",
                    recordResult=True,
                    status=1,
                    description=name + "的受管输出",
                    sortOrder=0,
                    options=[],
                ),
            )
            self.ref(var, "/projectId", self.project)
            self.vars[output] = var
            design, inputs = self.design(i, output)
            r = self.add(
                "RULE",
                dict(
                    scope="PROJECT",
                    projectId=pid,
                    projectCode="HENGSHUCREDIT",
                    projectName="衡枢真信",
                    ruleCode=code,
                    ruleName=name,
                    modelType=typ,
                    status=0,
                    currentVersion=1,
                    publishedVersion=None,
                    description="授信审批收尾演示 · " + name,
                ),
            )
            c = r["configuration"]
            rid = c["id"]
            self.rules[code] = r
            c["content"] = dict(
                definitionId=rid,
                compileStatus=0,
                scriptMode=0,
                modelJson=encode(design).decode(),
            )
            self.ref(r, "/projectId", self.project)
            for key, items in [
                ("inputFieldsJson", inputs),
                ("outputFieldsJson", [output]),
            ]:
                c[key] = self.fields(items, rid)
                for n, v in enumerate(items):
                    self.ref(r, f"/{key}/{n}/varId", self.vars[v])
            self.model_references(r, design, "/content/modelJson/@json")
            r["pageSettings"] = [
                self.testcase(name + " · " + str(n + 1), p, {output: v}, n)
                for n, (p, v) in enumerate(cases[i])
            ]

    def call(self, index):
        code, name, typ, output, *_ = CHILDREN[index]
        return dict(
            type="rule-call",
            ruleId=self.rules[code]["configuration"]["id"],
            ruleCode=code,
            ruleName=name,
            modelType=typ,
            versionMode="LATEST",
            enableOutputMapping=True,
            outputField=output,
            targetOperand=self.operand(output),
        )

    def extend(self, r, plus):
        c = r["configuration"]
        j = json.loads(c["content"]["modelJson"])
        lf = j["logicflow"]
        ln = {n["id"]: n for n in lf["nodes"]}
        ends = [n for n in j["nodes"] if n["type"] == "end"]
        if not ends or any(
            n.get("actionData")
            or n.get("terminationScope", "CURRENT_RULE") != "CURRENT_RULE"
            for n in ends
        ):
            raise ValueError("终点包含独立动作或不同终止范围，不能自动合流")
        first = len(j["nodes"])
        first_edge = len(j["edges"])
        x = max(n["x"] for n in ends) + 230
        y = 300
        task_proto = next(n for n in lf["nodes"] if n["type"] == "script-task")
        decision_proto = next(
            ln[n["id"]] for n in j["nodes"] if n["type"] == "decision"
        )
        end_proto = copy.deepcopy(ln[ends[-1]["id"]])
        for n in ends:
            n["type"] = "task"
            n.pop("terminationScope", None)
            v = ln[n["id"]]
            v["type"] = "script-task"
            v["properties"] = dict(
                task_proto["properties"],
                nodeName=n["name"],
                nodeCode=n["id"],
                actionData=[],
            )

        def node(label, typ, nx, ny, actions=None):
            key = uid(c["ruleCode"] + "/" + label)
            actions = actions or []
            n = dict(
                id=key,
                type=typ,
                name=label,
                x=nx,
                y=ny,
                actionData=actions,
                gatewayDirection="Diverging",
            )
            if typ == "end":
                n["terminationScope"] = "CURRENT_RULE"
            v = copy.deepcopy(
                {"task": task_proto, "decision": decision_proto, "end": end_proto}[typ]
            )
            v.update(id=key, x=nx, y=ny)
            v["properties"].update(
                nodeName=label, nodeCode=key, actionData=copy.deepcopy(actions)
            )
            j["nodes"].append(n)
            lf["nodes"].append(v)
            ln[key] = v
            return n

        def connect(src, dst, name="", condition=None, preview="", priority=10):
            key = uid(c["ruleCode"] + src["id"] + dst["id"])
            e = dict(
                id=key,
                source=src["id"],
                target=dst["id"],
                name=name,
                conditionConfig=condition,
                conditionExpression=preview,
                priority=priority,
            )
            j["edges"].append(e)
            a = dict(
                x=src["x"] + ln[src["id"]]["properties"].get("width", 80) / 2,
                y=src["y"],
            )
            b = dict(
                x=dst["x"] - ln[dst["id"]]["properties"].get("width", 80) / 2,
                y=dst["y"],
            )
            half = (a["x"] + b["x"]) / 2
            v = dict(
                id=key,
                type="polyline",
                sourceNodeId=src["id"],
                targetNodeId=dst["id"],
                sourceAnchorId=src["id"] + "_1",
                targetAnchorId=dst["id"] + "_3",
                startPoint=a,
                endPoint=b,
                pointsList=[a, dict(x=half, y=a["y"]), dict(x=half, y=b["y"]), b],
                properties=dict(
                    name=name,
                    conditionConfig=copy.deepcopy(condition),
                    conditionExpression=preview,
                    priority=priority,
                ),
            )
            if name:
                v["text"] = dict(value=name, x=half, y=(a["y"] + b["y"]) / 2)
            lf["edges"].append(v)

        frontier = ends
        for i in range(4):
            nextnode = node(CHILDREN[i][1], "task", x, y, [self.call(i)])
            for src in frontier:
                connect(src, nextnode)
            frontier = [nextnode]
            x += 220
        if plus:
            check = node("核对最终授信额度", "task", x, y, [self.call(4)])
            connect(frontier[0], check)
            x += 210
            gate = node("额度是否非正", "decision", x, y)
            connect(check, gate)
            x += 220
            reject = node(
                "额度非正，拒绝并清零",
                "task",
                x,
                y + 190,
                [
                    self.assign(v, literal(val))
                    for v, val in [
                        ("result", "1"),
                        ("credit_limit", 0),
                        ("credit_period", 0),
                        ("priced_rate", 0),
                    ]
                ],
            )
            allowed = node("是否已通过审批", "decision", x, y)
            connect(
                gate,
                reject,
                "是",
                group(self.condition("post_limit_blocked", "==", True)),
                "post_limit_blocked == true",
                1,
            )
            connect(
                gate,
                allowed,
                "否",
                group(self.condition("post_limit_blocked", "==", False)),
                "post_limit_blocked == false",
                2,
            )
            x += 220
            inc = dict(
                kind="OPERATION",
                terms=[
                    dict(operand=self.operand("credit_limit")),
                    dict(operator="+", operand=literal(1000)),
                ],
                valueType="NUMBER",
            )
            up = node(
                "通过件额度加 1000", "task", x, y, [self.assign("credit_limit", inc)]
            )
            connect(
                allowed,
                up,
                "已通过",
                group(self.condition("result", "==", "0")),
                'result == "0"',
                1,
            )
            x += 220
            final = node("返回最终授信结论", "end", x, y)
            connect(up, final)
            connect(reject, final)
            connect(
                allowed,
                final,
                "保持原结论",
                group(self.condition("result", "!=", "0")),
                'result != "0"',
                2,
            )
        else:
            final = node("返回最终授信结论", "end", x, y)
            connect(frontier[0], final)
        for suffix, obj, start in [
            ("nodes", j["nodes"], first),
            ("logicflow/nodes", lf["nodes"], first),
            ("edges", j["edges"], first_edge),
            ("logicflow/edges", lf["edges"], first_edge),
        ]:
            for n in range(start, len(obj)):
                self.model_references(
                    r, obj[n], f"/content/modelJson/@json/{suffix}/{n}"
                )
        c["content"]["modelJson"] = encode(j).decode()
        c["content"]["compileStatus"] = 0
        for field in (
            "compiledScript",
            "compileMessage",
            "compileTime",
            "compiledType",
        ):
            c["content"].pop(field, None)
        for field in [v[3] for v in CHILDREN[: 5 if plus else 4]]:
            n = len(c["outputFieldsJson"])
            f = self.fields([field], c["id"])[0]
            f["sortOrder"] = n
            c["outputFieldsJson"].append(f)
            self.ref(r, f"/outputFieldsJson/{n}/varId", self.vars[field])
        for case in r["pageSettings"]:
            if not case.get("responseJson"):
                continue
            response = json.loads(case["responseJson"])
            res = response.get("data", {}).get("result")
            if not isinstance(res, dict) or "result" not in res:
                continue
            passed = str(res["result"]) == "0"
            amount = res.get("credit_limit") or 0
            term = res.get("credit_period") or 0
            tier = (
                "未授信"
                if not passed
                else "优享" if amount >= 20000 else "标准" if amount > 0 else "待复核"
            )
            days = (
                0
                if tier not in ["标准", "优享"] or not term
                else (
                    (60 if tier == "优享" else 30)
                    if term >= 12
                    else (30 if tier == "优享" else 15)
                )
            )
            score = (
                (
                    (40 if amount >= 20000 else 20 if amount > 0 else 0)
                    + (30 if term >= 12 else 10 if term > 0 else 0)
                )
                if passed
                else 0
            )
            res.update(
                post_credit_tier=tier,
                post_review_days=days,
                post_follow_score=score,
                post_follow_action=(
                    "不启动贷后复核"
                    if days == 0
                    else "重点复核" if score >= 60 else "常规复核"
                ),
            )
            if plus:
                res["post_limit_blocked"] = amount <= 0
                if amount <= 0:
                    res.update(
                        result="1", credit_limit=0, credit_period=0, priced_rate=0
                    )
                elif passed:
                    res["credit_limit"] = amount + 1000
            case["responseJson"] = encode(response).decode()
        self.changed.add(r["key"])

    def build(self):
        if any(
            row["configuration"].get("ruleCode") == PLUS
            or row["configuration"].get("experimentCode") == EXP
            for row in self.rows.values()
        ):
            raise ValueError("已有随机实验或加额版，请使用未改造的授信项目导出包")
        self.configure_income_cache()
        self.add_children()
        origin = copy.deepcopy(self.parent)
        c = origin["configuration"]
        var = self.add("RULE", copy.deepcopy(c))
        new_id = var["configuration"]["id"]
        var.update(
            {
                k: copy.deepcopy(origin[k])
                for k in (
                    "references",
                    "versionReferences",
                    "pageSettings",
                    "requiredEnvironmentFields",
                )
            }
        )
        v = var["configuration"]
        v.update(
            ruleCode=PLUS,
            ruleName="授信准入 · 额度保护与加额",
            id=new_id,
            currentVersion=1,
            publishedVersion=None,
            status=0,
        )
        v["content"]["definitionId"] = new_id
        v["content"].pop("id", None)
        for field in v["inputFieldsJson"] + v["outputFieldsJson"]:
            field["definitionId"] = new_id
            field.pop("id", None)
        self.extend(self.parent, False)
        self.extend(var, True)
        exp = self.add(
            "EXPERIMENT",
            dict(
                projectId=self.project["configuration"]["id"],
                projectCode="HENGSHUCREDIT",
                experimentCode=EXP,
                experimentName="授信额度 30/70 随机对照",
                description="30% 基线授信，70% 最终额度保护与通过件加额",
                routingMode="RATIO",
                testRoutingMode="RATIO",
                testExclusive=1,
                routingErrorPolicy="PAUSE",
                requestKeyPath=encode(self.operand("request_id")).decode(),
                inputValidationConfig="{}",
                status=0,
                groups=[],
            ),
        )
        self.ref(exp, "/projectId", self.project)
        self.ref(exp, "/requestKeyPath/@json/refId", self.vars["request_id"])
        for i, (typ, rule, ratio) in enumerate(
            [("CHAMPION", self.parent, 30), ("CHALLENGER", var, 70)]
        ):
            rc = rule["configuration"]
            exp["configuration"]["groups"].append(
                dict(
                    groupType=typ,
                    groupCode="BASE_30" if i == 0 else "PLUS_70",
                    groupName="原额度" if i == 0 else "保护并加额",
                    ruleId=rc["id"],
                    ruleCode=rc["ruleCode"],
                    versionMode="LATEST",
                    trafficRatio=ratio,
                    status=1,
                    sortOrder=i,
                    invokeExternalSource=1,
                    conditionExpression="",
                    conditionValue="",
                )
            )
            self.ref(exp, f"/groups/{i}/ruleId", rule)
        return self

    def write(self, target):
        for key in self.changed:
            typ, num = key.split(":")
            self.files[f"resources/{typ}/{num}.json"] = encode(self.rows[key])
        for r in self.rows.values():
            for ref in r["references"]:
                if ref.get("external"):
                    continue
                dest = self.rows[ref["targetKey"]]
                at = r["configuration"]
                for part in ref["path"].strip("/").split("/"):
                    at = (
                        json.loads(at)
                        if part == "@json"
                        else at[int(part)] if isinstance(at, list) else at[part]
                    )
                if not ref.get("childPath") and str(at) != str(
                    dest["configuration"]["id"]
                ):
                    raise ValueError(f"引用未按 ID 绑定 {r['key']} {ref['path']}")
        manifest = dict(self.manifest)
        manifest.pop("artifactDigest")
        types = {c["path"]: c["mediaType"] for c in manifest["components"]}
        manifest["components"] = [
            dict(
                path=n,
                mediaType=types.get(n, "application/json"),
                size=len(b),
                digest=digest(b),
            )
            for n, b in sorted(self.files.items())
        ]
        manifest["artifactDigest"] = digest(encode(manifest))
        with zipfile.ZipFile(target, "x", zipfile.ZIP_DEFLATED) as z:
            z.writestr("manifest.json", encode(manifest))
            for name, blob in sorted(self.files.items()):
                z.writestr(name, blob)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    CreditDemo(args.source).build().write(args.output)
