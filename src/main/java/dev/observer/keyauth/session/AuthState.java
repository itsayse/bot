package dev.observer.keyauth.session;

public enum AuthState {
   AWAITING_KEY,
   AUTHENTICATED,
   KEY_LOCKED;

   // $FF: synthetic method
   private static AuthState[] $values() {
      return new AuthState[]{AWAITING_KEY, AUTHENTICATED, KEY_LOCKED};
   }
}
