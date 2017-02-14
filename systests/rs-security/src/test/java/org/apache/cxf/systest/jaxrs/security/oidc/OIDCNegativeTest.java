/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.systest.jaxrs.security.oidc;

import java.net.URL;
import java.util.Collections;
import java.util.Date;

import javax.ws.rs.client.ResponseProcessingException;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.rs.security.jose.jws.JwsHeaders;
import org.apache.cxf.rs.security.jose.jws.JwsJwtCompactConsumer;
import org.apache.cxf.rs.security.jose.jws.JwsJwtCompactProducer;
import org.apache.cxf.rs.security.jose.jwt.JwtClaims;
import org.apache.cxf.rs.security.jose.jwt.JwtToken;
import org.apache.cxf.rs.security.oauth2.common.ClientAccessToken;
import org.apache.cxf.rs.security.oauth2.common.OAuthAuthorizationData;
import org.apache.cxf.rs.security.oidc.common.IdToken;
import org.apache.cxf.rs.security.oidc.common.UserInfo;
import org.apache.cxf.systest.jaxrs.security.oauth2.common.OAuth2TestUtils;
import org.apache.cxf.systest.jaxrs.security.oauth2.common.OAuth2TestUtils.AuthorizationCodeParameters;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.cxf.testutil.common.TestUtil;
import org.junit.Assert;
import org.junit.BeforeClass;

/**
 * Some negative tests for OpenID Connect
 */
public class OIDCNegativeTest extends AbstractBusClientServerTestBase {

    static final String PORT = TestUtil.getPortNumber("jaxrs-negative-oidc");

    @BeforeClass
    public static void startServers() throws Exception {
        assertTrue(
                "Server failed to launch",
                // run the server in the same process
                // set this to false to fork
                launchServer(OIDCNegativeServer.class, true)
        );
    }

    @org.junit.Test
    public void testImplicitFlowPromptNone() throws Exception {
        URL busFile = OIDCFlowTest.class.getResource("client.xml");

        String address = "https://localhost:" + PORT + "/services/";
        WebClient client = WebClient.create(address, OAuth2TestUtils.setupProviders(),
                                            "alice", "security", busFile.toString());
        // Save the Cookie for the second request...
        WebClient.getConfig(client).getRequestContext().put(
            org.apache.cxf.message.Message.MAINTAIN_SESSION, Boolean.TRUE);

        // Get Access Token
        client.type("application/json").accept("application/json");
        client.query("client_id", "consumer-id");
        client.query("redirect_uri", "http://www.blah.apache.org");
        client.query("scope", "openid");
        client.query("response_type", "id_token");
        client.query("nonce", "1234565635");
        client.query("prompt", "none login");
        client.path("authorize-implicit/");
        Response response = client.get();

        try {
            response.readEntity(OAuthAuthorizationData.class);
            fail("Failure expected on a bad prompt");
        } catch (Exception ex) {
            // expected
        }
    }

    @org.junit.Test
    @org.junit.Ignore
    public void testImplicitFlowMaxAge() throws Exception {
        URL busFile = OIDCFlowTest.class.getResource("client.xml");

        String address = "https://localhost:" + PORT + "/services/";
        WebClient client = WebClient.create(address, OAuth2TestUtils.setupProviders(),
                                            "alice", "security", busFile.toString());
        // Save the Cookie for the second request...
        WebClient.getConfig(client).getRequestContext().put(
            org.apache.cxf.message.Message.MAINTAIN_SESSION, Boolean.TRUE);

        // Get Access Token
        client.type("application/json").accept("application/json");
        client.query("client_id", "consumer-id");
        client.query("redirect_uri", "http://www.blah.apache.org");
        client.query("scope", "openid");
        client.query("response_type", "id_token");
        client.query("nonce", "1234565635");
        client.query("max_age", "300");
        client.path("authorize-implicit/");
        Response response = client.get();

        OAuthAuthorizationData authzData = response.readEntity(OAuthAuthorizationData.class);

        // Now call "decision" to get the access token
        client.path("decision");
        client.type("application/x-www-form-urlencoded");

        Form form = new Form();
        form.param("session_authenticity_token", authzData.getAuthenticityToken());
        form.param("client_id", authzData.getClientId());
        form.param("redirect_uri", authzData.getRedirectUri());
        form.param("scope", authzData.getProposedScope());
        if (authzData.getResponseType() != null) {
            form.param("response_type", authzData.getResponseType());
        }
        if (authzData.getNonce() != null) {
            form.param("nonce", authzData.getNonce());
        }
        form.param("oauthDecision", "allow");

        response = client.post(form);

        String location = response.getHeaderString("Location");

        // Check IdToken
        String idToken = OAuth2TestUtils.getSubstring(location, "id_token");
        assertNotNull(idToken);

        JwsJwtCompactConsumer jwtConsumer = new JwsJwtCompactConsumer(idToken);
        JwtToken jwt = jwtConsumer.getJwtToken();
        Assert.assertNotNull(jwt.getClaims().getClaim(IdToken.AUTH_TIME_CLAIM));
    }

