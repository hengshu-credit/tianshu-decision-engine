package com.hengshucredit.rule.server.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hengshucredit.rule.model.entity.ConsoleUserProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface ConsoleUserProjectMapper extends BaseMapper<ConsoleUserProject> {
    /** 仅供账户授权目录使用；不能返回业务配置或访问凭据。 */
    @Select("SELECT id, project_code AS projectCode, project_name AS projectName, status FROM rule_project WHERE status != -1 ORDER BY id")
    List<ProjectOption> grantOptions();

    @Select("SELECT COUNT(*) FROM rule_project WHERE id = #{id} AND status != -1")
    long grantTargetExists(@Param("id") Long id);

    @Select("""
            <script>SELECT id, project_code AS projectCode, project_name AS projectName, status FROM rule_project WHERE status != -1
            <if test="keyword != null and keyword != ''">AND (project_code LIKE CONCAT('%',#{keyword},'%') OR project_name LIKE CONCAT('%',#{keyword},'%'))</if>
            <if test="code != null and code != ''">AND project_code LIKE CONCAT('%',#{code},'%')</if>
            <if test="name != null and name != ''">AND project_name LIKE CONCAT('%',#{name},'%')</if>
            <if test="status != null">AND status = #{status}</if>
            ORDER BY id DESC</script>
            """)
    com.baomidou.mybatisplus.core.metadata.IPage<ProjectOption> browseOptions(com.baomidou.mybatisplus.extension.plugins.pagination.Page<ProjectOption> page,
            @Param("keyword") String keyword, @Param("code") String code, @Param("name") String name, @Param("status") Integer status);

    @Select("SELECT id, project_code AS projectCode, project_name AS projectName, status FROM rule_project WHERE id = #{id} AND status != -1")
    ProjectOption browseOption(@Param("id") Long id);

    record ProjectOption(Long id, String projectCode, String projectName, Integer status) { }
}
