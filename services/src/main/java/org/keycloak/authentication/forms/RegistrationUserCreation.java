/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.authentication.forms;

import jakarta.ws.rs.core.MultivaluedHashMap;
import org.keycloak.Config;
import org.keycloak.TokenVerifier;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationFlowException;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.authentication.actiontoken.inviteorg.InviteOrgActionToken;
import org.keycloak.authentication.actiontoken.inviteorg.InviteOrgActionTokenHandler;
import org.keycloak.authentication.requiredactions.TermsAndConditions;
import org.keycloak.common.VerificationException;
import org.keycloak.common.util.Time;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventType;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.*;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.organization.OrganizationProvider;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.messages.Messages;
import org.keycloak.services.validation.Validation;
import org.keycloak.userprofile.Attributes;
import org.keycloak.userprofile.UserProfileContext;
import org.keycloak.userprofile.UserProfileProvider;
import org.keycloak.userprofile.ValidationException;
import org.keycloak.userprofile.UserProfile;

import jakarta.ws.rs.core.MultivaluedMap;
import java.util.List;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class RegistrationUserCreation implements FormAction, FormActionFactory {

    public static final String PROVIDER_ID = "registration-user-creation";

    @Override
    public String getHelpText() {
        return "This action must always be first! Validates the username and user profile of the user in validation phase.  In success phase, this will create the user in the database including his user profile.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return null;
    }

    @Override
    public void validate(ValidationContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        MultivaluedMap<String, String> queryParameters = context.getHttpRequest().getUri().getQueryParameters();

        context.getEvent().detail(Details.REGISTER_METHOD, "form");

        UserProfile profile = getOrCreateUserProfile(context, formData);
        Attributes attributes = profile.getAttributes();
        String email = attributes.getFirst(UserModel.EMAIL);
        String username = attributes.getFirst(UserModel.USERNAME);
        String firstName = attributes.getFirst(UserModel.FIRST_NAME);
        String lastName = attributes.getFirst(UserModel.LAST_NAME);
        context.getEvent().detail(Details.EMAIL, email);

        context.getEvent().detail(Details.USERNAME, username);
        context.getEvent().detail(Details.FIRST_NAME, firstName);
        context.getEvent().detail(Details.LAST_NAME, lastName);

        if (context.getRealm().isRegistrationEmailAsUsername()) {
            context.getEvent().detail(Details.USERNAME, email);
        }

        try {
            profile.validate();
        } catch (ValidationException pve) {
            List<FormMessage> errors = Validation.getFormErrorsFromValidation(pve.getErrors());

            if (pve.hasError(Messages.EMAIL_EXISTS, Messages.INVALID_EMAIL)) {
                context.getEvent().detail(Details.EMAIL, attributes.getFirst(UserModel.EMAIL));
            }

            if (pve.hasError(Messages.EMAIL_EXISTS)) {
                context.error(Errors.EMAIL_IN_USE);
            } else if (pve.hasError(Messages.USERNAME_EXISTS)) {
                context.error(Errors.USERNAME_IN_USE);
            } else {
                context.error(Errors.INVALID_REGISTRATION);
            }

            context.validationError(formData, errors);
            return;
        }

        // handle parsing of an organization invite token from the url
        String tokenFromQuery = queryParameters.getFirst(Constants.ORG_TOKEN);
        if (tokenFromQuery != null) {
            TokenVerifier<InviteOrgActionToken> tokenVerifier = TokenVerifier.create(tokenFromQuery, InviteOrgActionToken.class);
            try {
                InviteOrgActionToken aToken = tokenVerifier.getToken();
                if (aToken.isExpired() || !aToken.getActionId().equals(InviteOrgActionToken.TOKEN_TYPE) || !aToken.getEmail().equals(email)) {
                   throw new VerificationException("The provided token is not valid. It may be expired or issued for a different email");
                }
                // TODO probably need to check if string is empty or null
                if (context.getSession().getProvider(OrganizationProvider.class).getById(aToken.getOrgId()) == null) {
                   throw new VerificationException("The provided token contains an invalid organization id");
                }
            } catch (VerificationException e) {
                // TODO we can be more specific here just trying to get something working...
                context.getEvent().detail(Messages.INVALID_ORG_INVITE, tokenFromQuery);
                context.error(Errors.INVALID_TOKEN);
                return;
            }
        }
        context.success();
    }

    @Override
    public void buildPage(FormContext context, LoginFormsProvider form) {
        checkNotOtherUserAuthenticating(context);
    }

    @Override
    public void success(FormContext context) {
        checkNotOtherUserAuthenticating(context);

        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String tokenFromQuery = context.getHttpRequest().getUri().getQueryParameters().getFirst(Constants.ORG_TOKEN);

        DefaultActionTokenKey aToken = null;
        if(tokenFromQuery != null) {
            try {
                TokenVerifier<DefaultActionTokenKey> tokenVerifier = TokenVerifier.create(tokenFromQuery, DefaultActionTokenKey.class);
                aToken = tokenVerifier.getToken();
            } catch (VerificationException e) {
                // TODO in theory this should never happen since we already validated. We should either encapsulate decoding the token somehow (add to context or make new class?)
                // for now we can panic run this exception if we somehow end up here
                throw new RuntimeException(e);
            }
        }

        String email = formData.getFirst(UserModel.EMAIL);
        String username = formData.getFirst(UserModel.USERNAME);

        if (context.getRealm().isRegistrationEmailAsUsername()) {
            username = email;
        }

        context.getEvent().detail(Details.USERNAME, username)
                .detail(Details.REGISTER_METHOD, "form")
                .detail(Details.EMAIL, email);

        UserProfile profile = getOrCreateUserProfile(context, formData);
        UserModel user = profile.create();

        // since we already validated the token we can just add the user to the organization
        if (aToken != null) {
            String org = aToken.getSubject();
            KeycloakSession session = context.getSession();
            OrganizationProvider provider = session.getProvider(OrganizationProvider.class);
            OrganizationModel orgModel = provider.getById(org);
            provider.addMember(orgModel, user);
            context.getEvent().detail(Details.ORG_ID, org);
        }

        user.setEnabled(true);

        if ("on".equals(formData.getFirst(RegistrationTermsAndConditions.FIELD))) {
            // if accepted terms and conditions checkbox, remove action and add the attribute if enabled
            RequiredActionProviderModel tacModel = context.getRealm().getRequiredActionProviderByAlias(
                    UserModel.RequiredAction.TERMS_AND_CONDITIONS.name());
            if (tacModel != null && tacModel.isEnabled()) {
                user.setSingleAttribute(TermsAndConditions.USER_ATTRIBUTE, Integer.toString(Time.currentTime()));
                context.getAuthenticationSession().removeRequiredAction(UserModel.RequiredAction.TERMS_AND_CONDITIONS);
                user.removeRequiredAction(UserModel.RequiredAction.TERMS_AND_CONDITIONS);
            }
        }

        context.setUser(user);

        context.getAuthenticationSession().setClientNote(OIDCLoginProtocol.LOGIN_HINT_PARAM, username);

        context.getEvent().user(user);
        context.getEvent().success();
        context.newEvent().event(EventType.LOGIN);
        context.getEvent().client(context.getAuthenticationSession().getClient().getClientId())
                .detail(Details.REDIRECT_URI, context.getAuthenticationSession().getRedirectUri())
                .detail(Details.AUTH_METHOD, context.getAuthenticationSession().getProtocol());
        String authType = context.getAuthenticationSession().getAuthNote(Details.AUTH_TYPE);
        if (authType != null) {
            context.getEvent().detail(Details.AUTH_TYPE, authType);
        }
    }

    private void checkNotOtherUserAuthenticating(FormContext context) {
        if (context.getUser() != null) {
            // the user probably did some back navigation in the browser, hitting this page in a strange state
            context.getEvent().detail(Details.EXISTING_USER, context.getUser().getUsername());
            throw new AuthenticationFlowException(AuthenticationFlowError.GENERIC_AUTHENTICATION_ERROR, Errors.DIFFERENT_USER_AUTHENTICATING, Messages.EXPIRED_ACTION);
        }
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {

    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }


    @Override
    public void close() {

    }

    @Override
    public String getDisplayType() {
        return "Registration User Profile Creation";
    }

    @Override
    public String getReferenceCategory() {
        return null;
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    private static AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.DISABLED
    };
    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENT_CHOICES;
    }
    @Override
    public FormAction create(KeycloakSession session) {
        return this;
    }

    @Override
    public void init(Config.Scope config) {

    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {

    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    private MultivaluedMap<String, String> normalizeFormParameters(MultivaluedMap<String, String> formParams) {
        MultivaluedHashMap<String, String> copy = new MultivaluedHashMap<>(formParams);

        // Remove google recaptcha form property to avoid length errors
        copy.remove(RegistrationPage.FIELD_RECAPTCHA_RESPONSE);
        // Remove "password" and "password-confirm" to avoid leaking them in the user-profile data
        copy.remove(RegistrationPage.FIELD_PASSWORD);
        copy.remove(RegistrationPage.FIELD_PASSWORD_CONFIRM);

        return copy;
    }

    /**
     * Get user profile instance for current HTTP request (KeycloakSession) and for given context. This assumes that there is
     * single user registered within HTTP request, which is always the case in Keycloak
     */
    public UserProfile getOrCreateUserProfile(FormContext formContext, MultivaluedMap<String, String> formData) {
        KeycloakSession session = formContext.getSession();
        UserProfile profile = (UserProfile) session.getAttribute("UP_REGISTER");
        if (profile == null) {
            formData = normalizeFormParameters(formData);
            UserProfileProvider profileProvider = session.getProvider(UserProfileProvider.class);
            profile = profileProvider.create(UserProfileContext.REGISTRATION, formData);
            session.setAttribute("UP_REGISTER", profile);
        }
        return profile;
    }
}
