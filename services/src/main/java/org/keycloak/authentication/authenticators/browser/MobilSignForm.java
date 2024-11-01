package org.keycloak.authentication.authenticators.browser;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.authenticators.broker.AbstractIdpAuthenticator;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.forms.login.freemarker.model.IdentityProviderBean;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.ServicesLogger;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.turksat.mobilsign.MobilsignIdentityProvider;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class MobilSignForm extends AbstractUsernameFormAuthenticator implements Authenticator

{
    protected static ServicesLogger log = ServicesLogger.LOGGER;

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        boolean MobilsignSuccess = true;
        String postParamKonu = "AuthNET - Mobil Imza Login";
        String postParamOperator = formData.getFirst("operator");
        String postParamTel = formData.getFirst("gsmNo");
        String postParamTC = formData.getFirst("tcNo");

        System.out.println("Mobil sign form page");

        try {
            MobilsignSuccess = CustomRequest.sendHttpPOSTRequest(postParamKonu,postParamOperator,postParamTel,postParamTC);
        }catch (IOException e){
            throw new RuntimeException(e);
        }
        System.out.println("MobilsignSucces : " + MobilsignSuccess);

        if (formData.containsKey("cancel") || !MobilsignSuccess) {
            context.cancelLogin();
            return;
        }
        if (!validateForm(context, formData)) {
            return;
        }
        context.success();
    }

    protected boolean validateForm(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
        return validateUser(context, formData);
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();
        if (context.getUser() != null) {
            // We can skip the form when user is re-authenticating. Unless current user has some IDP set, so he can re-authenticate with that IDP
            if (!this.hasLinkedBrokers(context)) {
                context.success();
                return;
            }
//            List<IdentityProviderModel> identityProviders = LoginFormsUtil
//                    .filterIdentityProviders(context.getRealm().getIdentityProvidersStream(), context.getSession(), context);
//            if (identityProviders.isEmpty()) {
//                context.success();
//                return;
//            }

            }

        Response challengeResponse = challenge(context, formData);
        context.challenge(challengeResponse);
        //super.authenticate(context);
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    protected Response challenge(AuthenticationFlowContext context, MultivaluedMap<String, String> formData) {
        LoginFormsProvider forms = context.form();

        if (formData.size() > 0) forms.setFormData(formData);

        return forms.createLoginUsername();
    }


    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        // never called
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // never called
    }

    @Override
    public void close() {

    }

    // LoginFormsUtil yeni versiyonda silindiği için authenticate methodu içi değişti
    private boolean hasLinkedBrokers(AuthenticationFlowContext context) {
        KeycloakSession session = context.getSession();
        UserModel user = context.getUser();
        if (user == null) {
            return false;
        }
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        SerializedBrokeredIdentityContext serializedCtx = SerializedBrokeredIdentityContext.readFromAuthenticationSession(authSession, AbstractIdpAuthenticator.BROKERED_CONTEXT_NOTE);
        final IdentityProviderModel existingIdp = (serializedCtx == null) ? null : serializedCtx.deserialize(session, authSession).getIdpConfig();

        return session.users().getFederatedIdentitiesStream(session.getContext().getRealm(), user)
                .map(fedIdentity -> session.identityProviders().getByAlias(fedIdentity.getIdentityProvider()))
                .filter(Objects::nonNull)
                .anyMatch(idpModel -> existingIdp == null || !Objects.equals(existingIdp.getAlias(), idpModel.getAlias()));

    }

}
