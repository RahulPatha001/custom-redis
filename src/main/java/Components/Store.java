package Components;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class Store {

    @Autowired
    RespSerializer respSerializer;

    public ConcurrentHashMap<String, Value> map;

    public Store(){
        map = new ConcurrentHashMap<>();
    }

    public Set<String> getKeys(){
        return map.keySet();
    }

    public String set(String key, String val){
        try {
            Value value = new Value(val, LocalDateTime.now(),LocalDateTime.MAX);
            map.put(key, value);

            return "+OK\r\n";
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "$-1\r\n";
        }
    }

    public String get(String key){
        try {
            Value value = map.get(key);
            return respSerializer.serializeBulkString(value.val);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return "$-1\r\n";
        }
    }
}
