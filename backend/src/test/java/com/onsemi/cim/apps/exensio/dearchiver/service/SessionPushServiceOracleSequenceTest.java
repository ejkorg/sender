package com.onsemi.cim.apps.exensio.dearchiver.service;

import com.onsemi.cim.apps.exensio.dearchiver.config.ExternalDbConfig;
import com.onsemi.cim.apps.exensio.dearchiver.entity.LoadSession;
import com.onsemi.cim.apps.exensio.dearchiver.entity.LoadSessionPayload;
import com.onsemi.cim.apps.exensio.dearchiver.repository.LoadSessionPayloadRepository;
import com.onsemi.cim.apps.exensio.dearchiver.repository.LoadSessionRepository;
import org.h2.tools.SimpleResultSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

@SpringBootTest
@TestPropertySource(properties={"reloader.use-h2-external=true","external-db.allow-writes=true"})
public class SessionPushServiceOracleSequenceTest {

    @Autowired
    LoadSessionRepository sessionRepo;

    @Autowired
    LoadSessionPayloadRepository payloadRepo;

    @Autowired
    SessionPushService pushService;

    @SpyBean
    ExternalDbConfig externalDbConfig;

    @Test
    public void testOracleSequencePath() throws Exception {
        // create session + payloads
        LoadSession s = new LoadSession();
        s.setSenderId(77);
        s.setSite("ORACLE_SITE");
        s.setSource("test");
        s.setStatus("NEW");
        s.setTotalPayloads(1);
        sessionRepo.save(s);

        LoadSessionPayload p1 = new LoadSessionPayload(s, "META1,DATA1");
        payloadRepo.save(p1);

        // obtain a real connection from the real ExternalDbConfig (this will be H2 in tests)
        Connection real = externalDbConfig.getConnection(s.getSite());

        // prepare a SimpleResultSet to return a sequence value
        SimpleResultSet seqRs = new SimpleResultSet();
        seqRs.addColumn("NEXTVAL", Types.BIGINT, 10, 0);
        seqRs.addRow(12345L);

        // proxy for PreparedStatement for sequence SELECT
        InvocationHandler seqPsHandler = (proxy, method, args) -> {
            if ("executeQuery".equals(method.getName())) {
                return seqRs;
            }
            return method.invoke(real.prepareStatement("select DTP_SENDER_QUEUE_ITEM_SEQ.nextval from dual"), args);
        };
        PreparedStatement seqPsProxy = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class[]{PreparedStatement.class},
                seqPsHandler
        );

        // proxy DatabaseMetaData to report Oracle product name
        DatabaseMetaData realMd = real.getMetaData();
        InvocationHandler mdHandler = (proxy, method, args) -> {
            if ("getDatabaseProductName".equals(method.getName())) {
                return "Oracle";
            }
            return method.invoke(realMd, args);
        };
        DatabaseMetaData mdProxy = (DatabaseMetaData) Proxy.newProxyInstance(
                DatabaseMetaData.class.getClassLoader(),
                new Class[]{DatabaseMetaData.class},
                mdHandler
        );

        // proxy Connection to return our metadata and sequence PreparedStatement when appropriate
        InvocationHandler connHandler = (proxy, method, args) -> {
            if ("getMetaData".equals(method.getName())) return mdProxy;
            if ("prepareStatement".equals(method.getName()) && args != null && args.length >= 1 && args[0] instanceof String) {
                String sqlArg = (String) args[0];
                if (sqlArg != null && sqlArg.trim().toLowerCase().contains("nextval")) return seqPsProxy;
                // delegate other prepareStatement calls (like insert) to real connection
                return method.invoke(real, args);
            }
            return method.invoke(real, args);
        };

        Connection connProxy = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                connHandler
        );

        // stub the ExternalDbConfig to return our proxy connection
        doReturn(connProxy).when(externalDbConfig).getConnection(s.getSite());

        int pushed = pushService.pushSessionBatch(s.getId(), 10);
        assertEquals(1, pushed);

        int pushedCount = payloadRepo.countBySessionIdAndStatus(s.getId(), "PUSHED");
        assertEquals(1, pushedCount);
    }
}
