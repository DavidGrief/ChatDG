package ru.davidgrief.chatdg.models;

import java.util.UUID;

public final class MuteRecord {
    private final UUID playerUuid;
    private final String playerName;
    private final UUID adminUuid;
    private final String adminName;
    private final String reason;
    private final long issuedAt;
    private final long expiresAt;
    private final MuteType type;

    public MuteRecord(UUID playerUuid, String playerName, UUID adminUuid, String adminName,
                      String reason, long issuedAt, long expiresAt, MuteType type) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.adminUuid = adminUuid;
        this.adminName = adminName;
        this.reason = reason;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.type = type;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public UUID getAdminUuid() { return adminUuid; }
    public String getAdminName() { return adminName; }
    public String getReason() { return reason; }
    public long getIssuedAt() { return issuedAt; }
    public long getExpiresAt() { return expiresAt; }
    public MuteType getType() { return type; }

    public boolean isExpired(long now) {
        return type == MuteType.TIMED && expiresAt > 0L && expiresAt <= now;
    }
}
