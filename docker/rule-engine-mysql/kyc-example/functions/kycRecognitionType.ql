Object model;
model = type == null ? "" : strTrim(type);
if (model == "adaface_ir_101.onnx") { model = "AdaFace IR101"; }
if (model == "edgeface_xs_gamma_06.onnx") { model = "EdgeFace-XS"; }
requireCondition(arrContains(["w600k_r50.onnx", "w600k_mbf.onnx", "AdaFace IR101", "EdgeFace-XS"], model), "不支持的人脸比对模型：" + type);
return model;
