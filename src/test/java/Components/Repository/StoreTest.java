package Components.Repository;

import Components.Service.RespSerializer;
import Config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = AppConfig.class)
class StoreTest {

    @Autowired
    private Store store;

    @Autowired
    private RespSerializer respSerializer;

    @BeforeEach
    public void setUp(){
        store.map.clear();
    }

    @Test
    public void setAndGetKey(){
        String key = "testKey";
        String value = "testValue";

        String setResult = store.set(key,value);
        String getResult = store.get(key);

        assertEquals("+OK\r\n", setResult);
        assertEquals(respSerializer.serializeBulkString(value), getResult);
    }

    @Test
    public void setAndGetKeyExpire() throws InterruptedException {
        String key = "testKey";
        String value = "testValue";
        int expireMilli = 100;

        String setResult = store.set(key,value, expireMilli);
        String getResult = store.get(key);

        Thread.sleep((long) 100.0);

        String getResultExpire = store.get(key);

        assertEquals("+OK\r\n", setResult);
        assertEquals(respSerializer.serializeBulkString(value), getResult);

        assertEquals("$-1\r\n", getResultExpire);
    }

    @Test
    public void setAndGetKeyExpireReset() throws InterruptedException {
        String key = "testKey";
        String value = "testValue";
        String value2 = "testValue2";
        int expireMilli = 100;

        String setResult = store.set(key,value, expireMilli);
        String getResult = store.get(key);
        String setResultReset= store.set(key,value2, expireMilli*2);

        Thread.sleep((long) 100.0);
        String getResultReset = store.get(key);

        String getResultExpire = store.get(key);

        assertEquals("+OK\r\n", setResult);
        assertEquals("+OK\r\n", setResultReset);
        assertEquals(respSerializer.serializeBulkString(value), getResult);
        assertEquals(respSerializer.serializeBulkString(value2), getResultReset);
    }

    @Test
    public void testConcurrentlySetting(){
        List<CompletableFuture<Void>> l = new ArrayList<>();

        for(int i =0; i<10; i++){
            int finalI = i;
            CompletableFuture<Void> future =  CompletableFuture.runAsync(() -> {
               for(int j =0 ; j<100; j++){
                   store.set("key"+j+"," + finalI,"Hakuna matata" );
               }
            });

            l.add(future);
        }
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                l.toArray(new CompletableFuture[l.size()])
        );

        allFutures.join();
        assertEquals(1000, store.getKeys().size());
    }
}