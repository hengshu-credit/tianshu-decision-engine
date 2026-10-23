package com.hengshucredit.rule.server.mapper;

import com.hengshucredit.rule.model.entity.OfflineTransferLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface OfflineTransferLogResourceMapper {
    @Insert("""
            <script>INSERT IGNORE INTO offline_transfer_log_resource(log_id,resource_type,resource_id) VALUES
            <foreach collection="refs" item="ref" separator=",">(#{logId},#{ref.type},#{ref.resourceId})</foreach></script>
            """)
    void index(@Param("logId") Long logId, @Param("refs") List<Reference> refs);
    record Reference(String type, Long resourceId) { }

    @Select("SELECT l.* FROM offline_transfer_log l WHERE NOT EXISTS (SELECT 1 FROM offline_transfer_log_resource r WHERE r.log_id=l.id) ORDER BY l.id LIMIT 100")
    List<OfflineTransferLog> unindexed();
}
