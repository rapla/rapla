package org.rapla.rest.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.inject.Inject;
import javax.inject.Named;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.components.util.Tools;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.configuration.Preferences;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.internal.ContainerImpl;
import org.rapla.framework.logger.Logger;
import org.rapla.rest.gwtjsonrpc.common.FutureResult;
import org.rapla.server.ServerServiceContainer;
import org.rapla.servletpages.RaplaPageGenerator;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.LoginCredentials;
import org.rapla.storage.dbrm.LoginTokens;
import org.rapla.storage.dbrm.RemoteServer;

@WebService
public class RaplaAuthRestPage extends AbstractRestPage implements RaplaPageGenerator {

	private RemoteServer remoteServer;
	private I18nBundle i18n;

	@Inject
	public RaplaAuthRestPage(ClientFacade facade, ServerServiceContainer serverContainer, Logger logger, RemoteServer remoteServer,
			@Named(RaplaComponent.RaplaResourcesId) I18nBundle i18n) throws RaplaException {
		super(facade, serverContainer, logger, false);
		this.remoteServer = remoteServer;
		this.i18n = i18n;
	}

	public LoginTokens create(@WebParam(name = "credentials") LoginCredentials credentials) throws Exception {
		final FutureResult<LoginTokens> result = remoteServer.auth(credentials);
		final LoginTokens loginTokens = result.get();
		if (loginTokens.isValid()) {
			return loginTokens;
		}
		final String loginErrorMessage = i18n.getString("error.login");
		throw new RaplaSecurityException(loginErrorMessage);
	}
	
	@Override
	public void generatePage(ServletContext servletContext, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		Object serviceObj = this;
		String method = request.getMethod();
		final String contentType = request.getHeader("Accept");
		if (method.equals("GET") && contentType.startsWith("text/html")) {
			final String url = request.getParameter("url");
			getHtml(url, response);
		} else if (method.equals("POST") && contentType.startsWith("text/html")) {
			request.setAttribute("method", "create_");
			String url=request.getParameter("url");
			String user=request.getParameter("userName");
			String password=request.getParameter("password");
			String connectAs=request.getParameter("connectAs");
			try {
				create_(url, user, password, connectAs, response);
			} catch (Exception e) {
				servlet.serviceError(request, response, servletContext, e);
				return;
			}
			//servlet.service(request, response, servletContext, serviceObj);
		} else{
			super.generatePage(servletContext, request, response);
		}
		// super.generatePage(servletContext, request, response);
	}


	public void create_(@WebParam(name = "url") String url, @WebParam(name = "user") String user, @WebParam(name = "password") String password,
			@WebParam(name = "connectAs") String connectAs,  HttpServletResponse response) throws Exception {
		final String targetUrl = Tools.createXssSafeString(url);
		final String errorMessage;
		if (user != null) {
			try {
				final LoginTokens token = create(new LoginCredentials(user, password, connectAs));
				final String accessToken = token.getAccessToken();
				final Cookie cookie = new Cookie("raplaLoginToken", token.toString());
				response.addCookie(cookie);
				response.sendRedirect(targetUrl != null ? targetUrl : "../rapla.html");
				final PrintWriter writer = response.getWriter();
				writer.println(accessToken);
				writer.close();
				return;
			} catch (Exception e) {
				errorMessage = e.getMessage();
			}
		} else {
			errorMessage = null;
		}
		createPage(url, user, errorMessage, response);
	}

	public void getHtml(@WebParam(name = "url") String url, HttpServletResponse response) throws IOException {
		createPage(url, null, null, response);
	}

	protected void createPage(String url, final String user, final String errorMessage, HttpServletResponse response) throws IOException {
		final String userName = Tools.createXssSafeString(user);

		response.setContentType("text/html; charset=ISO-8859-1");
		PrintWriter out = response.getWriter();
		String linkPrefix = "../";//request.getPathTranslated() != null ? "../" : "";

		out.println("<html>");
		out.println("  <head>");
		// add the link to the stylesheet for this page within the <head> tag
		out.println("    <link REL=\"stylesheet\" href=\"" + linkPrefix + "default.css\" type=\"text/css\">");
		// tell the html page where its favourite icon is stored
		out.println("    <link REL=\"shortcut icon\" type=\"image/x-icon\" href=\"" + linkPrefix + "images/favicon.ico\">");
		out.println("    <title>");
		String title = null;
		final String defaultTitle = i18n.getString("rapla.title");
		try {
			final Preferences systemPreferences = facade.getSystemPreferences();
			title = systemPreferences.getEntryAsString(ContainerImpl.TITLE, defaultTitle);
		} catch (RaplaException e) {
			title = defaultTitle;
		}

		out.println(title);
		out.println("    </title>");
		out.println("  </head>");
		out.println("  <body>");
		out.println("    <form method=\"post\">");
		if (url != null) {
			out.println("    	<input type=\"hidden\" name=\"url\">" + Tools.createXssSafeString(url) + "</input>");
		}
		out.println("			<div class=\"loginOuterPanel\">");
		out.println("				<div class=\"loginInputPanel\">");
		final String userNameValue = userName != null ? userName : "";
		out.println("					<input name=\"userName\" type=\"text\" value=\"" + userNameValue + "\">");
		out.println("					<input name=\"password\"type=\"password\" >");
		out.println("				</div>");
		out.println("				<div class=\"loginCommandPanel\">");
		out.println("					<button type=\"submit\" class=\"sendButton\">login</button>");
		out.println("				</div>");
		out.println("			</div>");
		if (errorMessage != null) {
			out.println("		<div class=\"errorMessage\">" + errorMessage + "</div>");
		}
		out.println("  </body>");
		out.println("</html>");
		out.close();
	}

}
