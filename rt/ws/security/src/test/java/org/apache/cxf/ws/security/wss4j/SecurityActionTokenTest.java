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
package org.apache.cxf.ws.security.wss4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.DOMUtils.NullResolver;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.phase.PhaseInterceptor;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.wss4j.common.EncryptionActionToken;
import org.apache.wss4j.common.SignatureActionToken;
import org.apache.wss4j.dom.WSConstants;
import org.apache.wss4j.dom.WSDataRef;
import org.apache.wss4j.dom.engine.WSSecurityEngineResult;
import org.apache.wss4j.dom.handler.HandlerAction;
import org.apache.wss4j.dom.handler.WSHandlerConstants;
import org.apache.wss4j.dom.handler.WSHandlerResult;
import org.junit.Test;


/**
 * Some tests for configuring outbound security using SecurityActionTokens.
 */
public class SecurityActionTokenTest extends AbstractSecurityTest {

    @Test
    public void testSignature() throws Exception {
        SignatureActionToken actionToken = new SignatureActionToken();
        actionToken.setCryptoProperties("outsecurity.properties");
        actionToken.setUser("myalias");
        List<HandlerAction> actions = 
            Collections.singletonList(new HandlerAction(WSConstants.SIGN, actionToken));
        
        Map<String, Object> outProperties = new HashMap<>();
        outProperties.put(WSHandlerConstants.HANDLER_ACTIONS, actions);
        outProperties.put(WSHandlerConstants.PW_CALLBACK_REF, new TestPwdCallback());
        
        Map<String, String> inProperties = new HashMap<>();
        inProperties.put(WSHandlerConstants.ACTION, WSHandlerConstants.SIGNATURE);
        inProperties.put(WSHandlerConstants.SIG_VER_PROP_FILE, "insecurity.properties");
        
        List<String> xpaths = new ArrayList<String>();
        xpaths.add("//wsse:Security");
        xpaths.add("//wsse:Security/ds:Signature");

        List<WSHandlerResult> handlerResults = 
            getResults(makeInvocation(outProperties, xpaths, inProperties));
        WSSecurityEngineResult actionResult =
            handlerResults.get(0).getActionResults().get(WSConstants.SIGN).get(0);
         
        X509Certificate certificate = 
            (X509Certificate) actionResult.get(WSSecurityEngineResult.TAG_X509_CERTIFICATE);
        assertNotNull(certificate);
    }
    
    @Test
    public void testEncryption() throws Exception {
        EncryptionActionToken actionToken = new EncryptionActionToken();
        actionToken.setCryptoProperties("outsecurity.properties");
        actionToken.setUser("myalias");
        List<HandlerAction> actions = 
            Collections.singletonList(new HandlerAction(WSConstants.ENCR, actionToken));
        
        Map<String, Object> outProperties = new HashMap<String, Object>();
        outProperties.put(WSHandlerConstants.HANDLER_ACTIONS, actions);
        
        Map<String, String> inProperties = new HashMap<String, String>();
        inProperties.put(WSHandlerConstants.ACTION, WSHandlerConstants.ENCRYPT);
        inProperties.put(WSHandlerConstants.DEC_PROP_FILE, "insecurity.properties");
        inProperties.put(
            WSHandlerConstants.PW_CALLBACK_CLASS, 
            "org.apache.cxf.ws.security.wss4j.TestPwdCallback"
        );
        
        List<String> xpaths = new ArrayList<String>();
        xpaths.add("//wsse:Security");
        xpaths.add("//s:Body/xenc:EncryptedData");

        List<WSHandlerResult> handlerResults = 
            getResults(makeInvocation(outProperties, xpaths, inProperties));

        assertNotNull(handlerResults);
        assertSame(handlerResults.size(), 1);
        //
        // This should contain exactly 1 protection result
        //
        final java.util.List<WSSecurityEngineResult> protectionResults =
            handlerResults.get(0).getResults();
        assertNotNull(protectionResults);
        assertSame(protectionResults.size(), 1);
        //
        // This result should contain a reference to the decrypted element,
        // which should contain the soap:Body Qname
        //
        final java.util.Map<String, Object> result =
            protectionResults.get(0);
        final java.util.List<WSDataRef> protectedElements =
            CastUtils.cast((List<?>)result.get(WSSecurityEngineResult.TAG_DATA_REF_URIS));
        assertNotNull(protectedElements);
        assertSame(protectedElements.size(), 1);
        assertEquals(
            protectedElements.get(0).getName(),
            new javax.xml.namespace.QName(
                "http://schemas.xmlsoap.org/soap/envelope/",
                "Body"
            )
        );
    }
    
    private byte[] getMessageBytes(Document doc) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        XMLStreamWriter byteArrayWriter = StaxUtils.createXMLStreamWriter(outputStream);
        StaxUtils.writeDocument(doc, byteArrayWriter, false);
        byteArrayWriter.flush();
        return outputStream.toByteArray();
    }

    private List<WSHandlerResult> getResults(SoapMessage inmsg) {
        final List<WSHandlerResult> handlerResults = 
            CastUtils.cast((List<?>)inmsg.get(WSHandlerConstants.RECV_RESULTS));
        return handlerResults;
    }
    
    private SoapMessage makeInvocation(
        Map<String, Object> outProperties,
        List<String> xpaths,
        Map<String, String> inProperties
    ) throws Exception {
        Document doc = readDocument("wsse-request-clean.xml");

        WSS4JOutInterceptor ohandler = new WSS4JOutInterceptor();
        PhaseInterceptor<SoapMessage> handler = ohandler.createEndingInterceptor();

        SoapMessage msg = new SoapMessage(new MessageImpl());
        Exchange ex = new ExchangeImpl();
        ex.setInMessage(msg);

        SOAPMessage saajMsg = MessageFactory.newInstance().createMessage();
        SOAPPart part = saajMsg.getSOAPPart();
        part.setContent(new DOMSource(doc));
        saajMsg.saveChanges();

        msg.setContent(SOAPMessage.class, saajMsg);
        
        for (String key : outProperties.keySet()) {
            msg.put(key, outProperties.get(key));
        }

        handler.handleMessage(msg);

        doc = part;

        for (String xpath : xpaths) {
            assertValid(xpath, doc);
        }

        byte[] docbytes = getMessageBytes(doc);
        XMLStreamReader reader = StaxUtils.createXMLStreamReader(new ByteArrayInputStream(docbytes));

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        dbf.setValidating(false);
        dbf.setIgnoringComments(false);
        dbf.setIgnoringElementContentWhitespace(true);
        dbf.setNamespaceAware(true);

        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver(new NullResolver());
        doc = StaxUtils.read(db, reader, false);

        WSS4JInInterceptor inHandler = new WSS4JInInterceptor();

        SoapMessage inmsg = new SoapMessage(new MessageImpl());
        ex.setInMessage(inmsg);
        inmsg.setContent(SOAPMessage.class, saajMsg);

        for (String key : inProperties.keySet()) {
            inHandler.setProperty(key, inProperties.get(key));
        }

        inHandler.handleMessage(inmsg);

        return inmsg;
    }
    
    // FOR DEBUGGING ONLY
    /*private*/ static String serialize(Document doc) {
        return StaxUtils.toString(doc);
    }
}
