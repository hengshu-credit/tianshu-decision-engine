package com.hengshucredit.rule.server.transfer;

import com.hengshucredit.rule.server.artifact.CanonicalJson;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 上传、进度轮询和下载可以由不同 server 节点处理。文件分块在临时任务过期时级联清理。 */
@Repository
public class TransferTaskStore {
    public static final int PART_SIZE = 1024 * 1024;
    public static final int MAX_SIZE = 512 * PART_SIZE;
    private final JdbcTemplate db;

    public TransferTaskStore(JdbcTemplate db) { this.db = db; }

    public record Task(String id, String operation, String status, int size, Map<String, Object> result) {}
    public record Action(String id, long time, int progress, String message) {}

    public Task owned(UUID id, String session) {
        if (id == null) throw new IllegalArgumentException("缺少迁移任务标识");
        var tasks = db.query("SELECT operation_type,status,file_size,result_json FROM offline_transfer_task WHERE task_id=? AND session_digest=?",
                (rs, row) -> new Task(id.toString(), rs.getString(1), rs.getString(2), rs.getInt(3),
                        rs.getString(4) == null ? null : CanonicalJson.readMap(rs.getString(4))), id.toString(), session);
        if (tasks.isEmpty()) throw new IllegalArgumentException("迁移任务或临时文件已失效，请重新预检");
        return tasks.get(0);
    }

    public void create(UUID id, String session, String operation, String status, int size) {
        db.update("INSERT INTO offline_transfer_task(task_id,session_digest,operation_type,status,file_size) VALUES(?,?,?,?,?)",
                id.toString(), session, operation, status, size);
    }

    public Task upload(UUID id, String session) {
        Task task = owned(id, session);
        if (!"UPLOAD".equals(task.operation())) throw new IllegalArgumentException("请选择上传的配置包");
        return task;
    }

    public static int partSize(int size, int index) {
        if (index < 0 || (long) index * PART_SIZE >= size) throw new IllegalArgumentException("文件分块序号越界");
        return Math.min(PART_SIZE, size - index * PART_SIZE);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void receive(UUID id, String session, int index, InputStream input) throws IOException {
        int length = partSize(upload(id, session).size(), index);
        byte[] bytes = input.readNBytes(length + 1);
        if (bytes.length != length) throw new IllegalArgumentException("上传分块长度不匹配，请重新选择文件");
        db.update("UPDATE offline_transfer_task SET update_time=NOW(3) WHERE task_id=?", id.toString());
        // 重发相同分块幂等。已经预检的文件不能被后来的请求替换。
        try { insertPart(id.toString(), index, bytes); }
        catch (DuplicateKeyException duplicate) {
            if (!Arrays.equals(bytes, part(id.toString(), index))) throw new IllegalArgumentException("已上传分块与重传内容不一致");
        }
    }

    private void insertPart(String id, int index, byte[] bytes) {
        db.update("INSERT INTO offline_transfer_part(task_id,part_index,content) VALUES(?,?,?)", id, index, bytes);
    }

    private byte[] part(String id, int index) {
        var parts = db.query("SELECT content FROM offline_transfer_part WHERE task_id=? AND part_index=?",
                (rs, row) -> rs.getBytes(1), id, index);
        if (parts.isEmpty()) throw new IllegalArgumentException("配置包分块不完整，请重新上传");
        return parts.get(0);
    }

    public void writeFile(Task file, OutputStream destination) throws IOException {
        for (int index = 0; (long) index * PART_SIZE < file.size(); index++) {
            byte[] bytes = part(file.id(), index);
            if (partSize(file.size(), index) != bytes.length) throw new IllegalStateException("配置包分块长度异常");
            destination.write(bytes);
        }
    }

    public void saveExport(String id, byte[] bytes) {
        for (int offset = 0; offset < bytes.length; offset += PART_SIZE) {
            insertPart(id, offset / PART_SIZE, Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + PART_SIZE)));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void progress(String id, int percent, String message) {
        if (db.update("UPDATE offline_transfer_task SET update_time=NOW(3) WHERE task_id=? AND status='RUNNING'", id) != 1)
            throw new IllegalStateException("迁移任务已结束");
        db.update("INSERT INTO offline_transfer_action(task_id,progress,message) VALUES(?,?,?)", id, percent, message);
    }

    public List<Action> actions(String id, long after) {
        // 由数据库将事件时间转为时间戳，避免 JDBC 会话时区与浏览器相差数小时。
        return db.query("SELECT id,UNIX_TIMESTAMP(create_time) AS epoch_seconds,progress,message FROM offline_transfer_action WHERE task_id=? AND id>? ORDER BY id LIMIT 256",
                (rs, row) -> new Action(rs.getString(1), rs.getBigDecimal("epoch_seconds").movePointRight(3).longValueExact(), rs.getInt(3), rs.getString(4)), id, after);
    }

    public void finish(String id, String status, int size, Object result) {
        if (db.update("UPDATE offline_transfer_task SET status=?,file_size=?,result_json=?,update_time=NOW(3) WHERE task_id=? AND status='RUNNING'",
                status, size, CanonicalJson.write(result), id) != 1) throw new IllegalStateException("迁移任务已失效");
    }

    @Scheduled(fixedDelay = 1800000, initialDelay = 60000)
    public void cleanExpired() {
        db.update("UPDATE offline_transfer_task SET status='FAILED',result_json=?,update_time=NOW(3) WHERE status='RUNNING' AND update_time < NOW() - INTERVAL 2 HOUR",
                CanonicalJson.write(Map.of("message", "后台执行已中断，请核对迁移日志后重新预检")));
        db.update("DELETE FROM offline_transfer_task WHERE status<>'RUNNING' AND update_time < NOW() - INTERVAL 1 DAY LIMIT 100");
    }
}