    @org.junit.Test
    public void testImplicitFlowNoNonce() throws Exception {
        URL busFile = OIDCFlowTest.class.getResource("client.xml");

        String address = "https://localhost:" + PORT + "/services/";
        WebClient client = WebClient.create(address, OAuth2TestUtils.setupProviders(),
                                            "alice", "security", busFile.toString());
        // Save the Cookie for the second request...
        WebClient.getConfig(client).getRequestContext().put(
            org.apache.cxf.message.Message.MAINTAIN_SESSION, Boolean.TRUE);

        // Get Access Token
        client.type("application/json").accept("application/json");
        client.query("client_id", "consumer-id");
        client.query("redirect_uri", "http://www.blah.apache.org");
        client.query("scope", "openid");
        client.query("response_type", "id_token");
        client.path("authorize-implicit/");
        Response response = client.get();

        try {
            response.readEntity(OAuthAuthorizationData.class);
            fail("Failure expected on no nonce");
        } catch (Exception ex) {
            // expected
        }

        // Add a nonce and it should succeed
        client.query("nonce", "1234565635");
        response = client.get();
        response.readEntity(OAuthAuthorizationData.class);
    }

    @org.junit.Test
    public void testImplicitFlowNoATHash() throws Exception {
        URL busFile = OIDCFlowTest.class.getResource("client.xml");

        String address = "https://localhost:" + PORT + "/services/";
        WebClient client = WebClient.create(address, OAuth2TestUtils.setupProviders(),
                                            "alice", "security", busFile.toString());
        // Save the Cookie for the second request...
        WebClient.getConfig(client).getRequestContext().put(
            org.apache.cxf.message.Message.MAINTAIN_SESSION, Boolean.TRUE);

        // Get Access Token
        client.type("application/json").accept("application/json");
        client.query("client_id", "consumer-id");
        client.query("redirect_uri", "http://www.blah.apache.org");
        client.query("scope", "openid");
        client.query("response_type", "id_token");
        client.query("nonce", "1234565635");
        client.query("max_age", "300");
        client.path("authorize-implicit/");
        Response response = client.get();

        OAuthAuthorizationData authzData = response.readEntity(OAuthAuthorizationData.class);

        // Now call "decision" to get the access token
        client.path("decision");
        client.type("application/x-www-form-urlencoded");

        Form form = new Form();
        form.param("session_authenticity_token", authzData.getAuthenticityToken());
        form.param("client_id", authzData.getClientId());
        form.param("redirect_uri", authzData.getRedirectUri());
        form.param("scope", authzData.getProposedScope());
        if (authzData.getResponseType() != null) {
            form.param("response_type", authzData.getResponseType());
        }
        if (authzData.getNonce() != null) {
            form.param("nonce", authzData.getNonce());
        }
        form.param("oauthDecision", "allow");

        response = client.post(form);

        String location = response.getHeaderString("Location");

        // Check IdToken
        String idToken = OAuth2TestUtils.getSubstring(location, "id_token");
        assertNotNull(idToken);

        JwsJwtCompactConsumer jwtConsumer = new JwsJwtCompactConsumer(idToken);
        JwtToken jwt = jwtConsumer.getJwtToken();
        Assert.assertNull(jwt.getClaims().getClaim(IdToken.ACCESS_TOKEN_HASH_CLAIM));
    }

    @org.junit.Test
    public void testJWTRequestNonmatchingResponseType() throws Exception {
        URL busFile = OIDCNegativeTest.class.getResource("client.xml");

        String address = "https://localhost:" + PORT + "/unsignedjwtservices/";
        WebClient client = WebClient.create(address, OAuth2TestUtils.setupProviders(),
                                            "alice", "security", busFile.toString());
        // Save the Cookie for the second request...
        WebClient.getConfig(client).getRequestContext().put(
            org.apache.cxf.message.Message.MAINTAIN_SESSION, Boolean.TRUE);

        JwtClaims claims = new JwtClaims();
        claims.setIssuer("consumer-id");
        claims.setIssuedAt(new Date().getTime() / 1000L);
        claims.setAudiences(
            Collections.singletonList("https://localhost:" + PORT + "/unsignedjwtservices/"));
        claims.setProperty("response_type", "token");

        JwsHeaders headers = new JwsHeaders();
        headers.setAlgorithm("none");

        JwtToken token = new JwtToken(headers, claims);

        JwsJwtCompactProducer jws = new JwsJwtCompactProducer(token);
        String request = jws.getSignedEncodedJws();

        AuthorizationCodeParameters parameters = new AuthorizationCodeParameters();
        parameters.setConsumerId("consumer-id");
        parameters.setScope("openid");
        parameters.setResponseType("code");
        parameters.setPath("authorize/");
        parameters.setRequest(request);

        // Get Authorization Code
        try {
            OAuth2TestUtils.getLocation(client, parameters);
            fail("Failure expected on a non-matching response_type");
        } catch (ResponseProcessingException ex) {
            // expected
        }
    }

