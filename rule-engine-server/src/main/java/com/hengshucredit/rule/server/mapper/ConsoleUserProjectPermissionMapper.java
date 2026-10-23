package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.ConsoleUserProjectPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ConsoleUserProjectPermissionMapper extends BaseMapper<ConsoleUserProjectPermission> {
    @Select("SELECT g.project_id, p.permission_code, g.effect FROM console_user_project_permission g JOIN console_permission p ON p.id = g.permission_id AND p.status = 1 WHERE g.user_id = #{userId}")
    List<Override> overrides(@Param("userId") Long userId);
    record Override(Long projectId, String permissionCode, String effect) { }
}
