package org.rapla.rest.server;

import org.rapla.RaplaResources;
import org.rapla.components.util.Tools;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.framework.logger.Logger;
import org.rapla.jsonrpc.common.RemoteJsonMethod;
import org.rapla.server.internal.RaplaAuthentificationService;
import org.rapla.server.internal.TokenHandler;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.LoginCredentials;
import org.rapla.storage.dbrm.LoginTokens;

import javax.inject.Inject;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.PrintWriter;

@Path("auth")
@RemoteJsonMethod
public class RaplaAuthRestPage extends AbstractRestPage
{

    private final RaplaAuthentificationService authentificationService;
    private final I18nBundle i18n;
    private final RaplaFacade facade;
    private final Logger logger;
    private final TokenHandler tokenHandler;

    @Inject
    public RaplaAuthRestPage(RaplaFacade facade, RaplaAuthentificationService authentificationService, RaplaResources i18n, Logger logger, TokenHandler tokenHandler) throws RaplaException
    {
        super(facade);
        this.facade = facade;
        this.authentificationService = authentificationService;
        this.i18n = i18n;
        this.logger = logger;
        this.tokenHandler = tokenHandler;
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public LoginTokens create(@QueryParam("credentials") LoginCredentials credentials) throws Exception
    {
        User user = null;
        try
        {
            user = authentificationService.authenticate(credentials.getUsername(), credentials.getPassword(), credentials.getConnectAs(), logger);
        }
        catch(Exception e)
        {
            logger.error(e.getMessage());
            final String loginErrorMessage = i18n.getString("error.login");
            throw new RaplaSecurityException(loginErrorMessage);
        }
        final LoginTokens loginTokens = tokenHandler.generateAccessToken(user);
        if (loginTokens.isValid())
        {
            return loginTokens;
        }
        final String loginErrorMessage = i18n.getString("error.login");
        throw new RaplaSecurityException(loginErrorMessage);
    }

    @POST
    public String createPlain(@QueryParam("credentials") LoginCredentials credentials) throws Exception
    {
        User user = null;
        try
        {
            user = authentificationService.authenticate(credentials.getUsername(), credentials.getPassword(), credentials.getConnectAs(), logger);
        }
        catch(Exception e)
        {
            logger.error(e.getMessage());
            final String loginErrorMessage = i18n.getString("error.login");
            throw new RaplaSecurityException(loginErrorMessage);
        }
        final LoginTokens loginTokens = tokenHandler.generateAccessToken(user);
        if (loginTokens.isValid())
        {
            return loginTokens.getAccessToken();
        }
        final String loginErrorMessage = i18n.getString("error.login");
        throw new RaplaSecurityException(loginErrorMessage);
    }

    @POST
    @Produces(MediaType.TEXT_HTML)
    public void create_(@QueryParam("url") String url, @QueryParam("username") String user, @QueryParam("password") String password,
            @QueryParam("connectAs") String connectAs, @Context HttpServletResponse response) throws Exception
    {
        final String targetUrl = Tools.createXssSafeString(url);
        final String errorMessage;
        if (user != null)
        {
            try
            {
                final LoginTokens token = create(new LoginCredentials(user, password, connectAs));
                final String accessToken = token.getAccessToken();
                final Cookie cookie = new Cookie("raplaLoginToken", token.toString());
                response.addCookie(cookie);
                response.sendRedirect(targetUrl != null ? targetUrl : "rapla.html");
                final PrintWriter writer = response.getWriter();
                writer.println(accessToken);
                writer.close();
                return;
            }
            catch (Exception e)
            {
                errorMessage = e.getMessage();
            }
        }
        else
        {
            errorMessage = null;
        }
        createPage(url, user, errorMessage, response);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public void getHtml(@QueryParam("url") String url, @Context HttpServletResponse response) throws IOException
    {
        createPage(url, null, null, response);
    }

    protected void createPage(String url, final String user, final String errorMessage, HttpServletResponse response) throws IOException
    {
        final String userName = Tools.createXssSafeString(user);

        response.setContentType("text/html; charset=ISO-8859-1");
        PrintWriter out = response.getWriter();
        String linkPrefix = "../";//request.getPathTranslated() != null ? "../" : "";

        out.println("<html>");
        out.println("  <head>");
        // add the link to the stylesheet for this page within the <head> tag
        out.println("    <link REL=\"stylesheet\" href=\"" + linkPrefix + "login.css\" type=\"text/css\">");
        // tell the html page where its favourite icon is stored
        out.println("    <link REL=\"shortcut icon\" type=\"image/x-icon\" href=\"" + linkPrefix + "images/favicon.ico\">");
        out.println("    <title>");
        String title = null;
        final String defaultTitle = i18n.getString("rapla.title");
        try
        {
            final Preferences systemPreferences = facade.getSystemPreferences();
            title = systemPreferences.getEntryAsString(ContainerImpl.TITLE, defaultTitle);
        }
        catch (RaplaException e)
        {
            title = defaultTitle;
        }

        out.println(title);
        out.println("    </title>");
        out.println("  </head>");
        out.println("  <body>");
        out.println("    <form method=\"post\">");
        if (url != null)
        {
            out.println("       <input type=\"hidden\" name=\"url\" value=\"" + Tools.createXssSafeString(url) + "\"/>");
        }
        out.println("           <div class=\"loginOuterPanel\">");
        out.println("               <div class=\"loginInputPanel\">");
        final String userNameValue = userName != null ? userName : "";
        out.println("                   <input name=\"userName\" type=\"text\" value=\"" + userNameValue + "\">");
        out.println("                   <input name=\"password\"type=\"password\" >");
        out.println("               </div>");
        out.println("               <div class=\"loginCommandPanel\">");
        out.println("                   <button type=\"submit\" class=\"sendButton\">login</button>");
        out.println("               </div>");
        out.println("           </div>");
        if (errorMessage != null)
        {
            out.println("       <div class=\"errorMessage\">" + errorMessage + "</div>");
        }
        out.println("  </body>");
        out.println("</html>");
        out.close();
    }

}
