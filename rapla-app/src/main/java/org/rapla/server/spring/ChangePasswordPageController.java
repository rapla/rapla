package org.rapla.server.spring;

import jakarta.servlet.http.HttpServletRequest;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.server.RemoteSession;
import org.rapla.storage.CachableStorageOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

/**
 * B3 — the "please set a password" nag page. A browser/SPA login through the
 * Spring {@code /login} flow is redirected here by {@link FormLoginSuccessHandler}
 * whenever the user's password is unset (empty) and they are not the
 * {@code rapla.fix-admin-password}-locked admin.
 *
 * <p>The dialog is <b>skippable but not disableable</b>: "Later" continues to the app
 * (the empty password stays allowed), and the nag reappears on the next login until a
 * real password is set. Only this Spring SSO flow is touched — the Swing client login is
 * deliberately left unchanged.
 */
@Controller
@RequestMapping("/change-password")
public class ChangePasswordPageController
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ChangePasswordPageController.class);
    private static final String AFTER = "/app/";

    private final RemoteSession session;
    private final CachableStorageOperator operator;

    public ChangePasswordPageController(RemoteSession session, CachableStorageOperator operator)
    {
        this.session = session;
        this.operator = operator;
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String page(@RequestParam(value = "error", required = false) String error, HttpServletRequest request)
    {
        String banner = error != null
                ? "<p style=\"color:#c00;\">Passwords did not match or were empty.</p>" : "";
        return render(banner, request);
    }

    @PostMapping
    public String submit(@RequestParam(value = "newPassword", required = false) String newPassword,
                         @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
                         @RequestParam(value = "skip", required = false) String skip,
                         HttpServletRequest request) throws RaplaException
    {
        if (skip != null)
        {
            // skippable, not disableable — empty password stays allowed, nag returns next login
            return "redirect:" + AFTER;
        }
        if (newPassword == null || newPassword.isEmpty() || !newPassword.equals(confirmPassword))
        {
            return "redirect:/change-password?error";
        }
        User user = session.checkAndGetUser(request);
        operator.changePassword(user, new char[0], newPassword.toCharArray());
        LOGGER.info("User '{}' set a password via the change-password nag page", user.getUsername());
        return "redirect:" + AFTER;
    }

    private String render(String banner, HttpServletRequest request)
    {
        String csrfField = "";
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null)
        {
            csrfField = "<input type=\"hidden\" name=\"" + HtmlUtils.htmlEscape(token.getParameterName())
                    + "\" value=\"" + HtmlUtils.htmlEscape(token.getToken()) + "\">\n";
        }
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>Rapla — Set a password</title>
                  <style>
                    body { font-family: system-ui, sans-serif; background: #f4f4f4; margin: 0; display: flex; min-height: 100vh; align-items: center; justify-content: center; }
                    .card { background: #fff; padding: 2rem 2.5rem; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,.08); min-width: 320px; }
                    h1 { margin: 0 0 0.5rem; font-size: 1.4rem; color: #333; }
                    p.lead { color: #555; font-size: 0.9rem; margin: 0 0 1rem; }
                    label { display: block; margin: 0.8rem 0 0.2rem; color: #555; font-size: 0.9rem; }
                    input[type=password] { width: 100%; padding: 0.5rem; border: 1px solid #ccc; border-radius: 4px; font-size: 1rem; box-sizing: border-box; }
                    button { margin-top: 1.2rem; padding: 0.6rem 1.2rem; border: 0; border-radius: 4px; cursor: pointer; font-size: 1rem; width: 100%; }
                    .primary { background: #2a6; color: #fff; }
                    .primary:hover { background: #248; }
                    .skip { background: transparent; color: #888; margin-top: 0.4rem; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>Set a password</h1>
                    <p class="lead">Your account has no password. Set one now to secure it.</p>
                    %BANNER%
                    <form method="post" action="/change-password">
                      %CSRF%
                      <label for="np">New password</label>
                      <input id="np" type="password" name="newPassword" autofocus>
                      <label for="cp">Confirm password</label>
                      <input id="cp" type="password" name="confirmPassword">
                      <button type="submit" class="primary">Set password</button>
                    </form>
                    <form method="post" action="/change-password">
                      %CSRF%
                      <input type="hidden" name="skip" value="1">
                      <button type="submit" class="skip">Later</button>
                    </form>
                  </div>
                </body>
                </html>
                """
                .replace("%BANNER%", banner)
                .replace("%CSRF%", csrfField);
    }
}
