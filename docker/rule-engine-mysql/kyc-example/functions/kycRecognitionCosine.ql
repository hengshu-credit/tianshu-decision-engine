Object score;
requireCondition(embeddings instanceof java.util.List && count(embeddings) == 2, "需要证件照和现场照两条特征");
for (Object vector : embeddings) {
    requireCondition(vector instanceof java.util.List && count(vector) == 512, "人脸特征应为 512 维");
}
score = cosineSimilarity(embeddings[0], embeddings[1]);
requireCondition(isFiniteNumber(score), "余弦相似度无效");
return numMax(-1, numMin(1, score));
