package com.hengshucredit.rule.server.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hengshucredit.rule.model.dto.ProjectAuthDTO;
import com.hengshucredit.rule.model.dto.ProjectAuthSaveRequest;
import com.hengshucredit.rule.model.dto.ProjectAuthTokenDTO;
import com.hengshucredit.rule.model.dto.ProjectTokenResponse;
import com.hengshucredit.rule.model.entity.RuleAuthAccessLog;
import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.model.entity.RuleProjectAuth;
import com.hengshucredit.rule.model.entity.RuleProjectAuthToken;
import com.hengshucredit.rule.server.auth.CachedBodyHttpServletRequest;
import com.hengshucredit.rule.server.auth.CredentialCipher;
import com.hengshucredit.rule.server.auth.HmacRequestSigner;
import com.hengshucredit.rule.server.auth.ProjectAuthReplayGuard;
import com.hengshucredit.rule.server.auth.ProjectAuthContext;
import com.hengshucredit.rule.server.auth.ProjectAuthProperties;
import com.hengshucredit.rule.server.auth.ProjectAuthType;
import com.hengshucredit.rule.server.auth.ProjectTokenRateLimiter;
import com.hengshucredit.rule.server.mapper.RuleAuthAccessLogMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectAuthMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectAuthTokenMapper;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ProjectAuthService {

    private static final int DEFAULT_TOKEN_TTL_SECONDS = 7200;
    private static final int DEFAULT_TOKEN_GRACE_SECONDS = 600;

    private final CredentialCipher credentialCipher;
    private final SecureRandom secureRandom = new SecureRandom();

    @Resource
    private RuleProjectAuthMapper authMapper;

    @Resource
    private RuleProjectMapper projectMapper;

    @Resource
    private ProjectAuthProperties properties;

    @Resource
    private ProjectAuthReplayGuard replayGuard;

    @Resource
    private RuleAuthAccessLogMapper accessLogMapper;

    @Resource
    private RuleProjectAuthTokenMapper tokenMapper;

    @Resource
    private ProjectTokenRateLimiter tokenRateLimiter;

    public ProjectAuthService(CredentialCipher credentialCipher) {
        this.credentialCipher = credentialCipher;
    }

    public ProjectAuthContext authenticate(HttpServletRequest request) {
        if (request == null) return null;

        String authorization = request.getHeader("Authorization");
        if (startsWithIgnoreCase(authorization, "Basic ")) {
            return authenticateBasic(authorization.substring(6).trim());
        }

        if (StringUtils.hasText(request.getHeader("X-Rule-Access-Key"))) {
            return authenticateHmac(request);
        }

        if (startsWithIgnoreCase(authorization, "Bearer ")) {
            String bearer = authorization.substring(7).trim();
            ProjectAuthContext tokenContext = authenticateBearerToken(bearer);
            if (tokenContext != null) {
                return isTokenEndpoint(request) ? null : tokenContext;
            }
            return authenticateLegacyToken(bearer);
        }

        String legacyToken = extractLegacyToken(request, authorization);
        if (StringUtils.hasText(legacyToken)) {
            return authenticateLegacyToken(legacyToken);
        }

        ProjectAuthContext bodyContext = authenticateTokenBody(request);
        if (bodyContext != null) return bodyContext;

        return authenticateApiKey(request);
    }

    public ProjectAuthContext authenticateLegacyToken(String token) {
        if (!StringUtils.hasText(token)) return null;
        RuleProjectAuth auth = findEnabledAuthByLookupKey(
                credentialCipher.lookupKey(ProjectAuthType.LEGACY_TOKEN, token));
        if (!isUsable(auth, ProjectAuthType.LEGACY_TOKEN) || !matchesSecret(auth, token)) {
            return null;
        }
        return directContext(auth);
    }

    public String getLegacyToken(Long projectId) {
        if (projectId == null) return null;
        RuleProjectAuth auth = findAuthByCode(legacyAuthCode(projectId));
        if (auth == null || !projectId.equals(auth.getProjectId())) return null;
        return credentialCipher.decrypt(auth.getSecretCiphertext());
    }

    @Transactional
    public ProjectTokenResponse issueToken(ProjectAuthContext context) {
        if (context == null || context.isTemporaryToken()) {
            throw new IllegalArgumentException("Base project credential is required");
        }
        RuleProjectAuth auth = findAuthById(context.getAuthId());
        if (auth == null || !Integer.valueOf(1).equals(auth.getStatus())
                || !context.getProjectId().equals(auth.getProjectId())) {
            throw new IllegalArgumentException("Project credential is not available");
        }
        RuleProject project = findProjectById(context.getProjectId());
        if (project == null || !Integer.valueOf(1).equals(project.getStatus())) {
            throw new IllegalArgumentException("Project is not available");
        }

        LocalDateTime now = currentDateTime();
        String tokenValue = generateTokenValue();
        RuleProjectAuthToken token = new RuleProjectAuthToken();
        token.setProjectId(context.getProjectId());
        token.setAuthId(auth.getId());
        token.setTokenCode("TOKEN_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        token.setLookupKey(credentialCipher.lookupKey(ProjectAuthType.BEARER_TOKEN, tokenValue));
        token.setTokenCiphertext(credentialCipher.encrypt(tokenValue));
        token.setIssuedTime(now);
        token.setExpireTime(now.plusSeconds(tokenTtlSeconds(auth)));
        token.setGraceExpireTime(token.getExpireTime().plusSeconds(tokenGraceSeconds(auth)));
        token.setStatus(1);
        insertToken(token);

        ProjectTokenResponse response = new ProjectTokenResponse();
        response.setAccessToken(tokenValue);
        response.setTokenType("Bearer");
        response.setTokenCode(token.getTokenCode());
        response.setProjectCode(context.getProjectCode());
        response.setAuthCode(auth.getAuthCode());
        response.setAuthType(auth.getAuthType());
        response.setIssuedAt(token.getIssuedTime());
        response.setExpiresAt(token.getExpireTime());
        response.setGraceExpiresAt(token.getGraceExpireTime());
        return response;
    }

    public List<ProjectAuthDTO> listAuths(Long projectId) {
        requireProject(projectId);
        List<ProjectAuthDTO> result = new ArrayList<>();
        for (RuleProjectAuth auth : findAuthsByProject(projectId)) {
            result.add(toAuthDto(auth, false));
        }
        return result;
    }

    @Transactional
    public ProjectAuthDTO createAuth(Long projectId, ProjectAuthSaveRequest request) {
        requireProject(projectId);
        if (request == null) throw new IllegalArgumentException("Authentication configuration is required");
        String authType = normalizeAuthType(request.getAuthType());
        if (ProjectAuthType.LEGACY_TOKEN.equals(authType)) {
            throw new IllegalArgumentException("Legacy token can only be managed through the compatibility endpoint");
        }
        RuleProjectAuth auth = new RuleProjectAuth();
        auth.setProjectId(projectId);
        auth.setAuthCode(StringUtils.hasText(request.getAuthCode())
                ? request.getAuthCode().trim()
                : "AUTH_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        if (findAuthByCode(auth.getAuthCode()) != null) {
            throw new IllegalArgumentException("Authentication code already exists");
        }
        auth.setAuthType(authType);
        applyAuthRequest(auth, request, true);
        insertAuth(auth);
        return toAuthDto(auth, false);
    }

    @Transactional
    public ProjectAuthDTO updateAuth(Long projectId, Long authId, ProjectAuthSaveRequest request) {
        RuleProjectAuth auth = requireAuth(projectId, authId);
        if (request == null) throw new IllegalArgumentException("Authentication configuration is required");
        if (StringUtils.hasText(request.getAuthCode()) && !auth.getAuthCode().equals(request.getAuthCode().trim())) {
            throw new IllegalArgumentException("Authentication code cannot be changed");
        }
        if (StringUtils.hasText(request.getAuthType())
                && !auth.getAuthType().equals(normalizeAuthType(request.getAuthType()))) {
            throw new IllegalArgumentException("Authentication type cannot be changed");
        }
        applyAuthRequest(auth, request, false);
        updateAuth(auth);
        return toAuthDto(auth, false);
    }

    public ProjectAuthDTO setAuthStatus(Long projectId, Long authId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new IllegalArgumentException("Authentication status must be 0 or 1");
        }
        RuleProjectAuth auth = requireAuth(projectId, authId);
        auth.setStatus(status);
        updateAuth(auth);
        return toAuthDto(auth, false);
    }

    public ProjectAuthDTO getFullAuth(Long projectId, Long authId) {
        return toAuthDto(requireAuth(projectId, authId), true);
    }

    public IPage<ProjectAuthTokenDTO> pageTokens(Long projectId, Long authId, int pageNum, int pageSize) {
        requireAuth(projectId, authId);
        IPage<RuleProjectAuthToken> source = findTokenPage(projectId, authId, pageNum, pageSize);
        Page<ProjectAuthTokenDTO> result = new Page<>(pageNum, pageSize, source.getTotal());
        List<ProjectAuthTokenDTO> records = new ArrayList<>();
        for (RuleProjectAuthToken token : source.getRecords()) {
            records.add(toTokenDto(token, false));
        }
        result.setRecords(records);
        return result;
    }

    public ProjectAuthTokenDTO getFullToken(Long projectId, Long authId, Long tokenId) {
        requireAuth(projectId, authId);
        return toTokenDto(requireToken(projectId, authId, tokenId), true);
    }

    public ProjectAuthTokenDTO revokeToken(Long projectId, Long authId, Long tokenId) {
        requireAuth(projectId, authId);
        RuleProjectAuthToken token = requireToken(projectId, authId, tokenId);
        token.setStatus(0);
        token.setRevokedTime(currentDateTime());
        updateToken(token);
        return toTokenDto(token, false);
    }

    public IPage<RuleAuthAccessLog> pageAccessLogs(Long projectId, int pageNum, int pageSize,
                                                   String authCode, String tokenCode, Integer success) {
        requireProject(projectId);
        LambdaQueryWrapper<RuleAuthAccessLog> wrapper = new LambdaQueryWrapper<RuleAuthAccessLog>()
                .eq(RuleAuthAccessLog::getProjectId, projectId);
        if (StringUtils.hasText(authCode)) wrapper.like(RuleAuthAccessLog::getAuthCode, authCode);
        if (StringUtils.hasText(tokenCode)) wrapper.like(RuleAuthAccessLog::getTokenCode, tokenCode);
        if (success != null) wrapper.eq(RuleAuthAccessLog::getSuccess, success);
        wrapper.orderByDesc(RuleAuthAccessLog::getCreateTime);
        return accessLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public boolean isTokenRequestAllowed(HttpServletRequest request) {
        return tokenRateLimiter.isAllowed(resolveClientIp(request), tokenCredentialFingerprint(request));
    }

    public void recordTokenFailure(HttpServletRequest request) {
        tokenRateLimiter.recordFailure(resolveClientIp(request), tokenCredentialFingerprint(request));
    }

    public void clearTokenFailures(HttpServletRequest request) {
        tokenRateLimiter.clearFailures(resolveClientIp(request), tokenCredentialFingerprint(request));
    }

    public RuleProjectAuth saveLegacyToken(RuleProject project, String token) {
        if (project == null || project.getId() == null || !StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Project and legacy token are required");
        }
        String authCode = legacyAuthCode(project.getId());
        RuleProjectAuth auth = findAuthByCode(authCode);
        if (auth == null) {
            auth = new RuleProjectAuth();
            auth.setProjectId(project.getId());
            auth.setAuthCode(authCode);
            auth.setAuthName("默认访问令牌");
            auth.setAuthType(ProjectAuthType.LEGACY_TOKEN);
            auth.setTokenTtlSeconds(DEFAULT_TOKEN_TTL_SECONDS);
            auth.setTokenGraceSeconds(DEFAULT_TOKEN_GRACE_SECONDS);
            auth.setStatus(1);
            setSecret(auth, token, token);
            insertAuth(auth);
        } else {
            if (!project.getId().equals(auth.getProjectId())) {
                throw new IllegalStateException("Legacy authentication code belongs to another project");
            }
            setSecret(auth, token, token);
            auth.setStatus(1);
            updateAuth(auth);
        }
        return auth;
    }

    public void recordAccess(HttpServletRequest request, ProjectAuthContext context,
                             boolean success, String failureReason) {
        if (request == null) return;
        RuleAuthAccessLog accessLog = new RuleAuthAccessLog();
        if (context != null) {
            accessLog.setProjectId(context.getProjectId());
            accessLog.setProjectCode(context.getProjectCode());
            accessLog.setAuthId(context.getAuthId());
            accessLog.setAuthCode(context.getAuthCode());
            accessLog.setAuthType(context.getAuthType());
            accessLog.setTokenId(context.getTokenId());
            accessLog.setTokenCode(context.getTokenCode());
            accessLog.setAuthPhase(context.getAuthPhase());
        }
        accessLog.setRequestMethod(truncate(request.getMethod(), 16));
        accessLog.setRequestUri(truncate(request.getRequestURI(), 1024));
        accessLog.setRequestId(resolveRequestId(request));
        accessLog.setClientIp(truncate(resolveClientIp(request), 64));
        accessLog.setSuccess(success ? 1 : 0);
        accessLog.setFailureReason(success ? null : truncate(failureReason, 512));
        try {
            insertAccessLog(accessLog);
        } catch (RuntimeException e) {
            log.error("Unable to persist project authentication access log", e);
        }
    }

    public int migrateLegacyTokens() {
        int migrated = 0;
        for (RuleProject project : findProjectsWithLegacyToken()) {
            String token = project.getAccessToken();
            if (!StringUtils.hasText(token)) continue;
            saveLegacyToken(project, token);
            project.setAccessToken(null);
            updateProject(project);
            migrated++;
        }
        return migrated;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrateLegacyTokensOnStartup() {
        int migrated = migrateLegacyTokens();
        if (migrated > 0) {
            log.info("Migrated {} legacy project access tokens to encrypted authentication records", migrated);
        }
    }

    protected RuleProjectAuth findEnabledAuthByLookupKey(String lookupKey) {
        return authMapper.selectOne(new LambdaQueryWrapper<RuleProjectAuth>()
                .eq(RuleProjectAuth::getLookupKey, lookupKey)
                .eq(RuleProjectAuth::getStatus, 1));
    }

    protected RuleProjectAuth findAuthByLookupKey(String lookupKey) {
        return authMapper.selectOne(new LambdaQueryWrapper<RuleProjectAuth>()
                .eq(RuleProjectAuth::getLookupKey, lookupKey));
    }

    protected List<RuleProjectAuth> findAuthsByProject(Long projectId) {
        return authMapper.selectList(new LambdaQueryWrapper<RuleProjectAuth>()
                .eq(RuleProjectAuth::getProjectId, projectId)
                .orderByDesc(RuleProjectAuth::getCreateTime));
    }

    protected List<RuleProjectAuth> findEnabledAuthByLookupKeys(List<String> lookupKeys) {
        if (lookupKeys == null || lookupKeys.isEmpty()) return Collections.emptyList();
        return authMapper.selectList(new LambdaQueryWrapper<RuleProjectAuth>()
                .in(RuleProjectAuth::getLookupKey, lookupKeys)
                .eq(RuleProjectAuth::getStatus, 1));
    }

    protected RuleProject findProjectById(Long projectId) {
        return projectMapper.selectById(projectId);
    }

    protected List<RuleProject> findProjectsWithLegacyToken() {
        return projectMapper.selectList(new LambdaQueryWrapper<RuleProject>()
                .isNotNull(RuleProject::getAccessToken)
                .ne(RuleProject::getAccessToken, ""));
    }

    protected RuleProjectAuth findAuthByCode(String authCode) {
        return authMapper.selectOne(new LambdaQueryWrapper<RuleProjectAuth>()
                .eq(RuleProjectAuth::getAuthCode, authCode));
    }

    protected RuleProjectAuth findAuthById(Long authId) {
        return authMapper.selectById(authId);
    }

    protected RuleProjectAuthToken findEnabledTokenByLookupKey(String lookupKey) {
        return tokenMapper.selectOne(new LambdaQueryWrapper<RuleProjectAuthToken>()
                .eq(RuleProjectAuthToken::getLookupKey, lookupKey)
                .eq(RuleProjectAuthToken::getStatus, 1));
    }

    protected RuleProjectAuthToken findTokenById(Long tokenId) {
        return tokenMapper.selectById(tokenId);
    }

    protected IPage<RuleProjectAuthToken> findTokenPage(Long projectId, Long authId,
                                                        int pageNum, int pageSize) {
        return tokenMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<RuleProjectAuthToken>()
                        .eq(RuleProjectAuthToken::getProjectId, projectId)
                        .eq(RuleProjectAuthToken::getAuthId, authId)
                        .orderByDesc(RuleProjectAuthToken::getIssuedTime));
    }

    protected void insertAuth(RuleProjectAuth auth) {
        authMapper.insert(auth);
    }

    protected void updateAuth(RuleProjectAuth auth) {
        authMapper.updateById(auth);
    }

    protected void updateProject(RuleProject project) {
        projectMapper.updateById(project);
    }

    protected void insertAccessLog(RuleAuthAccessLog accessLog) {
        accessLogMapper.insert(accessLog);
    }

    protected void insertToken(RuleProjectAuthToken token) {
        tokenMapper.insert(token);
    }

    protected void updateToken(RuleProjectAuthToken token) {
        tokenMapper.updateById(token);
    }

    protected LocalDateTime currentDateTime() {
        return LocalDateTime.now();
    }

    protected long currentEpochSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    protected long hmacTimeWindowSeconds() {
        return properties.getHmacTimeWindowSeconds();
    }

    protected boolean claimHmacNonce(Long authId, String nonce) {
        return replayGuard.claim(authId, nonce, hmacTimeWindowSeconds() * 2L + 1L);
    }

    private ProjectAuthContext authenticateBasic(String encodedCredentials) {
        try {
            String credentials = new String(Base64.getDecoder().decode(encodedCredentials), StandardCharsets.UTF_8);
            int separator = credentials.indexOf(':');
            if (separator <= 0) return null;
            return authenticateBasicCredentials(
                    credentials.substring(0, separator), credentials.substring(separator + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ProjectAuthContext authenticateBasicCredentials(String username, String password) {
        if (!StringUtils.hasText(username) || password == null) return null;
        RuleProjectAuth auth = findEnabledAuthByLookupKey(
                credentialCipher.lookupKey(ProjectAuthType.BASIC, username));
        if (!isUsable(auth, ProjectAuthType.BASIC) || !matchesSecret(auth, password)) return null;
        return directContext(auth);
    }

    private ProjectAuthContext authenticateBearerToken(String tokenValue) {
        if (!StringUtils.hasText(tokenValue)) return null;
        RuleProjectAuthToken token = findEnabledTokenByLookupKey(
                credentialCipher.lookupKey(ProjectAuthType.BEARER_TOKEN, tokenValue));
        if (token == null || !matchesToken(token, tokenValue)) return null;
        RuleProjectAuth auth = findAuthById(token.getAuthId());
        if (!isUsable(auth, auth == null ? null : auth.getAuthType())
                || !token.getProjectId().equals(auth.getProjectId())) {
            return null;
        }
        LocalDateTime now = currentDateTime();
        if (token.getExpireTime() == null || token.getGraceExpireTime() == null
                || now.isAfter(token.getGraceExpireTime())) {
            return null;
        }
        String phase = now.isAfter(token.getExpireTime()) ? "GRACE" : "VALID";
        ProjectAuthContext context = directContext(auth);
        if (context == null) return null;
        token.setLastUsedTime(now);
        try {
            updateToken(token);
        } catch (RuntimeException e) {
            log.warn("Unable to update project token last-used time: {}", token.getTokenCode(), e);
        }
        return ProjectAuthContext.temporary(context.getProjectId(), context.getProjectCode(),
                context.getAuthId(), context.getAuthCode(), context.getAuthType(), token.getId(),
                token.getTokenCode(), phase);
    }

    private ProjectAuthContext authenticateTokenBody(HttpServletRequest request) {
        if (!isTokenEndpoint(request) || !(request instanceof CachedBodyHttpServletRequest)) return null;
        byte[] body = ((CachedBodyHttpServletRequest) request).getCachedBody();
        if (body.length == 0) return null;
        try {
            JSONObject json = JSON.parseObject(new String(body, StandardCharsets.UTF_8));
            return authenticateBasicCredentials(json.getString("username"), json.getString("password"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private ProjectAuthContext authenticateApiKey(HttpServletRequest request) {
        List<PresentedCredential> credentials = collectPresentedCredentials(request);
        if (credentials.isEmpty()) return null;
        Set<String> lookupKeys = new LinkedHashSet<>();
        for (PresentedCredential credential : credentials) {
            lookupKeys.add(credentialCipher.lookupKey(ProjectAuthType.API_KEY, credential.value));
        }
        List<RuleProjectAuth> matches = findEnabledAuthByLookupKeys(new ArrayList<>(lookupKeys));
        for (RuleProjectAuth auth : matches) {
            if (!isUsable(auth, ProjectAuthType.API_KEY)) continue;
            for (PresentedCredential credential : credentials) {
                if (auth.getLookupKey().equals(
                        credentialCipher.lookupKey(ProjectAuthType.API_KEY, credential.value))
                        && matchesApiKeyLocation(auth, credential)
                        && matchesSecret(auth, credential.value)) {
                    return directContext(auth);
                }
            }
        }
        return null;
    }

    private ProjectAuthContext authenticateHmac(HttpServletRequest request) {
        String accessKey = request.getHeader("X-Rule-Access-Key");
        String timestampText = request.getHeader("X-Rule-Timestamp");
        String nonce = request.getHeader("X-Rule-Nonce");
        String signature = request.getHeader("X-Rule-Signature");
        if (!StringUtils.hasText(timestampText) || !StringUtils.hasText(nonce)
                || !StringUtils.hasText(signature) || accessKey.length() > 128
                || nonce.length() > 128 || signature.length() != 64) {
            return null;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampText);
        } catch (NumberFormatException e) {
            return null;
        }
        long now = currentEpochSeconds();
        long window = hmacTimeWindowSeconds();
        if (timestamp < now - window || timestamp > now + window) return null;

        RuleProjectAuth auth = findEnabledAuthByLookupKey(
                credentialCipher.lookupKey(ProjectAuthType.HMAC_SHA256, accessKey));
        if (!isUsable(auth, ProjectAuthType.HMAC_SHA256)) return null;
        String expected = HmacRequestSigner.signHex(
                credentialCipher.decrypt(auth.getSecretCiphertext()),
                request.getMethod(), request.getRequestURI(), request.getQueryString(),
                timestampText, nonce, requestBody(request));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        if (!claimHmacNonce(auth.getId(), nonce)) return null;
        return directContext(auth);
    }

    private ProjectAuthContext directContext(RuleProjectAuth auth) {
        RuleProject project = findProjectById(auth.getProjectId());
        if (project == null || !Integer.valueOf(1).equals(project.getStatus())) return null;
        return ProjectAuthContext.direct(project.getId(), project.getProjectCode(), auth.getId(),
                auth.getAuthCode(), auth.getAuthType());
    }

    private boolean isUsable(RuleProjectAuth auth, String expectedType) {
        return auth != null
                && expectedType.equals(auth.getAuthType())
                && Integer.valueOf(1).equals(auth.getStatus());
    }

    private boolean matchesSecret(RuleProjectAuth auth, String presented) {
        String expected = credentialCipher.decrypt(auth.getSecretCiphertext());
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    private boolean matchesToken(RuleProjectAuthToken token, String presented) {
        String expected = credentialCipher.decrypt(token.getTokenCiphertext());
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    private boolean matchesApiKeyLocation(RuleProjectAuth auth, PresentedCredential credential) {
        if (!StringUtils.hasText(auth.getConfigJson())) return false;
        JSONObject config = JSON.parseObject(auth.getConfigJson());
        String placement = config.getString("placement");
        String parameterName = config.getString("parameterName");
        if (!credential.placement.equalsIgnoreCase(placement)) return false;
        return "HEADER".equalsIgnoreCase(placement)
                ? credential.name.equalsIgnoreCase(parameterName)
                : credential.name.equals(parameterName);
    }

    private List<PresentedCredential> collectPresentedCredentials(HttpServletRequest request) {
        List<PresentedCredential> credentials = new ArrayList<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if ("Authorization".equalsIgnoreCase(name)) continue;
            addCredential(credentials, "HEADER", name, request.getHeader(name));
        }
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames != null && parameterNames.hasMoreElements()) {
            String name = parameterNames.nextElement();
            addCredential(credentials, "QUERY", name, request.getParameter(name));
        }
        return credentials;
    }

    private void addCredential(List<PresentedCredential> credentials, String placement,
                               String name, String value) {
        if (StringUtils.hasText(name) && StringUtils.hasText(value)) {
            credentials.add(new PresentedCredential(placement, name, value));
        }
    }

    private String extractLegacyToken(HttpServletRequest request, String authorization) {
        String token = request.getHeader("X-Rule-Token");
        return StringUtils.hasText(token) ? token : request.getParameter("token");
    }

    private byte[] requestBody(HttpServletRequest request) {
        return request instanceof CachedBodyHttpServletRequest
                ? ((CachedBodyHttpServletRequest) request).getCachedBody()
                : new byte[0];
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-Id");
        if (!StringUtils.hasText(requestId)) {
            Object existing = request.getAttribute("requestId");
            requestId = existing == null ? UUID.randomUUID().toString() : String.valueOf(existing);
        }
        requestId = truncate(requestId, 128);
        request.setAttribute("requestId", requestId);
        return requestId;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private boolean isTokenEndpoint(HttpServletRequest request) {
        return request != null && "/api/rule/auth/token".equals(request.getRequestURI());
    }

    private int tokenTtlSeconds(RuleProjectAuth auth) {
        return auth.getTokenTtlSeconds() == null ? DEFAULT_TOKEN_TTL_SECONDS : auth.getTokenTtlSeconds();
    }

    private int tokenGraceSeconds(RuleProjectAuth auth) {
        return auth.getTokenGraceSeconds() == null ? DEFAULT_TOKEN_GRACE_SECONDS : auth.getTokenGraceSeconds();
    }

    private String generateTokenValue() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String tokenCredentialFingerprint(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        String locator = null;
        if (startsWithIgnoreCase(authorization, "Basic ")) {
            locator = basicUsername(authorization.substring(6).trim());
            if (locator != null) locator = "BASIC:" + locator;
        }
        if (locator == null && StringUtils.hasText(request.getHeader("X-Rule-Access-Key"))) {
            locator = "HMAC:" + request.getHeader("X-Rule-Access-Key");
        }
        if (locator == null && isTokenEndpoint(request) && request instanceof CachedBodyHttpServletRequest) {
            try {
                JSONObject json = JSON.parseObject(new String(
                        ((CachedBodyHttpServletRequest) request).getCachedBody(), StandardCharsets.UTF_8));
                if (StringUtils.hasText(json.getString("username"))) {
                    locator = "BASIC:" + json.getString("username");
                }
            } catch (RuntimeException ignored) {
            }
        }
        if (locator == null) {
            String token = StringUtils.hasText(authorization) ? authorization : request.getHeader("X-Rule-Token");
            if (!StringUtils.hasText(token)) token = request.getQueryString();
            if (!StringUtils.hasText(token)) token = customCredentialLocator(request);
            if (!StringUtils.hasText(token)) {
                token = HmacRequestSigner.sha256Hex(requestBody(request));
            }
            locator = "PRESENTED:" + token;
        }
        return credentialCipher.lookupKey("TOKEN_RATE", locator);
    }

    private String customCredentialLocator(HttpServletRequest request) {
        StringBuilder locator = new StringBuilder();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (name.regionMatches(true, 0, "X-", 0, 2)
                    && !"X-Forwarded-For".equalsIgnoreCase(name)
                    && !"X-Request-Id".equalsIgnoreCase(name)) {
                locator.append(name.toLowerCase(Locale.ROOT)).append('=')
                        .append(request.getHeader(name)).append('&');
            }
        }
        return locator.length() == 0 ? null : locator.toString();
    }

    private String basicUsername(String encodedCredentials) {
        try {
            String credentials = new String(Base64.getDecoder().decode(encodedCredentials), StandardCharsets.UTF_8);
            int separator = credentials.indexOf(':');
            return separator <= 0 ? null : credentials.substring(0, separator);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean startsWithIgnoreCase(String value, String prefix) {
        return value != null && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private void applyAuthRequest(RuleProjectAuth auth, ProjectAuthSaveRequest request, boolean creating) {
        if (StringUtils.hasText(request.getAuthName())) {
            auth.setAuthName(request.getAuthName().trim());
        } else if (creating) {
            auth.setAuthName(auth.getAuthCode());
        }
        auth.setTokenTtlSeconds(resolvePositiveSeconds(request.getTokenTtlSeconds(),
                auth.getTokenTtlSeconds(), DEFAULT_TOKEN_TTL_SECONDS, "Token validity"));
        auth.setTokenGraceSeconds(resolveNonNegativeSeconds(request.getTokenGraceSeconds(),
                auth.getTokenGraceSeconds(), DEFAULT_TOKEN_GRACE_SECONDS, "Token grace period"));
        if (request.getStatus() != null) {
            if (request.getStatus() != 0 && request.getStatus() != 1) {
                throw new IllegalArgumentException("Authentication status must be 0 or 1");
            }
            auth.setStatus(request.getStatus());
        } else if (creating) {
            auth.setStatus(1);
        }

        String identifier = currentIdentifier(auth, request.getIdentifier());
        String secret = currentSecret(auth, request.getSecret());
        if (ProjectAuthType.BASIC.equals(auth.getAuthType())) {
            requireText(identifier, "Basic username is required");
            requireText(secret, "Basic password is required");
            auth.setConfigJson(null);
        } else if (ProjectAuthType.HMAC_SHA256.equals(auth.getAuthType())) {
            if (!StringUtils.hasText(identifier) && creating) identifier = "AK_" + generateCredentialValue();
            if (!StringUtils.hasText(secret) && creating) secret = generateCredentialValue();
            requireText(identifier, "HMAC access key is required");
            requireText(secret, "HMAC secret is required");
            auth.setConfigJson(null);
        } else if (ProjectAuthType.API_KEY.equals(auth.getAuthType())) {
            if (!StringUtils.hasText(secret) && creating) secret = generateCredentialValue();
            requireText(secret, "API key is required");
            identifier = null;
            auth.setConfigJson(apiKeyConfig(auth.getConfigJson(), request));
        }

        String lookupIdentifier = identifier == null ? secret : identifier;
        String lookupKey = credentialCipher.lookupKey(auth.getAuthType(), lookupIdentifier);
        RuleProjectAuth existing = findAuthByLookupKey(lookupKey);
        if (existing != null && (auth.getId() == null || !auth.getId().equals(existing.getId()))) {
            throw new IllegalArgumentException("Authentication credential already exists");
        }
        auth.setLookupKey(lookupKey);
        auth.setIdentifierCiphertext(credentialCipher.encrypt(identifier));
        auth.setSecretCiphertext(credentialCipher.encrypt(secret));
    }

    private String normalizeAuthType(String authType) {
        if (!StringUtils.hasText(authType)) throw new IllegalArgumentException("Authentication type is required");
        String normalized = authType.trim().toUpperCase(Locale.ROOT);
        if (!ProjectAuthType.LEGACY_TOKEN.equals(normalized)
                && !ProjectAuthType.API_KEY.equals(normalized)
                && !ProjectAuthType.BASIC.equals(normalized)
                && !ProjectAuthType.HMAC_SHA256.equals(normalized)) {
            throw new IllegalArgumentException("Unsupported authentication type");
        }
        return normalized;
    }

    private String currentIdentifier(RuleProjectAuth auth, String requested) {
        if (StringUtils.hasText(requested)) return requested;
        return auth.getIdentifierCiphertext() == null ? null
                : credentialCipher.decrypt(auth.getIdentifierCiphertext());
    }

    private String currentSecret(RuleProjectAuth auth, String requested) {
        if (StringUtils.hasText(requested)) return requested;
        return auth.getSecretCiphertext() == null ? null : credentialCipher.decrypt(auth.getSecretCiphertext());
    }

    private String apiKeyConfig(String currentConfig, ProjectAuthSaveRequest request) {
        JSONObject config = StringUtils.hasText(currentConfig) ? JSON.parseObject(currentConfig) : new JSONObject();
        String placement = StringUtils.hasText(request.getPlacement())
                ? request.getPlacement().trim().toUpperCase(Locale.ROOT)
                : config.getString("placement");
        if (!StringUtils.hasText(placement)) placement = "HEADER";
        if (!"HEADER".equals(placement) && !"QUERY".equals(placement)) {
            throw new IllegalArgumentException("API key placement must be HEADER or QUERY");
        }
        String parameterName = StringUtils.hasText(request.getParameterName())
                ? request.getParameterName().trim()
                : config.getString("parameterName");
        if (!StringUtils.hasText(parameterName)) parameterName = "X-Rule-Api-Key";
        config.put("placement", placement);
        config.put("parameterName", parameterName);
        return config.toJSONString();
    }

    private ProjectAuthDTO toAuthDto(RuleProjectAuth auth, boolean full) {
        ProjectAuthDTO dto = new ProjectAuthDTO();
        dto.setId(auth.getId());
        dto.setProjectId(auth.getProjectId());
        dto.setAuthCode(auth.getAuthCode());
        dto.setAuthName(auth.getAuthName());
        dto.setAuthType(auth.getAuthType());
        String identifier = auth.getIdentifierCiphertext() == null ? null
                : credentialCipher.decrypt(auth.getIdentifierCiphertext());
        String secret = credentialCipher.decrypt(auth.getSecretCiphertext());
        dto.setIdentifierMasked(mask(identifier));
        dto.setSecretMasked(mask(secret));
        if (full) {
            dto.setIdentifier(identifier);
            dto.setSecret(secret);
        }
        if (StringUtils.hasText(auth.getConfigJson())) {
            JSONObject config = JSON.parseObject(auth.getConfigJson());
            dto.setPlacement(config.getString("placement"));
            dto.setParameterName(config.getString("parameterName"));
        }
        dto.setTokenTtlSeconds(auth.getTokenTtlSeconds());
        dto.setTokenGraceSeconds(auth.getTokenGraceSeconds());
        dto.setStatus(auth.getStatus());
        dto.setCreateTime(auth.getCreateTime());
        dto.setUpdateTime(auth.getUpdateTime());
        return dto;
    }

    private ProjectAuthTokenDTO toTokenDto(RuleProjectAuthToken token, boolean full) {
        ProjectAuthTokenDTO dto = new ProjectAuthTokenDTO();
        dto.setId(token.getId());
        dto.setProjectId(token.getProjectId());
        dto.setAuthId(token.getAuthId());
        dto.setTokenCode(token.getTokenCode());
        String value = credentialCipher.decrypt(token.getTokenCiphertext());
        dto.setTokenMasked(mask(value));
        if (full) dto.setAccessToken(value);
        dto.setIssuedTime(token.getIssuedTime());
        dto.setExpireTime(token.getExpireTime());
        dto.setGraceExpireTime(token.getGraceExpireTime());
        dto.setLastUsedTime(token.getLastUsedTime());
        dto.setRevokedTime(token.getRevokedTime());
        dto.setStatus(token.getStatus());
        return dto;
    }

    private RuleProject requireProject(Long projectId) {
        RuleProject project = projectId == null ? null : findProjectById(projectId);
        if (project == null) throw new IllegalArgumentException("Project not found");
        return project;
    }

    private RuleProjectAuth requireAuth(Long projectId, Long authId) {
        requireProject(projectId);
        RuleProjectAuth auth = authId == null ? null : findAuthById(authId);
        if (auth == null || !projectId.equals(auth.getProjectId())) {
            throw new IllegalArgumentException("Authentication configuration not found for project");
        }
        return auth;
    }

    private RuleProjectAuthToken requireToken(Long projectId, Long authId, Long tokenId) {
        RuleProjectAuthToken token = tokenId == null ? null : findTokenById(tokenId);
        if (token == null || !projectId.equals(token.getProjectId()) || !authId.equals(token.getAuthId())) {
            throw new IllegalArgumentException("Project token not found for authentication configuration");
        }
        return token;
    }

    private int resolvePositiveSeconds(Integer requested, Integer current, int defaultValue, String label) {
        int value = requested != null ? requested : current != null ? current : defaultValue;
        if (value <= 0) throw new IllegalArgumentException(label + " must be greater than zero");
        return value;
    }

    private int resolveNonNegativeSeconds(Integer requested, Integer current, int defaultValue, String label) {
        int value = requested != null ? requested : current != null ? current : defaultValue;
        if (value < 0) throw new IllegalArgumentException(label + " cannot be negative");
        return value;
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) throw new IllegalArgumentException(message);
    }

    private String mask(String value) {
        if (!StringUtils.hasText(value)) return null;
        if (value.length() < 8) return "****";
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private String generateCredentialValue() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void setSecret(RuleProjectAuth auth, String identifier, String secret) {
        auth.setLookupKey(credentialCipher.lookupKey(auth.getAuthType(), identifier));
        auth.setIdentifierCiphertext(credentialCipher.encrypt(identifier));
        auth.setSecretCiphertext(credentialCipher.encrypt(secret));
    }

    private String legacyAuthCode(Long projectId) {
        return "LEGACY_" + projectId;
    }

    private static class PresentedCredential {
        private final String placement;
        private final String name;
        private final String value;

        private PresentedCredential(String placement, String name, String value) {
            this.placement = placement;
            this.name = name;
            this.value = value;
        }
    }
}
