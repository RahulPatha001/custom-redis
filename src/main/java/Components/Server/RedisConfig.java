package Components.Server;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class RedisConfig {
    public String role;
    public int port;

//    public RedisConfig(int port) {
//        this.role = "master";
//        this.port = port;
//    }
//
//    public RedisConfig(String role, int port) {
//        this.role = role;
//        this.port = port;
//    }
}
