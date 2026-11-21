package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.OfflineTransferLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OfflineTransferLogMapper extends BaseMapper<OfflineTransferLog> {
    @org.apache.ibatis.annotations.Update("""
            UPDATE offline_transfer_log SET resource_count=JSON_LENGTH(content_json,'$.lineage.nodes')
            WHERE operation_type='EXPORT' AND status='SUCCESS'
              AND JSON_TYPE(JSON_EXTRACT(IF(JSON_VALID(content_json),content_json,NULL),'$.lineage.nodes'))='ARRAY'
              AND resource_count <> JSON_LENGTH(IF(JSON_VALID(content_json),content_json,NULL),'$.lineage.nodes')
            """)
    int repairExportResourceCounts();
}
