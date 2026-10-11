package com.hengshucredit.rule.server.governance;

/** A deletion either completes immediately or creates an approval draft, never both. */
public record GovernanceDeletionResult(boolean deleted, Long approvalRequestId) { }
