# 身份证、字符串与正则函数模板

这些函数在“函数管理”中按 `GLOBAL` + `SCRIPT` 新建。引擎已提供受控桥接函数，因此脚本仍可在 QLExpress isolation 沙箱中执行，无需开放任意 Java 类或方法。

## 1. `idCardGender`：身份证提取性别

- 返回类型：`NUMBER`
- 参数：`idCard`（`STRING`）
- 脚本：

```ql
return idCardGenderValue(idCard);
```

返回：女 `0`、男 `1`、未知 `-1`。支持 15/18 位中国居民身份证；格式或出生日期无效时返回 `-1`。

## 2. `idCardBirthDate`：身份证提取出生年月日

- 返回类型：`OBJECT`
- 参数：`idCard`（`STRING`）
- 脚本：

```ql
return idCardBirthDateValue(idCard);
```

返回 `java.sql.Date`；格式或出生日期无效时返回 `null`。

## 3. `strLeft`：字符串提取前 N 位

- 返回类型：`STRING`
- 参数：`text`（`STRING`）、`length`（`NUMBER`）
- 脚本：

```ql
return leftStringValue(text, length);
```

`length` 大于等于字符串长度时返回全部字符；小于等于 `0` 返回空字符串；`text` 或 `length` 为空返回 `null`。

## 4. `strRight`：字符串提取后 N 位

- 返回类型：`STRING`
- 参数：`text`（`STRING`）、`length`（`NUMBER`）
- 脚本：

```ql
return rightStringValue(text, length);
```

`length` 大于等于字符串长度时返回全部字符；小于等于 `0` 返回空字符串；`text` 或 `length` 为空返回 `null`。

## 5. `idCardAge`：身份证计算年龄

- 返回类型：`NUMBER`
- 参数：`idCard`（`STRING`）、`currentTime`（`DATE`，可选）、`mode`（`STRING`，可选）
- 脚本：

```ql
return idCardAgeValue(idCard, currentTime, mode);
```

`currentTime` 未传时使用系统当前日期；可传 `Date`、秒/毫秒时间戳，或 `yyyy-MM-dd`、`yyyy/MM/dd`、`yyyyMMdd`、带时分秒的日期字符串。

- `mode=YEAR`、`0`、`按年相减`：当前年份 - 出生年份。
- `mode=YMD`、`EXACT`、`1`、`按年月日相减` 或未传：按完整年月日计算周岁。
- 身份证、出生日期或当前日期无效，或出生日期晚于当前日期：返回 `-1`。

示例：

```ql
idCardAge(idCard)
idCardAge(idCard, '2026-07-06', 'YEAR')
idCardAge(idCard, '2026-07-06', 'YMD')
```

## 6. `regexMatch`：字符串匹配正则表达式

- 返回类型：`NUMBER`
- 参数：`text`（`STRING`）、`regex`（`STRING`）
- 脚本：

```ql
return regexMatchValue(text, regex);
```

完整匹配返回 `1`，不匹配、空值或非法正则返回 `0`。需要包含匹配时，在正则前后显式添加 `.*`。

```ql
regexMatch(mobileNo, '^1[3-9]\\d{9}$')
regexMatch(text, '.*test.*')
```
