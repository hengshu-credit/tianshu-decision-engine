"""Rebuild the tiny, self-contained CPU smoke fixture with onnx==1.19.1.

Generation only: normal Maven tests consume the checked-in binary and do not
need Python or a model download. API: https://onnx.ai/onnx/api/helper.html
"""
from pathlib import Path

import onnx
from onnx import TensorProto, helper

graph = helper.make_graph(
    [helper.make_node("Mul", ["Input_Tensor", "scale"], ["scaled"]),
     helper.make_node("Add", ["scaled", "bias"], ["Risk_Score"])],
    "tianshu_cpu_smoke",
    [helper.make_tensor_value_info("Input_Tensor", TensorProto.FLOAT, [1, 3])],
    [helper.make_tensor_value_info("Risk_Score", TensorProto.FLOAT, [1, 3])],
    [helper.make_tensor("scale", TensorProto.FLOAT, [1], [2.0]),
     helper.make_tensor("bias", TensorProto.FLOAT, [1], [1.0])],
)
model = helper.make_model(graph, producer_name="tianshu-tests",
                          opset_imports=[helper.make_opsetid("", 13)], ir_version=8)
# RepositoryOnnxCpuTest validates the serialized artifact with native ONNX
# Runtime, including exact output values and rejection of an invalid shape.
onnx.save(model, Path(__file__).with_name("linear.onnx"))
