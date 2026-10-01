# 数据看板地图来源与处理

看板默认显示中国省级地图。中国全图、省、市、县使用本地资源；全球与境外行政区保留悬停高亮、名称提示和层级切换。

## 中国边界优先

- 中国国界、台湾省、香港特别行政区、澳门特别行政区及南海诸岛，采用 DataV GeoAtlas 中国数据。
- 不手绘、删减或简化中国界线。当前源数据的 `100000_JD` 包含 10 个断续线面片，包含南海断续线及东海有关线段，完整保留。
- 默认中国视野覆盖东经 72–137 度、北纬 2–55 度；不使用“最大陆块”算法裁掉南海诸岛或台湾。
- 全球地图中的中国几何与中国全图一致；海外目录的 CHN、TWN、HKG、MAC 不参与国家选择。
- 其他国家的全球轮廓及按需加载的行政区，在展示前扣除与国内来源中国陆地边界重叠的部分，避免境外数据反向覆盖中国边界。断续线面片仅用于地图表达，不作为陆地裁剪范围。

## 本地资源

| 文件 | 来源和处理 |
| --- | --- |
| `dashboard-china-ADM0.geojson` | DataV 中国全图，原始几何不变 |
| `dashboard-china-ADM1.geojson` | DataV 34 个省级行政区，叠加完整中国背景与断续线 |
| `dashboard-china-ADM2.geojson` | 天地图来源的公开整理版本，394 个地市级区域，叠加完整中国背景与断续线 |
| `dashboard-china-ADM3.geojson` | 同源 2891 个区县级区域，叠加完整中国背景与断续线 |
| `dashboard-china-land.geojson` | 从 DataV 中国全图中排除断续线面片，供境外数据重叠校正 |
| `dashboard-world.geojson` | 中文世界地图 239 个国家/地区要素，以 DataV 中国全图替换其中中国几何，并校正其他国家与中国的重叠 |

DataV 源文件（获取日期 2026-09-16）：

- https://geo.datav.aliyun.com/areas_v3/bound/100000.json
- https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json
- 官方工具及下载说明：https://help.aliyun.com/zh/datav/datav-6-0/user-guide/introduction-to-range-selector-features

市县及世界数据采用 `chinese-global-compliant-geodata` 的固定版本 `f5acc17`，不执行该项目代码、不添加其 npm 依赖：

- 源码：https://github.com/JayMuShui/chinese-global-compliant-geodata/tree/f5acc17
- 市县原始文件：`src/geojson/countries/as/chn/global/chn-level-2.json`、`chn-level-3.json`
- 市县数据上游标注为天地图，更新时间 2024 年 5 月；这是公开整理版本，并非实时官方接口。
- 世界原始文件：`src/geojson/globe/world.json`，上游 Surbowl/world-geo-json-zh，Unlicense。
- 整理版本许可证 MIT，保留于 [chinese-geodata-LICENSE.txt](chinese-geodata-LICENSE.txt)。
- 台湾省三级行政数据暂缺时，保留已有市县边界，并在页面注明，不能当作全量乡镇/区级数据。

## 境外行政区

`dashboard-boundaries.json` 是 geoBoundaries gbOpen 目录的本地快照。境外细分层级按需从固定版本的 `media.githubusercontent.com` 地址加载，不发送进件坐标或业务数据。

- API 及层级定义：https://www.geoboundaries.org/api.html
- 来源及署名：https://www.geoboundaries.org/
- 许可：CC BY 4.0，https://creativecommons.org/licenses/by/4.0/
- ADM0–ADM5 对应各国不同深度行政区，市、县所在层级及数据年份因国家而异。
- 境外 ADM0 优先使用本地中文世界底图；细分区域用 `shapeID` 独立识别同名市县。

## 更新与核验

`scripts/prepare-dashboard-china-maps.mjs <原始文件目录>` 生成中国与世界资源。输入文件名为：

- `tianshu-datav-china-outline.json`
- `tianshu-datav-china-provinces.json`
- `tianshu-cn-world-source.json`
- `tianshu-chn-level-2.json`、`tianshu-chn-level-3.json`
- `tianshu-chinese-geodata-LICENSE.txt`

`scripts/update-dashboard-boundaries.mjs [已下载的目录 JSON]` 更新境外目录，排除中国及台湾港澳，不能覆盖本地中国层级。

相关测试验证关键地域、台湾港澳层级、断续线完整性、默认视野及中国边界优先裁剪。数据处理和测试不能代替正式地图审核；公开发布以[自然资源部标准地图](https://bzdt.tianditu.gov.cn/)和[公开地图内容表示规范](https://www.fmprc.gov.cn/web/wjb_673085/zzjg_673183/bjhysws_674671/bhflfg/dtdmxgfl/202303/P020230313585504979937.pdf)的要求及审核结果为准，不把数据提供者的声明等同于官方审图结论。
