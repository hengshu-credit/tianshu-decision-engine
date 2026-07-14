package com.hengshucredit.rule.client.auth;

import org.junit.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.Assert.assertEquals;

public class TokenExchangeManagerTest {

    @Test
    public void exchangesOnceAndReusesTokenBeforeRefreshWindow() throws Exception {
        LocalDateTime issued = LocalDateTime.of(2026, 7, 15, 8, 0);
        FakeTokenExchangeManager manager = manager(issued);
        manager.responses.add(state("token-a", issued.plusHours(2), issued.plusHours(2).plusMinutes(10)));

        assertEquals("token-a", manager.getAccessToken());
        manager.now = issued.plusHours(1);
        assertEquals("token-a", manager.getAccessToken());
        assertEquals(1, manager.exchangeCount);
    }

    @Test
    public void refreshesSixtySecondsBeforeExpiration() throws Exception {
        LocalDateTime issued = LocalDateTime.of(2026, 7, 15, 8, 0);
        FakeTokenExchangeManager manager = manager(issued);
        manager.responses.add(state("token-a", issued.plusHours(2), issued.plusHours(2).plusMinutes(10)));
        manager.responses.add(state("token-b", issued.plusHours(4), issued.plusHours(4).plusMinutes(10)));

        assertEquals("token-a", manager.getAccessToken());
        manager.now = issued.plusHours(2).minusSeconds(60);

        assertEquals("token-b", manager.getAccessToken());
        assertEquals(2, manager.exchangeCount);
    }

    @Test
    public void failedRefreshKeepsOldTokenThroughGracePeriod() throws Exception {
        LocalDateTime issued = LocalDateTime.of(2026, 7, 15, 8, 0);
        FakeTokenExchangeManager manager = manager(issued);
        manager.responses.add(state("token-a", issued.plusHours(2), issued.plusHours(2).plusMinutes(10)));

        assertEquals("token-a", manager.getAccessToken());
        manager.now = issued.plusHours(2).plusMinutes(5);
        manager.failure = new IOException("temporary outage");

        assertEquals("token-a", manager.getAccessToken());
        assertEquals(2, manager.exchangeCount);
    }

    @Test(expected = IOException.class)
    public void failedRefreshAfterGraceDoesNotSendExpiredToken() throws Exception {
        LocalDateTime issued = LocalDateTime.of(2026, 7, 15, 8, 0);
        FakeTokenExchangeManager manager = manager(issued);
        manager.responses.add(state("token-a", issued.plusHours(2), issued.plusHours(2).plusMinutes(10)));
        manager.getAccessToken();
        manager.now = issued.plusHours(2).plusMinutes(10).plusSeconds(1);
        manager.failure = new IOException("temporary outage");

        manager.getAccessToken();
    }

    private FakeTokenExchangeManager manager(LocalDateTime now) {
        ClientAuthConfig config = ClientAuthConfig.basic("partner", "secret");
        config.setRefreshAheadSeconds(60);
        return new FakeTokenExchangeManager(config, now);
    }

    private TokenExchangeManager.TokenState state(String token, LocalDateTime expiresAt,
                                                   LocalDateTime graceExpiresAt) {
        return new TokenExchangeManager.TokenState(token, expiresAt, graceExpiresAt);
    }

    private static class FakeTokenExchangeManager extends TokenExchangeManager {
        private final Queue<TokenState> responses = new ArrayDeque<>();
        private LocalDateTime now;
        private IOException failure;
        private int exchangeCount;

        private FakeTokenExchangeManager(ClientAuthConfig config, LocalDateTime now) {
            super("http://localhost:8080", 1000, config);
            this.now = now;
        }

        @Override
        protected TokenState requestToken() throws IOException {
            exchangeCount++;
            if (failure != null) throw failure;
            return responses.remove();
        }

        @Override
        protected LocalDateTime currentDateTime() {
            return now;
        }
    }
}
