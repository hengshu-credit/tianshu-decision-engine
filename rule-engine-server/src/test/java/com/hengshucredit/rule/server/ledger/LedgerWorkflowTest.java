package com.hengshucredit.rule.server.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hengshucredit.rule.model.PlatformException;
import com.hengshucredit.rule.model.dto.LedgerContent;
import com.hengshucredit.rule.model.dto.LedgerRequests;
import com.hengshucredit.rule.server.security.*;
import com.hengshucredit.rule.server.service.ConsoleOperatorResolver;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static com.hengshucredit.rule.server.ledger.LedgerAccess.number;
import static org.junit.Assert.*;

public class LedgerWorkflowTest {
    private JdbcTemplate jdbc;
    private DriverManagerDataSource dataSource;
    private AtomicReference<ConsoleProjectAccess.Scope> identity;
    private AtomicReference<LedgerAntivirus.Result> scanResult;
    private LedgerArticleService articles;
    private LedgerAssetService assets;
    private LedgerQueryService queries;
    private ConsoleDataPermissionHandler policy;
    private ObjectMapper json;
    private org.springframework.context.annotation.AnnotationConfigApplicationContext repositoryContext;

    @After public void closeRepositoryContext() { if (repositoryContext != null) repositoryContext.close(); }

    @Before public void setup() throws Exception {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:ledger" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        String schema = new ClassPathResource("sql/schema.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String ledgerSchema = schema.substring(schema.indexOf("-- 台账模块："));
        new ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(ledgerSchema.getBytes(java.nio.charset.StandardCharsets.UTF_8))).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE rule_project (id BIGINT PRIMARY KEY,project_name VARCHAR(128))");
        jdbc.update("INSERT INTO rule_project VALUES (1,'项目一'),(2,'项目二')");
        identity = new AtomicReference<>(scope(10, Set.of(1L), true));
        ConsoleProjectAccess projectAccess = new ConsoleProjectAccess() { @Override public Scope current() { return identity.get(); } };
        policy = new ConsoleDataPermissionHandler(projectAccess);
        var access = new LedgerAccess(projectAccess, new ConsoleDataWriteInterceptor(projectAccess, policy, jdbc), new ConsoleOperatorResolver() { @Override public String resolve() { return "作者" + identity.get().userId(); } }, jdbc);
        repositoryContext = new org.springframework.context.annotation.AnnotationConfigApplicationContext();
        repositoryContext.registerBean(JdbcTemplate.class, () -> jdbc);
        repositoryContext.registerBean(ConsoleDataPermissionHandler.class, () -> policy);
        repositoryContext.registerBean(org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor.class);
        repositoryContext.register(LedgerRepository.class);
        repositoryContext.refresh();
        var repository = repositoryContext.getBean(LedgerRepository.class);
        assertTrue("生产环境的 @Repository 必须通过代理参与完整工作流测试", org.springframework.aop.support.AopUtils.isCglibProxy(repository));
        scanResult = new AtomicReference<>(new LedgerAntivirus.Result("PENDING", "等待扫描"));
        LedgerAntivirus antivirus = new LedgerAntivirus(new LedgerProperties()) { @Override public Result scan(byte[] bytes) { return scanResult.get(); } };
        assets = transaction(new LedgerAssetService(repository, access, antivirus));
        var builder = new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder();
        new com.hengshucredit.rule.server.config.JacksonConfig().localDateTimeCustomizer().customize(builder);
        json = builder.featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        articles = transaction(new LedgerArticleService(repository, access, assets, json));
        queries = transaction(new LedgerQueryService(repository, access, articles));
    }
    private ConsoleProjectAccess.Scope scope(long user, Set<Long> projects, boolean editable) {
        return new ConsoleProjectAccess.Scope(user, projects, editable ? Set.of("ledger:view", "ledger:edit") : Set.of("ledger:view"), false, true, Map.of());
    }
    @SuppressWarnings("unchecked") private <T> T transaction(T target) {
        var factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
        var advice = new TransactionInterceptor(); advice.setTransactionManager(new DataSourceTransactionManager(dataSource)); advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(advice); return (T) factory.getProxy();
    }
    private LedgerContent content(String title, String body) {
        var value = new LedgerContent(); value.setTitle(title); value.setBody(body); value.setCategory("WORK"); value.setScope("PROJECT"); value.setProjectId(1L); value.setOccurredAt(LocalDateTime.of(2026, 11, 1, 10, 20, 30)); return value;
    }
    private Map<String, Object> draft(LedgerContent value) { return articles.save(null, new LedgerRequests.SaveDraft(null, value)); }
    private Map<String, Object> publish(Map<String, Object> draft) { return articles.publish(number(draft.get("id")), number(draft.get("version"))); }
    private LedgerQueryService.Filter filter(List<Long> ids) { return new LedgerQueryService.Filter(null,null,ids,null,null,null,null); }
    private byte[] png() throws Exception { var image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB); image.setRGB(2,3,0xffa12345); var output = new ByteArrayOutputStream(); javax.imageio.ImageIO.write(image, "png", output); return output.toByteArray(); }

    @Test public void publishedSnapshotsSurviveDraftEditsAndEveryPublicationAppearsInTimeline() {
        var firstDraft = draft(content("初始调整", "初始正文")); long article = number(firstDraft.get("id"));
        var first = publish(firstDraft);
        var edit = articles.edit(article);
        var secondContent = content("二次调整", "修正后的正文"); secondContent.setChangeSummary("首日结果不符合预期");
        var secondDraft = articles.save(article, new LedgerRequests.SaveDraft(number(edit.get("version")), secondContent));
        assertEquals("初始正文", articles.revision(number(first.get("id"))).get("body"));
        assertEquals(1, queries.timeline(filter(List.of()),1,20).getTotal());
        var second = publish(secondDraft);
        assertEquals(2, number(second.get("versionNo"))); assertEquals(2, queries.timeline(filter(List.of()),1,20).getTotal());
        assertEquals("初始正文", articles.revision(number(first.get("id"))).get("body"));
        assertEquals("修正后的正文", articles.revision(number(second.get("id"))).get("body"));
        var editor = articles.edit(article);
        var restored = articles.restore(article, number(first.get("id")), number(editor.get("version")));
        assertEquals("初始正文", ((LedgerContent) restored.get("content")).getBody());
        assertEquals(2, number(restored.get("publishedVersion")));
        assertEquals(2, queries.timeline(filter(List.of()),1,20).getTotal());
        assertEquals(2, ((List<?>) articles.workspace(article).get("history")).size());
    }
    @Test public void draftsArePrivateWhilePublishedContentIsSharedOnlyWithinAuthorizedProjects() {
        var draft = draft(content("私有标题", "不能泄露的草稿")); long id = number(draft.get("id"));
        identity.set(scope(11, Set.of(1L), true));
        assertEquals(0, articles.list(null,null,null,null,1,20).getTotal());
        assertThrows(ConsoleAccessDeniedException.class, () -> articles.edit(id));
        identity.set(scope(10, Set.of(1L), true)); var publication = publish(draft);
        var edit = articles.edit(id); articles.save(id, new LedgerRequests.SaveDraft(number(edit.get("version")), content("秘密草稿二", "未公开内容")));
        identity.set(scope(11, Set.of(1L), true));
        var visible = articles.list(null,null,null,null,1,20).getRecords().get(0);
        assertEquals("私有标题", visible.get("title")); assertEquals(false, visible.get("hasDraft")); assertEquals(false, visible.get("canEdit"));
        assertEquals(0, articles.list(null,null,null,"秘密草稿二",1,20).getTotal());
        assertEquals(0, ((List<?>) articles.workspace(id).get("drafts")).size());
        assertEquals("不能泄露的草稿", articles.revision(number(publication.get("id"))).get("body"));
        identity.set(scope(11, Set.of(2L), true));
        assertEquals(0, queries.timeline(filter(List.of()),1,20).getTotal());
        assertThrows(ConsoleAccessDeniedException.class, () -> articles.revision(number(publication.get("id"))));
    }
    @Test public void staleUpdatesAreRejectedAndDraftDeletionLeavesPublishedVersionIntact() {
        var draft = draft(content("文章", "内容")); long id = number(draft.get("id")); long version = number(draft.get("version"));
        articles.save(id, new LedgerRequests.SaveDraft(version, content("更新", "正文")));
        assertThrows(PlatformException.class, () -> articles.save(id, new LedgerRequests.SaveDraft(version, content("覆盖", "过期请求"))));
        var published = publish(articles.edit(id));
        var current = articles.edit(id); var pending = articles.save(id,new LedgerRequests.SaveDraft(number(current.get("version")),content("新草稿", "保留发布")));
        articles.discard(id,number(pending.get("version")));
        assertEquals("正文",articles.revision(number(published.get("id"))).get("body"));
        assertEquals(false,articles.edit(id).get("hasDraft"));
    }
    @Test public void multiEventSelectionUsesUnionWithoutDuplicatingAnArticleVersionAndDatesKeepSeconds() {
        long one=number(queries.saveEvent(null,new LedgerRequests.Event("事件一","FOLLOWING","PROJECT",1L,null)).get("id"));
        long two=number(queries.saveEvent(null,new LedgerRequests.Event("事件二","FOLLOWING","PROJECT",1L,null)).get("id"));
        var body=content("双事件文章","共同内容");body.setEventIds(List.of(one,two));publish(draft(body));
        assertEquals(1,queries.timeline(filter(List.of(one,two)),1,20).getTotal());
        assertEquals(1,queries.calendar("2026-11",filter(List.of(one,two))).size());
        var start=body.getOccurredAt();
        assertEquals(1,queries.timeline(new LedgerQueryService.Filter(null,null,List.of(),null,null,start,start),1,20).getTotal());
        assertEquals(0,queries.timeline(new LedgerQueryService.Filter(null,null,List.of(),null,null,start.plusSeconds(1),start.plusSeconds(2)),1,20).getTotal());
        assertThrows(IllegalArgumentException.class,()->queries.deleteEvent(one,0L));
    }
    @Test public void quarantinedAndPendingUploadsCannotBeReadSharedOrPublishedAndCleanImagesAreReencoded() throws Exception {
        byte[] original=png();var upload=assets.upload(new MockMultipartFile("file","image.png","image/png",original));long id=number(upload.get("id"));
        assertEquals("PENDING",upload.get("scanStatus"));assertNull(upload.get("contentUrl"));
        assertThrows(PlatformException.class,()->assets.content(id));
        assertThrows(IllegalArgumentException.class,()->assets.update(id,new LedgerRequests.Asset("image.png","GLOBAL",null,number(upload.get("version")))));
        var value=content("含图片文章","![图片](/api/rule/ledger/assets/"+id+"/content)");var pending=draft(value);
        assertThrows(IllegalArgumentException.class,()->publish(pending));
        scanResult.set(new LedgerAntivirus.Result("QUARANTINED","检测到威胁"));var quarantined=assets.retry(id,number(upload.get("version")));
        assertThrows(PlatformException.class,()->assets.content(id));
        identity.set(scope(11,Set.of(1L),true));assertEquals(0,assets.list(null,null,null,null,1,20).getTotal());
        identity.set(scope(10,Set.of(1L),true));scanResult.set(new LedgerAntivirus.Result("CLEAN","检测通过"));var clean=assets.retry(id,number(quarantined.get("version")));
        assertEquals("CLEAN",clean.get("scanStatus"));assertEquals(0xffa12345,javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(assets.content(id))).getRGB(2,3));
        publish(pending);assertEquals("PROJECT",assets.get(id).get("scope"));
        assertThrows(IllegalArgumentException.class,()->assets.delete(List.of(id)));
        assertThrows(IllegalArgumentException.class,()->assets.update(id,new LedgerRequests.Asset("image.png","GLOBAL",null,number(assets.get(id).get("version")))));
        identity.set(scope(11,Set.of(2L),true));assertThrows(ConsoleAccessDeniedException.class,()->assets.content(id));
    }
    @Test public void privateScopeNeedsAnIdentityAndGlobalReadOnlyCannotPublishOrManage() {
        identity.set(new ConsoleProjectAccess.Scope(null,Set.of(),Set.of("ledger:view","ledger:edit"),true,true,Map.of()));
        assertEquals("1 = 0",policy.predicate("rule_ledger_article","item"));
        assertThrows(ConsoleAccessDeniedException.class,()->draft(content("匿名","正文")));
        identity.set(new ConsoleProjectAccess.Scope(10L,Set.of(1L),Set.of("ledger:view","ledger:edit","ledger:manage"),false,false,Map.of()));
        var value=content("全局","正文");value.setScope("GLOBAL");value.setProjectId(null);
        assertThrows(ConsoleAccessDeniedException.class,()->draft(value));
        assertFalse(identity.get().globalCan("ledger:manage"));
    }
    @Test public void projectDraftsCanBeFoundInTheirProjectAndAreHiddenAfterGrantRevocation() {
        var draft = draft(content("项目草稿", "项目一的私有内容")); long id = number(draft.get("id"));
        assertEquals(1, articles.list("DRAFT","PROJECT",1L,null,1,20).getTotal());
        identity.set(scope(10,Set.of(2L),true));
        assertEquals(0,articles.list("DRAFT",null,null,"项目一",1,20).getTotal());
        assertThrows(ConsoleAccessDeniedException.class,()->articles.edit(id));
    }
    @Test public void automaticSummaryTracksEachPublishedBodyWhileManualSummaryIsPreserved() {
        var first = publish(draft(content("摘要测试", "第一版正文"))); long id = number(first.get("articleId"));
        var editor = articles.edit(id); var next = (LedgerContent) editor.get("content");
        assertEquals("", next.getSummary()); assertEquals(Boolean.TRUE, next.getSummaryAuto());
        next.setBody("第二版正文");
        var second = publish(articles.save(id,new LedgerRequests.SaveDraft(number(editor.get("version")),next)));
        assertEquals("第二版正文",second.get("summary")); assertEquals("第一版正文",articles.revision(number(first.get("id"))).get("summary"));
        var manual = content("手动摘要", "完整正文"); manual.setSummary("专门填写的概述");
        var publication = publish(draft(manual)); var edit = articles.edit(number(publication.get("articleId"))); var edited = (LedgerContent) edit.get("content"); edited.setBody("修改正文");
        assertEquals(Boolean.FALSE, edited.getSummaryAuto());
        var updated = publish(articles.save(number(edit.get("id")),new LedgerRequests.SaveDraft(number(edit.get("version")),edited)));
        assertEquals("专门填写的概述",updated.get("summary"));
    }
    @Test public void encodedMarkdownAndHtmlAssetReferencesCannotBypassSecurityChecks() {
        assertEquals(Set.of(7L),LedgerArticleService.assetIds("<img src='&#47;a&#9;pi/rule/ledger/assets/7/content'>"));
        assertEquals(Set.of(8L),LedgerArticleService.assetIds("![图片](\\/api\\/rule\\/ledger\\/assets\\/8\\/content)"));
        assertEquals(Set.of(9L),LedgerArticleService.assetIds("<img src='&amp;#47;api/rule/ledger/assets/9/content'>"));
    }
    @Test public void ownerCanExplicitlyShareCleanPrivateMaterialUsedByMultipleDrafts() throws Exception {
        scanResult.set(new LedgerAntivirus.Result("CLEAN","检测通过"));
        var asset=assets.upload(new MockMultipartFile("file","shared.png","image/png",png()));long assetId=number(asset.get("id"));
        String body="![图](/api/rule/ledger/assets/"+assetId+"/content)";
        var one=draft(content("文章一",body));var two=draft(content("文章二",body));
        assertThrows(IllegalArgumentException.class,()->publish(one));
        assertEquals(true,assets.get(assetId).get("canChangeScope"));
        assets.update(assetId,new LedgerRequests.Asset("shared.png","PROJECT",1L,number(assets.get(assetId).get("version"))));
        publish(one);publish(two);
        assertEquals(false,assets.get(assetId).get("canChangeScope"));
        assertThrows(IllegalArgumentException.class,()->assets.delete(List.of(assetId)));
    }
    @Test public void publishedDatesUseTheExistingLocalDateTimeJsonContractWithoutTimezoneShift() throws Exception {
        var input = content("时间契约", "正文"); var published = publish(draft(input));
        assertEquals(input.getOccurredAt(), published.get("occurredAt"));
        assertTrue(json.writeValueAsString(published).contains("2026-11-01T10:20:30"));
        @SuppressWarnings("unchecked") var history = (List<Map<String,Object>>) articles.workspace(number(published.get("articleId"))).get("history");
        var restored = json.readValue((String) history.get(0).get("snapshotJson"), LedgerContent.class);
        assertEquals(input.getOccurredAt(), restored.getOccurredAt());
        assertTrue(history.get(0).get("createTime") instanceof LocalDateTime);
    }
}
