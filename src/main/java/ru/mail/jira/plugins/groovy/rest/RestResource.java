package ru.mail.jira.plugins.groovy.rest;

import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.websudo.WebSudoRequired;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import ru.mail.jira.plugins.groovy.api.RestRepository;
import ru.mail.jira.plugins.groovy.api.dto.rest.RestScriptForm;
import ru.mail.jira.plugins.groovy.impl.PermissionHelper;
import ru.mail.jira.plugins.groovy.util.ExceptionHelper;
import ru.mail.jira.plugins.groovy.util.RestExecutor;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Scanned
@Path("/rest")
@WebSudoRequired
public class RestResource {
    private final JiraAuthenticationContext authenticationContext;
    private final PermissionHelper permissionHelper;
    private final RestRepository restRepository;

    public RestResource(
        @ComponentImport JiraAuthenticationContext authenticationContext,
        PermissionHelper permissionHelper,
        RestRepository restRepository
    ) {
        this.authenticationContext = authenticationContext;
        this.permissionHelper = permissionHelper;
        this.restRepository = restRepository;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @WebSudoRequired
    public Response createRestScript(RestScriptForm form) {
        return new RestExecutor<>(() -> {
            permissionHelper.checkIfAdmin();

            return restRepository.createScript(authenticationContext.getLoggedInUser(), form);
        })
            .withExceptionMapper(MultipleCompilationErrorsException.class, Response.Status.BAD_REQUEST, e -> ExceptionHelper.mapCompilationException("scriptBody", e))
            .getResponse();
    }

    @Path("/{id}")
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @WebSudoRequired
    public Response updateRestScript(@PathParam("id") int id, RestScriptForm form) {
        return new RestExecutor<>(() -> {
            permissionHelper.checkIfAdmin();

            return restRepository.updateScript(authenticationContext.getLoggedInUser(), id, form);
        })
            .withExceptionMapper(MultipleCompilationErrorsException.class, Response.Status.BAD_REQUEST, e -> ExceptionHelper.mapCompilationException("scriptBody", e))
            .getResponse();
    }

    @Path("/{id}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @WebSudoRequired
    public Response getRestScript(@PathParam("id") int id) {
        return new RestExecutor<>(() -> {
            permissionHelper.checkIfAdmin();

            return restRepository.getScript(id, true);
        }).getResponse();
    }

    @Path("/{id}")
    @DELETE
    @WebSudoRequired
    public Response deleteRestScript(@PathParam("id") int id) {
        return new RestExecutor<>(() -> {
            permissionHelper.checkIfAdmin();

            restRepository.deleteScript(authenticationContext.getLoggedInUser(), id);
            return null;
        }).getResponse();
    }

    @Path("/all")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @WebSudoRequired
    public Response getAllScripts() {
        return new RestExecutor<>(() -> {
            permissionHelper.checkIfAdmin();

            return restRepository.getAllScripts();
        }).getResponse();
    }
}
