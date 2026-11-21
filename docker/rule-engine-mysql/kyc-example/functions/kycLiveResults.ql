Object model, rows, result, cursor, row, value, pair, peak, a, b, live, attack;
model = type;
if (type != null && arrContains(["insightface", "liveness.onnx"], strLower(strTrim(type)))) {
    model = "liveness.onnx";
} else if (arrContains(["Facenox MiniFASNetV2-SE FP32", "Facenox MiniFASNetV2-SE，FP32"], type)) {
    model = "best_model.onnx";
}
requireCondition(arrContains(["liveness.onnx", "CDCN++", "best_model.onnx", "anti-spoof-mn3.onnx"], model), "不支持的活体模型类型：" + type);
requireCondition(outputs instanceof java.util.List, "期望模型输出列表");
rows = prepared == null ? [] : prepared;
requireCondition(rows instanceof java.util.List, "人脸输入应为列表");
result = []; cursor = 0;
for (Object face : rows) {
    row = {"faceIndex":face["faceIndex"],"bbox":face["bbox"],"status":face["status"],"modelType":model,"liveProbability":null,"attackProbability":null};
    if (face["status"] != "ok") {
        row["reason"] = "aligned_crop_out_of_bounds";
    } else {
        requireCondition(cursor < count(outputs), "模型输出缺少对应人脸");
        value = outputs[cursor]; cursor = cursor + 1;
        if (model == "best_model.onnx" || model == "anti-spoof-mn3.onnx") {
            requireCondition(isFiniteNumber(value["faceIndex"]) && value["faceIndex"] == face["faceIndex"], "人脸返回顺序不符");
            pair = value[model == "best_model.onnx" ? "logits" : "probabilities"];
            requireCondition(pair instanceof java.util.List && count(pair) == 2 && isFiniteNumber(pair[0]) && isFiniteNumber(pair[1]), "二分类结果维度或数值有误");
            if (model == "best_model.onnx") {
                peak = numMax(pair[0],pair[1]);
                a = numPow(2.718281828459045,pair[0]-peak); b = numPow(2.718281828459045,pair[1]-peak);
                live = a/(a+b); attack = b/(a+b);
            } else {
                live = pair[0]; attack = pair[1];
                requireCondition(live >= 0 && live <= 1 && attack >= 0 && attack <= 1 && numAbs(live+attack-1) <= 0.00001, "二分类概率无效");
            }
        } else {
            requireCondition(isFiniteNumber(value) && value >= 0 && value <= 1, "活体分数超出 [0,1]");
            live = value; attack = 1.0-value;
        }
        row["liveProbability"] = live; row["attackProbability"] = attack;
    }
    result = arrAdd(result,row);
}
requireCondition(cursor == count(outputs), "模型输出有多余的人脸结果");
return result;
