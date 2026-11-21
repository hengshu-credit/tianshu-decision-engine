Object model, result;
model = type;
if (type != null && arrContains(["insightface", "liveness.onnx"], strLower(strTrim(type)))) {
    model = "liveness.onnx";
} else if (arrContains(["Facenox MiniFASNetV2-SE FP32", "Facenox MiniFASNetV2-SE，FP32"], type)) {
    model = "best_model.onnx";
}
requireCondition(arrContains(["liveness.onnx", "CDCN++", "best_model.onnx", "anti-spoof-mn3.onnx"], model), "不支持的活体模型类型：" + type);
if (prepared == null) { return []; }
requireCondition(prepared instanceof java.util.List, "人脸输入应为列表");
result = [];
for (Object face : prepared) {
    requireCondition(face["status"] != "ok", "模型输出缺少对应人脸");
    result = arrAdd(result, {"faceIndex":face["faceIndex"], "bbox":face["bbox"], "status":face["status"], "modelType":model, "reason":"aligned_crop_out_of_bounds", "liveProbability":null, "attackProbability":null});
}
return result;
