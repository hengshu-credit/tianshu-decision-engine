Object model;
model = type;
if (type != null && arrContains(["insightface", "liveness.onnx"], strLower(strTrim(type)))) {
    model = "liveness.onnx";
} else if (arrContains(["Facenox MiniFASNetV2-SE FP32", "Facenox MiniFASNetV2-SE，FP32"], type)) {
    model = "best_model.onnx";
}
requireCondition(arrContains(["liveness.onnx", "CDCN++", "best_model.onnx", "anti-spoof-mn3.onnx"], model), "不支持的活体模型类型：" + type);
if (prepared == null) { return "SKIP"; }
requireCondition(prepared instanceof java.util.List, "待推理人脸应为列表");
for (Object face : prepared) {
    requireCondition(face instanceof java.util.Map, "期望人脸对象");
    if (face["status"] == "ok") { return model; }
}
return "SKIP";
