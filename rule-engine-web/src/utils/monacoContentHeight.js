// 左右差异编辑器会插入对齐用的空行。按实际排版高度测量，同时覆盖折行和折叠。
export function followMonacoContentHeight(editors, setHeight) {
  const measure = () => setHeight(Math.ceil(Math.max(...editors.map(editor => editor.getContentHeight()))) + 2)
  for (const editor of editors) editor.onDidContentSizeChange(measure)
  measure()
}
