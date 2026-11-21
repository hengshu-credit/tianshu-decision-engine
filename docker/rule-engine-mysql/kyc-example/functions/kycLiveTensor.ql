Object model, shape, batch, opts, box, half, cx, cy, x0, y0, x1, y1;
model = type;
if (type != null && arrContains(["insightface", "liveness.onnx"], strLower(strTrim(type)))) {
    model = "liveness.onnx";
} else if (arrContains(["Facenox MiniFASNetV2-SE FP32", "Facenox MiniFASNetV2-SE，FP32"], type)) {
    model = "best_model.onnx";
}
requireCondition(arrContains(["liveness.onnx", "CDCN++", "best_model.onnx", "anti-spoof-mn3.onnx"], model), "不支持的活体模型类型：" + type);
requireCondition(model == "CDCN++" || model == "liveness.onnx", "该模型使用引擎内建图像预处理");
shape = imageDimensions(image);
requireCondition(prepared instanceof java.util.List, "待推理人脸应为列表");
batch = [];
for (Object face : prepared) {
    if (face["status"] == "ok") {
        if (model == "liveness.onnx") {
            opts = {"width":80,"height":80,"order":"RGB","offset":0,"divisor":255,"affine":face["affine"],"replicate":true};
        } else {
            box = face["bbox"];
            half = numMax(box["width"],box["height"])/2.0;
            cx = box["x"] + box["width"]/2.0; cy = box["y"] + box["height"]/2.0;
            x0 = numMax(0,numFloor(cx-half)); y0 = numMax(0,numFloor(cy-half));
            x1 = numMin(shape["width"],numCeil(cx+half)); y1 = numMin(shape["height"],numCeil(cy+half));
            opts = {"width":256,"height":256,"order":"RGB","offset":127.5,"divisor":127.5,"crop":{"x":x0,"y":y0,"width":x1-x0,"height":y1-y0}};
        }
        batch = arrAdd(batch, imageTensorChw(image, opts));
    }
}
return batch;
