package dev.observer.keyauth.session;

public enum AuthState {
    AWAITING_KEY,
    AWAITING_REGISTER,
    AWAITING_LOGIN,
    AUTHENTICATED,
    // Was AUTHENTICATED, but a periodic check found the bound key is now
    // expired or revoked. Treated as "not authenticated" everywhere (frozen
    // by ProtectionListener) until the player fixes it with /renewkey.
    KEY_LOCKED
}
