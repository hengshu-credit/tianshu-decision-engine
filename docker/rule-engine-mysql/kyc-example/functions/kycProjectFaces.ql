Object rows, shape, w, h, result;
Object box, points, confidence, affine, a, b, scale, u, v;
Object cx, cy, radius, rx, ry, inclusive, left, top, right, bottom, mapped;
rows = detections instanceof java.util.Map ? detections["faces"] : detections;
requireCondition(rows instanceof java.util.List, "人脸检测结果不可用");
requireCondition(height >= 1 && width >= 1 && width <= 2147483647 && height <= 2147483647 && height == numFloor(height) && width == numFloor(width), "输出宽高必须为正整数");
requireCondition(isFiniteNumber(widthScale) && isFiniteNumber(heightScale) && widthScale > 0 && heightScale > 0, "外扩倍数必须为正有限数");
shape = imageDimensions(image); w = shape["width"]; h = shape["height"]; result = [];
for (Object detection : rows) {
    if (detection instanceof java.util.Map) {
        box = detection["bbox"]; points = detection["landmarks"];
        confidence = objGetOrDefault(detection,"confidence",objGetOrDefault(detection,"score",0));
    } else {
        requireCondition(detection instanceof java.util.List && count(detection) == 15, "五点检测输出形状无效");
        for (Object coordinate : detection) { requireCondition(isFiniteNumber(coordinate), "检测结果含无效数值"); }
        box = {"x":detection[0]*w,"y":detection[1]*h,"width":(detection[2]-detection[0])*w,"height":(detection[3]-detection[1])*h};
        confidence = detection[4]; points = [];
        for (int i=5; i<15; i=i+2) { points=arrAdd(points,[detection[i]*w,detection[i+1]*h]); }
    }
    requireCondition(box instanceof java.util.Map && points instanceof java.util.List && count(points) == 5, "模型未返回人脸框与五点");
    if (name != null && arrContains(["insightface","liveness.onnx"],strLower(strTrim(name)))) {
        affine = pointSimilarityAffine(points,[[32.03,38.06],[47.89,37.98],[40.01,47.08],[33.50,56.36],[46.63,56.29]]);
        a=affine[0]; b=affine[3]; scale=a*a+b*b;
        u=39.5-affine[2]; v=39.5-affine[5];
        cx=(a*u+b*v)/scale; cy=(a*v-b*u)/scale;
        radius=39.5*(numAbs(a)+numAbs(b))/scale;
        rx=radius*numMax(1,widthScale); ry=radius*numMax(1,heightScale); inclusive=1;
    } else {
        cx=box["x"]+box["width"]/2.0; cy=box["y"]+box["height"]/2.0;
        rx=box["width"]*widthScale/2.0; ry=box["height"]*heightScale/2.0; inclusive=0;
    }
    left=numMax(0,numFloor(cx-rx)); top=numMax(0,numFloor(cy-ry));
    right=numMin(w,numCeil(cx+rx)+inclusive); bottom=numMin(h,numCeil(cy+ry)+inclusive);
    requireCondition(right>left && bottom>top,"裁剪区域与原图无交集");
    mapped=[];
    for (Object xy : points) {
        requireCondition(xy instanceof java.util.List && count(xy)==2 && isFiniteNumber(xy[0]) && isFiniteNumber(xy[1]), "五点坐标无效");
        mapped=arrAdd(mapped,[(xy[0]-left)*width/(right-left),(xy[1]-top)*height/(bottom-top)]);
    }
    result=arrAdd(result,{"bbox":box,"landmarks":points,"confidence":confidence,"cropBox":{"x":left,"y":top,"width":right-left,"height":bottom-top},"outputWidth":width,"outputHeight":height,"outputLandmarks":mapped});
}
return result;
