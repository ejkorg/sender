package com.onsemi.cim.apps.exensio.exensioDearchiver.service;

import com.onsemi.cim.apps.exensio.exensioDearchiver.config.ExternalDbConfig;
import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.LoadSession;
import com.onsemi.cim.apps.exensio.exensioDearchiver.entity.LoadSessionPayload;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.LoadSessionPayloadRepository;
import com.onsemi.cim.apps.exensio.exensioDearchiver.repository.LoadSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

@SpringBootTest
@TestPropertySource(properties={"reloader.use-h2-external=true","external-db.allow-writes=true"})
public class SessionPushServiceConstraintSkippedTest {

    @Autowired
    LoadSessionRepository sessionRepo;

    @Autowired
    LoadSessionPayloadRepository payloadRepo;

    @Autowired
    SessionPushService pushService;

    @SpyBean
    ExternalDbConfig externalDbConfig;

    @Test
    public void testConstraintMarkedSkipped() throws Exception {
        LoadSession s = new LoadSession();
        s.setSenderId(5);
        s.setSite("SKIP_SITE");
        s.setSource("test");
        s.setStatus("NEW");
        s.setTotalPayloads(1);
        sessionRepo.save(s);

        LoadSessionPayload p1 = new LoadSessionPayload(s, "M, D");
        payloadRepo.save(p1);

        Connection real = externalDbConfig.getConnection(s.getSite());
        PreparedStatement realPs = real.prepareStatement("insert into DTP_SENDER_QUEUE_ITEM (id_metadata, id_data, id_sender, record_created) values (?, ?, ?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS);

        InvocationHandler psHandler = (proxy, method, args) -> {
            if ("executeUpdate".equals(method.getName())) {
                throw new java.sql.SQLIntegrityConstraintViolationException("unique constraint", "23000");
            }
            return method.invoke(realPs, args);
        };

        PreparedStatement psProxy = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class[]{PreparedStatement.class},
                psHandler
        );

        InvocationHandler connHandler = (proxy, method, args) -> {
            if ("prepareStatement".equals(method.getName()) && args != null && args.length >= 1 && args[0] instanceof String) {
                String sqlArg = (String) args[0];
                if (sqlArg != null && sqlArg.toLowerCase().contains("dtp_sender_queue_item")) return psProxy;
                return method.invoke(real, args);
            }
            return method.invoke(real, args);
        };

        Connection connProxy = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                connHandler
        );

        doReturn(connProxy).when(externalDbConfig).getConnection(s.getSite());

        int pushed = pushService.pushSessionBatch(s.getId(), 10);
        assertEquals(0, pushed);

        int skipped = payloadRepo.countBySessionIdAndStatus(s.getId(), "SKIPPED");
        assertEquals(1, skipped);
    }
}
