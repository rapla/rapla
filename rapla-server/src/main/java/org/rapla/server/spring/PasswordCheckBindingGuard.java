package org.rapla.server.spring;

/**
 * Boot guard for the {@code rapla.password-check-disabled} foot-gun (B4).
 *
 * <p>With password verification disabled (the {@code standalone} desktop
 * profile), the OAuth password grant returns a token for any username with no
 * credential. That is acceptable ONLY when the server binds a loopback address
 * (a single-user desktop talking to its own WebView). If such an instance binds
 * a non-loopback address — by misconfiguration, a copied profile, or a Docker
 * image built with {@code standalone} — it exposes a credential-free admin login
 * to the whole network. This guard refuses to boot in that case.
 */
public final class PasswordCheckBindingGuard
{
    private PasswordCheckBindingGuard()
    {
    }

    /**
     * @throws IllegalStateException if password checks are disabled while the
     *         HTTP connector binds a non-loopback (network-reachable) address.
     */
    public static void validate(boolean passwordCheckDisabled, String serverAddress)
    {
        if (!passwordCheckDisabled)
        {
            return;
        }
        if (bindsNonLoopback(serverAddress))
        {
            String shown = (serverAddress == null || serverAddress.isBlank())
                    ? "<unset> (binds all interfaces / 0.0.0.0)"
                    : serverAddress.trim();
            throw new IllegalStateException(
                    "rapla.password-check-disabled=true (no password verification) but the HTTP "
                  + "connector binds a non-loopback address [" + shown + "]. This would expose a "
                  + "credential-free admin login to the network. Set server.address to a loopback "
                  + "address (127.0.0.1) for standalone mode, or remove rapla.password-check-disabled.");
        }
    }

    private static boolean bindsNonLoopback(String serverAddress)
    {
        if (serverAddress == null || serverAddress.isBlank())
        {
            return true; // unset ⇒ Spring binds every interface
        }
        String address = serverAddress.trim();
        if (address.equals("0.0.0.0") || address.equals("::") || address.equals("*"))
        {
            return true;
        }
        try
        {
            return !java.net.InetAddress.getByName(address).isLoopbackAddress();
        }
        catch (java.net.UnknownHostException e)
        {
            return true; // can't prove it's loopback ⇒ fail closed
        }
    }
}
