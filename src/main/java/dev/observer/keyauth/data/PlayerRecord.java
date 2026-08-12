package dev.observer.keyauth.data;

public class PlayerRecord {
   public String uuid;
   public String keyUsed;
   public long registeredAt;
   public String discordId;

   public PlayerRecord() {
   }

   public PlayerRecord(String uuid, String keyUsed, long registeredAt, String discordId) {
      this.uuid = uuid;
      this.keyUsed = keyUsed;
      this.registeredAt = registeredAt;
      this.discordId = discordId;
   }
}
