Object model, shape, result, box, points, row, affine;
model = type;
if (type != null && arrContains(["insightface", "liveness.onnx"], strLower(strTrim(type)))) {
    model = "liveness.onnx";
} else if (arrContains(["Facenox MiniFASNetV2-SE FP32", "Facenox MiniFASNetV2-SE，FP32"], type)) {
    model = "best_model.onnx";
}
requireCondition(arrContains(["liveness.onnx", "CDCN++", "best_model.onnx", "anti-spoof-mn3.onnx"], model), "不支持的活体模型类型：" + type);
if (faces == null) { return []; }
requireCondition(faces instanceof java.util.List, "人脸输入应为列表");
if (count(faces) == 0) { return []; }
shape = imageDimensions(image);
result = [];
for (Object face : faces) {
    box = face["bbox"];
    requireCondition(box instanceof java.util.Map, "人脸缺少 bbox 对象");
    for (Object key : ["x","y","width","height"]) { requireCondition(isFiniteNumber(box[key]), "人脸框坐标必须为有限数值"); }
    requireCondition(box["width"] > 0 && box["height"] > 0 && box["x"] < shape["width"] && box["y"] < shape["height"] && box["x"] + box["width"] > 0 && box["y"] + box["height"] > 0, "人脸框与原图没有有效交集");
    points = face["landmarks"];
    requireCondition(points instanceof java.util.List && count(points) == 5, "需要原图五点");
    for (Object xy : points) { requireCondition(xy instanceof java.util.List && count(xy) == 2 && isFiniteNumber(xy[0]) && isFiniteNumber(xy[1]), "五点坐标无效"); }
    row = {"faceIndex":count(result), "bbox":box, "status":"ok"};
    if (model == "liveness.onnx") {
        affine = pointSimilarityAffine(points, [[32.03,38.06],[47.89,37.98],[40.01,47.08],[33.50,56.36],[46.63,56.29]]);
        if (1.0 - imageAffineCoverage(shape, affine, {"width":80,"height":80}) > 0.30) { row["status"] = "input_rejected"; }
        else { row["affine"] = affine; }
    }
    result = arrAdd(result, row);
}
return result;
