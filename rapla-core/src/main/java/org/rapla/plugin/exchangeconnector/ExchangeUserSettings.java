package org.rapla.plugin.exchangeconnector;

/**
 * Per-user Exchange Connector overrides exposed at
 * {@code GET /exchange/config/user}.
 *
 * <p>Nullable {@code sendInvitationAndCancellation} signals "no user
 * override — fall back to {@link ExchangeConnectorConfig#DEFAULT_EXCHANGE_SEND_INVITATION_AND_CANCELATION}".
 * Today this is the only live preference key on the user-options panel
 * (the remaining keys in the legacy panel are all commented out).
 */
public record ExchangeUserSettings(
        Boolean sendInvitationAndCancellation
) {}