    @org.junit.Test
    public void testJWTRequestNonmatchingClientId() throws Exception {
        URL busFile = OIDCNegativeTest.class.getResource("client.xml");

        String address = "https://localhost:" + PORT + "/unsignedjwtservices/";
        WebClient client = WebClient.create(address, OAuth2TestUtils.setupProviders(),
                                            "alice", "security", busFile.toString());
        // Save the Cookie for the second request...
        WebClient.getConfig(client).getRequestContext().put(
            org.apache.cxf.message.Message.MAINTAIN_SESSION, Boolean.TRUE);

        JwtClaims claims = new JwtClaims();
        claims.setIssuer("consumer-id");
        claims.setIssuedAt(new Date().getTime() / 1000L);
        claims.setAudiences(
            Collections.singletonList("https://localhost:" + PORT + "/unsignedjwtservices/"));
        claims.setProperty("client_id", "consumer-id2");

        JwsHeaders headers = new JwsHeaders();
        headers.setAlgorithm("none");

        JwtToken token = new JwtToken(headers, claims);

        JwsJwtCompactProducer jws = new JwsJwtCompactProducer(token);
        String request = jws.getSignedEncodedJws();

        AuthorizationCodeParameters parameters = new AuthorizationCodeParameters();
        parameters.setConsumerId("consumer-id");
        parameters.setScope("openid");
        parameters.setResponseType("code");
        parameters.setPath("authorize/");
        parameters.setRequest(request);

        // Get Authorization Code
        try {
            OAuth2TestUtils.getLocation(client, parameters);
            fail("Failure expected on a non-matching client id");
        } catch (ResponseProcessingException ex) {
            // expected
        }
    }

    @org.junit.Test
    public void testUserInfoRefreshToken() throws Exception {
        URL busFile = UserInfoTest.class.getResource("client.xml");

        String address = "https://localhost:" + PORT + "/services/";
        WebClient client = WebClient.create(address, OAuth2TestUtils.setupProviders(),
                                            "alice", "security", busFile.toString());
        // Save the Cookie for the second request...
        WebClient.getConfig(client).getRequestContext().put(
            org.apache.cxf.message.Message.MAINTAIN_SESSION, Boolean.TRUE);

        // Get Authorization Code
        String code = OAuth2TestUtils.getAuthorizationCode(client, "openid");
        assertNotNull(code);

        // Now get the access token
        client = WebClient.create(address, OAuth2TestUtils.setupProviders(),
                                  "consumer-id", "this-is-a-secret", busFile.toString());
        // Save the Cookie for the second request...
        WebClient.getConfig(client).getRequestContext().put(
            org.apache.cxf.message.Message.MAINTAIN_SESSION, Boolean.TRUE);

        ClientAccessToken accessToken =
            OAuth2TestUtils.getAccessTokenWithAuthorizationCode(client, code);
        assertNotNull(accessToken.getTokenKey());
        String oldAccessToken = accessToken.getTokenKey();
        assertTrue(accessToken.getApprovedScope().contains("openid"));

        String idToken = accessToken.getParameters().get("id_token");
        assertNotNull(idToken);

        // Refresh the access token
        client.type("application/x-www-form-urlencoded").accept("application/json");

        Form form = new Form();
        form.param("grant_type", "refresh_token");
        form.param("refresh_token", accessToken.getRefreshToken());
        form.param("client_id", "consumer-id");
        form.param("scope", "openid");
        Response response = client.post(form);

        accessToken = response.readEntity(ClientAccessToken.class);
        assertNotNull(accessToken.getTokenKey());
        assertNotNull(accessToken.getRefreshToken());
        accessToken.getParameters().get("id_token");
        assertNotNull(idToken);
        String newAccessToken = accessToken.getTokenKey();

        // Now test the UserInfoService.

        // The old Access Token should fail
        String userInfoAddress = "https://localhost:" + PORT + "/ui/plain/userinfo";
        WebClient userInfoClient = WebClient.create(userInfoAddress, OAuth2TestUtils.setupProviders(),
                                                    busFile.toString());
        userInfoClient.accept("application/json");
        userInfoClient.header("Authorization", "Bearer " + oldAccessToken);

        Response serviceResponse = userInfoClient.get();
        assertEquals(serviceResponse.getStatus(), 401);

        // The refreshed Access Token should work
        userInfoClient.replaceHeader("Authorization", "Bearer " + newAccessToken);
        serviceResponse = userInfoClient.get();
        assertEquals(serviceResponse.getStatus(), 200);

        UserInfo userInfo = serviceResponse.readEntity(UserInfo.class);
        assertNotNull(userInfo);

        assertEquals("alice", userInfo.getSubject());
        assertEquals("consumer-id", userInfo.getAudience());
    }


}
