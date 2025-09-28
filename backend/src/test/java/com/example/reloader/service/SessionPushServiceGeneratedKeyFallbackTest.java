package com.example.reloader.service;

import com.example.reloader.config.ExternalDbConfig;
import com.example.reloader.entity.LoadSession;
import com.example.reloader.entity.LoadSessionPayload;
import com.example.reloader.repository.LoadSessionPayloadRepository;
import com.example.reloader.repository.LoadSessionRepository;
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
public class SessionPushServiceGeneratedKeyFallbackTest {

    @Autowired
    LoadSessionRepository sessionRepo;

    @Autowired
    LoadSessionPayloadRepository payloadRepo;

    @Autowired
    SessionPushService pushService;

    @SpyBean
    ExternalDbConfig externalDbConfig;

    @Test
    public void testFallbackWhenNoGeneratedKeys() throws Exception {
        // create session + payloads
        LoadSession s = new LoadSession();
        s.setSenderId(99);
        s.setSite("EXAMPLE_SITE");
        s.setSource("test");
        s.setStatus("NEW");
        s.setTotalPayloads(1);
        sessionRepo.save(s);

        LoadSessionPayload p1 = new LoadSessionPayload(s, "MFOO,DFOO");
        payloadRepo.save(p1);

        // obtain a real connection from the real ExternalDbConfig
    Connection real = externalDbConfig.getConnection(s.getSite());
    String insertSql = "insert into DTP_SENDER_QUEUE_ITEM (id_metadata, id_data, id_sender, record_created) values (?, ?, ?, ?)";
    PreparedStatement realPs = real.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);

        // create a proxy for PreparedStatement that delegates to the real PreparedStatement
        InvocationHandler psHandler = (proxy, method, args) -> {
            if ("getGeneratedKeys".equals(method.getName())) {
                // return an empty SimpleResultSet
                SimpleResultSet empty = new SimpleResultSet();
                empty.addColumn("ID", Types.BIGINT, 10, 0);
                return empty;
            }
            return method.invoke(realPs, args);
        };
        PreparedStatement psProxy = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class[]{PreparedStatement.class},
                psHandler
        );

        // create a Connection proxy that returns our PreparedStatement proxy when prepareStatement is called
        InvocationHandler connHandler = (proxy, method, args) -> {
            if ("prepareStatement".equals(method.getName()) && args != null && args.length >= 1 && args[0] instanceof String) {
                String sqlArg = (String) args[0];
                if (insertSql.equals(sqlArg)) return psProxy;
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
