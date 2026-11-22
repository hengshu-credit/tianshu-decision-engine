package com.hengshucredit.rule.server.testdata;

import com.hengshucredit.rule.server.security.ConsoleResourceAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TestInputDefaults {
    private final JdbcTemplate jdbc;
    private final ConsoleResourceAccess access;

    public Long ownerProject(String type,long owner) {
        return access.projectForResource(type.toUpperCase(java.util.Locale.ROOT),owner);
    }

    public String read(String type,long owner) {
        if (!"RULE".equalsIgnoreCase(type) && !"VARIABLE".equalsIgnoreCase(type)) return null;
        var resource = TestResource.valueOf(type.toUpperCase(java.util.Locale.ROOT));
        access.require(resource.accessType,owner,"view");
        return jdbc.queryForObject("SELECT test_input_json FROM "+resource.table+" WHERE id=?",String.class,owner);
    }

    @Transactional
    public void save(TestResource type,long owner,String json) {
        if (type != TestResource.RULE && type != TestResource.VARIABLE) throw new IllegalArgumentException("该资源使用原配置保存样例");
        access.require(type.accessType,owner,"edit");
        if (json == null || json.length()>1048576 || !MockRule.parse(json).isObject()) throw new IllegalArgumentException("测试参数须为 JSON 对象且长度不能超过 1M");
        if (jdbc.update("UPDATE "+type.table+" SET test_input_json=? WHERE id=? AND status>=0",json,owner)==0)
            throw new IllegalArgumentException("资源已删除");
    }
}
