package dev.observer.keyauth.session;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
   private final Map<UUID, AuthState> states = new ConcurrentHashMap();
   private final Map<UUID, Long> keyGraceDeadline = new ConcurrentHashMap();

   public AuthState get(UUID uuid) {
      return (AuthState)this.states.getOrDefault(uuid, AuthState.AWAITING_KEY);
   }

   public void set(UUID uuid, AuthState state) {
      this.states.put(uuid, state);
   }

   public void remove(UUID uuid) {
      this.states.remove(uuid);
      this.keyGraceDeadline.remove(uuid);
   }

   public void startKeyGrace(UUID uuid, long deadlineMillis) {
      this.keyGraceDeadline.putIfAbsent(uuid, deadlineMillis);
   }

   public boolean isInKeyGrace(UUID uuid) {
      return this.keyGraceDeadline.containsKey(uuid);
   }

   public boolean keyGraceExpired(UUID uuid) {
      Long deadline = (Long)this.keyGraceDeadline.get(uuid);
      return deadline != null && System.currentTimeMillis() > deadline;
   }

   public void clearKeyGrace(UUID uuid) {
      this.keyGraceDeadline.remove(uuid);
   }

   public boolean isAuthenticated(UUID uuid) {
      return this.get(uuid) == AuthState.AUTHENTICATED;
   }
}
