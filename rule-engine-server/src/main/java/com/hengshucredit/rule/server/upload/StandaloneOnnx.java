package com.hengshucredit.rule.server.upload;

import java.nio.ByteBuffer;

/** 在交给 native runtime 前按 ONNX protobuf 的结构检查外部张量；大权重字段只跳过字节。 */
public final class StandaloneOnnx {
    private enum Message { MODEL, GRAPH, NODE, ATTRIBUTE, TENSOR, SPARSE, FUNCTION, TRAINING }
    private StandaloneOnnx() { }

    public static void require(byte[] bytes) { inspect(ByteBuffer.wrap(bytes), Message.MODEL, 0); }

    private static void inspect(ByteBuffer data, Message type, int depth) {
        if (depth > 64) throw new IllegalArgumentException("ONNX 嵌套深度超过限制");
        boolean graph = false, version = false;
        while (data.hasRemaining()) {
            long tag = varint(data);
            if (tag <= 0 || tag > Integer.MAX_VALUE) throw invalid();
            int field = (int) tag >>> 3, wire = (int) tag & 7;
            if (field == 0) throw invalid();
            if (type == Message.TENSOR && field == 13) throw external();
            switch (wire) {
                case 0 -> {
                    long value = varint(data);
                    if (type == Message.TENSOR && field == 14 && value != 0) throw external();
                    if (type == Message.MODEL && field == 1) version = value > 0;
                }
                case 1 -> skip(data, 8);
                case 5 -> skip(data, 4);
                case 2 -> {
                    long length = varint(data);
                    if (length < 0 || length > data.remaining()) throw invalid();
                    Message child = child(type, field);
                    if (child != null) {
                        inspect(data.slice(data.position(), (int) length), child, depth + 1);
                        if (type == Message.MODEL && child == Message.GRAPH) graph = true;
                    }
                    skip(data, (int) length);
                }
                default -> throw invalid();
            }
        }
        if (type == Message.MODEL && !(graph && version)) throw invalid();
    }

    private static Message child(Message type, int field) {
        return switch (type) {
            case MODEL -> switch (field) { case 7 -> Message.GRAPH; case 20 -> Message.TRAINING; case 25 -> Message.FUNCTION; default -> null; };
            case GRAPH -> switch (field) { case 1 -> Message.NODE; case 5 -> Message.TENSOR; case 15 -> Message.SPARSE; default -> null; };
            case NODE -> field == 5 ? Message.ATTRIBUTE : null;
            case ATTRIBUTE -> switch (field) { case 5,10 -> Message.TENSOR; case 6,11 -> Message.GRAPH; case 22,23 -> Message.SPARSE; default -> null; };
            case FUNCTION -> switch (field) { case 7 -> Message.NODE; case 11 -> Message.ATTRIBUTE; default -> null; };
            case SPARSE -> field == 1 || field == 2 ? Message.TENSOR : null;
            case TRAINING -> field == 1 || field == 2 ? Message.GRAPH : null;
            case TENSOR -> null;
        };
    }

    private static long varint(ByteBuffer data) {
        long value = 0;
        for (int shift = 0; shift < 64 && data.hasRemaining(); shift += 7) {
            int next = data.get() & 255;
            if (shift == 63 && next > 1) throw invalid();
            value |= (long) (next & 127) << shift;
            if ((next & 128) == 0) return value;
        }
        throw invalid();
    }

    private static void skip(ByteBuffer data, int count) {
        if (count > data.remaining()) throw invalid();
        data.position(data.position() + count);
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("无效的 ONNX 模型文件结构"); }
    private static IllegalArgumentException external() { return new IllegalArgumentException("ONNX 模型必须内嵌权重，不能引用外部张量文件"); }
}
