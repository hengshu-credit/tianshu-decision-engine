# 阿里妈妈方圆体 VF

`font.ttf` 和 `font.woff2` 基于官方字体文件，未做字形修改或静态化。官方版本的 STAT 表引用了不存在的第 3 个轴（实际只有 wght / BEVL 两轴），会导致当前 Chrome 拒绝加载；此处仅移除了该无效的可选元数据表，保留全部字形、可变轴和命名实例。页面通过可变轴选择 SemiBold（wght=600，BEVL=80）。

- 官方字体站：https://www.alibabafonts.com/
- 官方下载包：https://fonts.alibabadesign.com/AlimamaFangYuanTiVF.zip
- 来源版本：AlimamaFangYuanTiVF-Thin，2026-11-03 下载。
- 原始使用说明与许可见本目录 `AlimamaFangYuanTiVF-INSTRUCTION.txt`、`AlimamaFangYuanTiVF-LICENSE.txt`。

字体版权归淘宝（中国）软件有限公司（阿里妈妈）所有，按随附许可嵌入使用。

兼容处理可复现：用 FontTools 读取官方 TTF，删除 `STAT` 表并保存 TTF，再设置 `font.flavor = "woff2"` 保存 WOFF2。原始 TTF SHA256：`c73d546e2a75f4184944f372cdd41677999fe7dd53fc66c690dc5f01f99002cf`。
