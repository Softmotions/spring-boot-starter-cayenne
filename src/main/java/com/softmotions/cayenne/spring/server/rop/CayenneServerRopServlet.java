package com.softmotions.cayenne.spring.server.rop;

import java.io.IOException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.configuration.web.WebUtil;
import org.apache.cayenne.remote.ClientMessage;
import org.apache.cayenne.remote.RemoteService;
import org.apache.cayenne.remote.RemoteSession;
import org.apache.cayenne.rop.ROPConstants;
import org.apache.cayenne.rop.ROPRequestContext;
import org.apache.cayenne.rop.ROPSerializationService;

import com.softmotions.cayenne.spring.CayenneServerProperties;

/**
 * @author Adamansky Anton (adamansky@softmotions.com)
 */
public class CayenneServerRopServlet extends HttpServlet {

    private final ServerRuntime srt;

    private final CayenneServerProperties cfg;

    private RemoteService remoteService;

    private ROPSerializationService serializationService;

    public CayenneServerRopServlet(ServerRuntime srt,
                                   CayenneServerProperties cfg) {
        this.srt = srt;
        this.cfg = cfg;
    }

    @Override
    public void init(ServletConfig scfg) throws ServletException {
        ServletContext context = scfg.getServletContext();
        if (WebUtil.getCayenneRuntime(context) != null) {
            throw new ServletException(
                    "CayenneRuntime is already configured in the servlet environment");
        }
        ServletContext servletContext = scfg.getServletContext();
        this.remoteService = srt.getInjector().getInstance(RemoteService.class);
        this.serializationService = srt.getInjector().getInstance(ROPSerializationService.class);
        WebUtil.setCayenneRuntime(servletContext, srt);
        super.init(scfg);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            String serviceId = req.getPathInfo();
            String objectId = req.getParameter("id");
            if (objectId == null) {
                objectId = req.getParameter("ejbid");
            }
            ROPRequestContext.start(serviceId, objectId, req, resp);
            String operation = req.getParameter(ROPConstants.OPERATION_PARAMETER);
            if (operation != null) {
                switch (operation) {
                    case ROPConstants.ESTABLISH_SESSION_OPERATION:
                        RemoteSession session = remoteService.establishSession();
                        serializationService.serialize(session, resp.getOutputStream());
                        break;
                    case ROPConstants.ESTABLISH_SHARED_SESSION_OPERATION:
                        String sessionName = req.getParameter(ROPConstants.SESSION_NAME_PARAMETER);
                        RemoteSession sharedSession = remoteService.establishSharedSession(sessionName);

                        serializationService.serialize(sharedSession, resp.getOutputStream());
                        break;
                    default:
                        throw new ServletException("Unknown operation: " + operation);
                }
            } else {
                Object response = remoteService.processMessage(
                        serializationService.deserialize(req.getInputStream(), ClientMessage.class));
                serializationService.serialize(response, resp.getOutputStream());
            }
        } catch (RuntimeException | ServletException e) {
            throw e;
        } catch (Throwable e) {
            throw new ServletException(e);
        } finally {
            ROPRequestContext.end();
        }
    }
}
