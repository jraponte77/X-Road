/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.converter.GlobalConfWrapper;
import org.niis.xroad.restapi.exceptions.ConflictException;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * test client service
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Slf4j
@Transactional
public class ClientServiceIntegrationTest {

    private static final String SUBSYSTEM = "SS";
    private static final String SUBSYSTEM1 = "SS1";
    private static final String SUBSYSTEM2 = "SS2";
    private static final String SUBSYSTEM3 = "SS3";
    private static final String NAME_APPENDIX = "-name";

    @Autowired
    private ClientRepository clientRepository;
    private ClientService clientService;

    private byte[] pemBytes;
    private byte[] derBytes;
    private byte[] sqlFileBytes;

    @Before
    public void setup() throws Exception {
        GlobalConfWrapper globalConfWrapper = new GlobalConfWrapper() {
            @Override
            public List<MemberInfo> getGlobalMembers(String... instanceIdentifiers) {
                return new ArrayList<>(
                        Arrays.asList(getMemberInfoWithSubsystem(SUBSYSTEM), getMemberInfoWithSubsystem(SUBSYSTEM1),
                                getMemberInfoWithSubsystem(SUBSYSTEM2), getMemberInfoWithSubsystem(SUBSYSTEM3)));
            }

            @Override
            public String getMemberName(ClientId identifier) {
                return identifier.getSubsystemCode() + NAME_APPENDIX;
            }
        };
        clientService = new ClientService(clientRepository, globalConfWrapper);
        pemBytes = IOUtils.toByteArray(this.getClass().getClassLoader().
                getResourceAsStream("google-cert.pem"));
        derBytes = IOUtils.toByteArray(this.getClass().getClassLoader().
                getResourceAsStream("google-cert.der"));
        sqlFileBytes = IOUtils.toByteArray(this.getClass().getClassLoader().
                getResourceAsStream("data.sql"));
        assertTrue(pemBytes.length > 1);
        assertTrue(derBytes.length > 1);
        assertTrue(sqlFileBytes.length > 1);
    }

    @Test
    @WithMockUser(authorities = { "EDIT_CLIENT_INTERNAL_CONNECTION_TYPE",
            "VIEW_CLIENT_DETAILS" })
    public void updateConnectionType() {
        ClientId id = getM1Ss1ClientId();
        ClientType clientType = clientService.getClient(id);
        assertEquals("SSLNOAUTH", clientType.getIsAuthentication());
        assertEquals(2, clientType.getLocalGroup().size());

        try {
            clientService.updateConnectionType(id, "FUBAR");
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }

        clientService.updateConnectionType(id, "NOSSL");
        clientType = clientService.getClient(id);
        assertEquals("NOSSL", clientType.getIsAuthentication());
        assertEquals(2, clientType.getLocalGroup().size());
    }

    private ClientId getM1Ss1ClientId() {
        return ClientId.create("FI", "GOV", "M1", "SS1");
    }

