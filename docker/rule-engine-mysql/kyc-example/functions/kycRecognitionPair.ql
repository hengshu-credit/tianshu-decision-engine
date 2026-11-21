Object model, template, batch, selected, points, affine, opts;
model = type == null ? "" : strTrim(type);
if (model == "adaface_ir_101.onnx") { model = "AdaFace IR101"; }
if (model == "edgeface_xs_gamma_06.onnx") { model = "EdgeFace-XS"; }
requireCondition(arrContains(["w600k_r50.onnx", "w600k_mbf.onnx", "AdaFace IR101", "EdgeFace-XS"], model), "不支持的人脸比对模型：" + type);
template = [[38.2946,51.6963],[73.5318,51.5014],[56.0252,71.7366],[41.5493,92.3655],[70.7299,92.2041]];
batch = [];
for (Object side : [{"image":documentImage, "faces":documentFaces, "label":"证件照"}, {"image":image, "faces":faces, "label":"现场照"}]) {
    selected = side["faces"];
    requireCondition(selected instanceof java.util.List && count(selected) == 1, side["label"] + "需要恰好一张已选人脸");
    points = selected[0]["landmarks"];
    requireCondition(points instanceof java.util.List && count(points) == 5, side["label"] + "缺少原图五点");
    affine = pointSimilarityAffine(points, template);
    requireCondition(affine[0]*affine[0] + affine[3]*affine[3] >= 1.0e-14, "五点相似变换退化");
    opts = {"width":112, "height":112, "order":(model == "AdaFace IR101" ? "BGR" : "RGB"), "offset":127.5, "divisor":127.5, "affine":affine};
    batch = arrAdd(batch, imageTensorChw(side["image"], opts));
}
return batch;
