package Components.Server;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RedisConfig {
    private String role;
    private int port;
    private String masterHost;
    private int masterPort;
    private String masterReplId = null;
    private Long masterReplOffset = null;
    private String dir; // Add this line
    private String dbfilename; // Add this line

    public Long getMasterReplOffset() {
        if(masterReplOffset == null){
            masterReplOffset = 0L;
        }
        return masterReplOffset;
    }

    public void setMasterReplOffset(Long masterReplOffset) {
        this.masterReplOffset = masterReplOffset;
    }

    public String getMasterReplId() {
        if(masterReplId == null){
            masterReplId = UUID.randomUUID().toString().replace("-","") +
                    UUID.randomUUID().toString().replace("-","").substring(0,8);

        }
        return masterReplId;
    }

    public void setMasterReplId(String masterReplId) {
        this.masterReplId = masterReplId;
    }

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

    // Add these new methods
    public String getDir() {
        return dir;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public String getDbfilename() {
        return dbfilename;
    }

    public void setDbfilename(String dbfilename) {
        this.dbfilename = dbfilename;
    }
}