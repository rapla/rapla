package org.rapla.client.menu;

import org.rapla.components.util.Tools;
import org.rapla.entities.User;
import org.rapla.storage.PermissionController;

/**
 * Pure-Java decision logic carved out of
 * {@code rapla-client/.../menu/PasswordChangeAction} (PRD 023 — applying
 * the carve-out pattern beyond the reservation-edit tier to validate the
 * tier-1/tier-2 test harness on a real production class).
 * <p>
 * Two decisions that are answered identically server-side and
 * client-side: who can change a password, and is a submitted form valid.
 * Lives in rapla-core so a future Angular client can call the same
 * functions (eventually via REST) without re-implementing the rules.
 */
public final class PasswordChangePolicy
{
    private PasswordChangePolicy() {}

    /**
     * True when {@code actingUser} is allowed to change the password of
     * {@code targetUser}. The user can always change their own; an
     * admin can change anyone they administer.
     */
    public static boolean canChangePassword(User actingUser, User targetUser)
    {
        if (actingUser == null || targetUser == null) return false;
        return PermissionController.canAdminUser(actingUser, targetUser) || actingUser.equals(targetUser);
    }

    /**
     * True when the change-password form requires the old password to
     * be entered. An admin changing someone else's password doesn't need
     * the old one; everyone else (including admins changing their own
     * password) does.
     */
    public static boolean requiresOldPassword(User actingUser, User targetUser)
    {
        if (actingUser == null || targetUser == null) return true;
        return !PermissionController.canAdminUser(actingUser, targetUser) || actingUser.equals(targetUser);
    }

    /** Outcome of validating a submitted password-change form. */
    public record ValidationResult(boolean valid, String errorKey)
    {
        public static ValidationResult ok() { return new ValidationResult(true, null); }
        public static ValidationResult error(String key) { return new ValidationResult(false, key); }
    }

    /**
     * Validate a submitted password-change form. {@code oldPassword} is
     * ignored when {@code requireOld} is {@code false}. The error key is
     * an {@code i18n} bundle key — the caller resolves to a localized
     * message.
     */
    public static ValidationResult validate(boolean requireOld,
                                            char[] oldPassword,
                                            char[] newPassword,
                                            char[] verification)
    {
        if (requireOld && (oldPassword == null || oldPassword.length == 0))
        {
            return ValidationResult.error("error.password_required");
        }
        if (newPassword == null || newPassword.length == 0)
        {
            return ValidationResult.error("error.password_required");
        }
        if (!Tools.match(newPassword, verification))
        {
            return ValidationResult.error("error.passwords_dont_match");
        }
        return ValidationResult.ok();
    }
}
