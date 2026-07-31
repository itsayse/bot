package dev.observer.keyauth.data;

public class PlayerRecord {
    public String uuid;
    public String passwordHash;
    public String salt;
    public String keyUsed;
    public long registeredAt;
    public String discordId;

    public PlayerRecord() {
    }

    public PlayerRecord(String uuid, String passwordHash, String salt, String keyUsed, long registeredAt) {
        this(uuid, passwordHash, salt, keyUsed, registeredAt, null);
    }

    public PlayerRecord(String uuid, String passwordHash, String salt, String keyUsed, long registeredAt, String discordId) {
        this.uuid = uuid;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.keyUsed = keyUsed;
        this.registeredAt = registeredAt;
        this.discordId = discordId;
    }
}