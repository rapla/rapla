package org.rapla.server.spring;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Replaces Spring Security's DefaultLoginPageGeneratingFilter with a minimal
 * page that does NOT mark the password field as required — Rapla's dev admin
 * has an empty password by convention.
 *
 * Activated by SecurityConfig's formLogin().loginPage("/login") which
 * disables the default generator. Both /login (GET) and /login (POST) live
 * at the same URL; Spring's UsernamePasswordAuthenticationFilter intercepts
 * the POST before this controller sees it.
 */
@Controller
@RequestMapping("/login")
public class LoginPageController
{
    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout)
    {
        String banner = "";
        if (error != null)
        {
            banner = "<p style=\"color:#c00;\">Invalid username or password</p>";
        }
        else if (logout != null)
        {
            banner = "<p style=\"color:#080;\">You have been logged out</p>";
        }
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>Rapla — Sign in</title>
                  <style>
                    body { font-family: system-ui, sans-serif; background: #f4f4f4; margin: 0; padding: 0; display: flex; min-height: 100vh; align-items: center; justify-content: center; }
                    .card { background: #fff; padding: 2rem 2.5rem; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,.08); min-width: 320px; }
                    h1 { margin: 0 0 1rem; font-size: 1.4rem; color: #333; }
                    label { display: block; margin: 0.8rem 0 0.2rem; color: #555; font-size: 0.9rem; }
                    input[type=text], input[type=password] { width: 100%; padding: 0.5rem; border: 1px solid #ccc; border-radius: 4px; font-size: 1rem; box-sizing: border-box; }
                    button { margin-top: 1.2rem; padding: 0.6rem 1.2rem; background: #2a6; color: #fff; border: 0; border-radius: 4px; cursor: pointer; font-size: 1rem; width: 100%; }
                    button:hover { background: #248; }
                    .note { color: #888; font-size: 0.8rem; margin-top: 1rem; }
                    .remember { display: flex; align-items: center; gap: 0.5rem; margin: 1rem 0 0.2rem; color: #555; font-size: 0.9rem; }
                    .remember input { margin: 0; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>Rapla — Sign in</h1>
                    %BANNER%
                    <form method="post" action="/login">
                      <label for="u">Username</label>
                      <input id="u" type="text" name="username" autofocus required>
                      <label for="p">Password</label>
                      <input id="p" type="password" name="password">
                      <label class="remember">
                        <input type="checkbox" name="remember-me" value="on">
                        Remember me on this device
                      </label>
                      <button type="submit">Sign in</button>
                    </form>
                    <p class="note">Dev default: <code>admin</code> with empty password.</p>
                  </div>
                </body>
                </html>
                """.replace("%BANNER%", banner);
    }
}
