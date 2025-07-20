package Components.Server;

import org.springframework.stereotype.Component;

@Component
public class RedisConfig {
    private String role;
    private int port;
    private String masterHost;
    private int masterPort;
    public String getMasterHost() {
        return masterHost;
    }

    public int getMasterPort() {
        return masterPort;
    }

    public void setMasterPort(int masterPort) {
        this.masterPort = masterPort;
    }

    public void setMasterHost(String masterHost) {
        this.masterHost = masterHost;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
