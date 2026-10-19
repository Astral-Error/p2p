package com.project.p2p.client.model;

public record PresenceSession(String sessionId, String observedIp, int listeningPort, String status) {}
