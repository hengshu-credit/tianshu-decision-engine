package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface GovernanceApprovalRequestMapper
        extends BaseMapper<GovernanceApprovalRequest> {
    // Read only the latest request metadata; list pages must not fetch every historical snapshot.
    @Select({"<script>",
            "SELECT r.id, r.resource_id, r.action, r.status, r.applicant, r.active_resource_key",
            "FROM governance_approval_request r JOIN (",
            "SELECT COALESCE(MAX(CASE WHEN status = 'PENDING' THEN id END), MAX(CASE WHEN status = 'EDITING' THEN id END), MAX(id)) id FROM governance_approval_request WHERE resource_type = #{type} AND resource_id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "GROUP BY resource_id) latest ON latest.id = r.id",
            "</script>"})
    List<GovernanceApprovalRequest> selectLatestResourceRequests(@Param("type") String type, @Param("ids") List<Long> ids);
}