    private MemberInfo getMemberInfoWithSubsystem(String subsystem) {
        return new MemberInfo(ClientId.create("FI", "GOV", "M1", subsystem), subsystem + NAME_APPENDIX);
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "ADD_CLIENT_INTERNAL_CERT" })
    public void addCertificatePem() throws Exception {

        ClientId id = getM1Ss1ClientId();
        ClientType clientType = clientService.getClient(id);
        assertEquals(0, clientType.getIsCert().size());

        clientService.addTlsCertificate(id, pemBytes);

        clientType = clientService.getClient(id);
        assertEquals(1, clientType.getIsCert().size());
        assertTrue(Arrays.equals(derBytes, clientType.getIsCert().get(0).getData()));
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "ADD_CLIENT_INTERNAL_CERT" })
    public void addInvalidCertificate() throws Exception {

        ClientId id = getM1Ss1ClientId();
        ClientType clientType = clientService.getClient(id);
        assertEquals(0, clientType.getIsCert().size());

        try {
            clientService.addTlsCertificate(id, sqlFileBytes);
            fail("should have thrown CertificateException");
        } catch (CertificateException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "ADD_CLIENT_INTERNAL_CERT" })
    public void addCertificateDer() throws Exception {

        ClientId id = getM1Ss1ClientId();
        ClientType clientType = clientService.getClient(id);
        assertEquals(0, clientType.getIsCert().size());

        clientService.addTlsCertificate(id, derBytes);

        clientType = clientService.getClient(id);
        assertEquals(1, clientType.getIsCert().size());
        assertTrue(Arrays.equals(derBytes, clientType.getIsCert().get(0).getData()));
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "ADD_CLIENT_INTERNAL_CERT" })
    public void addDuplicate() throws Exception {

        ClientId id = getM1Ss1ClientId();
        ClientType clientType = clientService.getClient(id);
        assertEquals(0, clientType.getIsCert().size());

        clientService.addTlsCertificate(id, derBytes);

        try {
            clientService.addTlsCertificate(id, pemBytes);
            fail("should have thrown ConflictException");
        } catch (ConflictException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "ADD_CLIENT_INTERNAL_CERT",
            "DELETE_CLIENT_INTERNAL_CERT" })
    public void deleteCertificate() throws Exception {

        ClientId id = getM1Ss1ClientId();
        ClientType clientType = clientService.getClient(id);
        assertEquals(0, clientType.getIsCert().size());

        clientService.addTlsCertificate(id, derBytes);
        String hash = CryptoUtils.calculateCertHexHash(derBytes);

        try {
            clientService.deleteTlsCertificate(id, "wrong hash");
            fail("should have thrown NotFoundException");
        } catch (NotFoundException expected) {
        }
        clientType = clientService.getClient(id);
        assertEquals(1, clientType.getIsCert().size());

        clientService.deleteTlsCertificate(id, hash);
        clientType = clientService.getClient(id);
        assertEquals(0, clientType.getIsCert().size());
    }

    @Test
    public void findLocalClientsByNameIncludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(SUBSYSTEM1 + NAME_APPENDIX, null, null,
                null, null, true);
        assertEquals(1, clients.size());
    }

    @Test
    public void findLocalClientsByInstanceIncludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(null, "FI", null,
                null, null, true);
        assertEquals(3, clients.size());
    }

    @Test
    public void findLocalClientsByClassIncludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(null, null, "GOV",
                null, null, true);
        assertEquals(3, clients.size());
    }

    @Test
    public void findLocalClientsByInstanceAndMemberCodeIncludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(null, "FI", null,
                "M1", null, true);
        assertEquals(3, clients.size());
    }

    @Test
    public void findLocalClientsByAllTermsIncludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(SUBSYSTEM1 + NAME_APPENDIX, "FI",
                "GOV", "M1", SUBSYSTEM1, true);
        assertEquals(1, clients.size());
    }

    @Test
    public void findLocalClientsByNameExcludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(SUBSYSTEM1 + NAME_APPENDIX, null, null,
                null, null, false);
        assertEquals(1, clients.size());
    }

    @Test
    public void findLocalClientsByInstanceExcludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(null, "FI", null,
                null, null, false);
        assertEquals(2, clients.size());
    }

    @Test
    public void findLocalClientsByClassExcludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(null, null, "GOV",
                null, null, false);
        assertEquals(2, clients.size());
    }

    @Test
    public void findLocalClientsByInstanceAndMemberCodeExcludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(null, "FI", null,
                "M1", null, false);
        assertEquals(2, clients.size());
    }

    @Test
    public void findLocalClientsByAllTermsExcludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(SUBSYSTEM1 + NAME_APPENDIX, "FI",
                "GOV", "M1", SUBSYSTEM1, false);
        assertEquals(1, clients.size());
    }

    @Test
    public void findGlobalMembers() {

    }
}
