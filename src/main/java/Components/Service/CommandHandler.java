package Components.Service;


import Components.Infra.Client;
import Components.Infra.ConnectionPool;
import Components.Infra.Slave;
import Components.Repository.Store;
import Components.Repository.Value;
import Components.Server.RedisConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class CommandHandler {
    private static final String emptyRdbFile = "UkVESVMwMDEx+glyZWRpcy12ZXIFNy4yLjD6CnJlZGlzLWJpdHPAQPoFY3RpbWXCbQi8ZfoIdXNlZC1tZW3CsMQQAPoIYW9mLWJhc2XAAP/wbjv+wP9aog==";
    private static final Logger logger = Logger.getLogger(CommandHandler.class.getName());

    @Autowired
    public RespSerializer respSerializer;

    @Autowired
    public Store store;

    @Autowired
    public RedisConfig redisConfig;

    @Autowired
    public ConnectionPool connectionPool;

    public String ping(String[] command){
        return "+PONG\r\n";
    }

    public String echo(String[] command){
        return respSerializer.serializeBulkString(command[1]);
    }

    public String set(String[] command){
        try {
            String key = command[1];
            String value = command[2];

            int pxFlag = Arrays.stream(command).toList().indexOf("px");
            // if there is no px flag then the above command will return -1

            if(pxFlag > -1){
                int delta = Integer.parseInt(command[pxFlag + 1]);
                return store.set(key, value, delta);
            }else {
                return store.set(key, value);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE,e.getMessage());
            return "$-1\r\n";
        }

    }

    public String get (String[] command){
        try {
            String key = command[1];
            return store.get(key);
        } catch (Exception e) {
            logger.log(Level.SEVERE,e.getMessage());
            return "$-1\r\n";
        }

    }

    public String info(String[] command){
        int replication = Arrays.stream(command).toList().indexOf("replication");

        if(replication> -1){
            String role = "role:" + redisConfig.getRole();
            String masterReplId = "master_replid:" + redisConfig.getMasterReplId();
            String masterReplOffset = "master_repl_offset:" + redisConfig.getMasterReplOffset();

            String[] info = new String[]{ role, masterReplId, masterReplOffset};
            String replicationData = String.join("\r\n",info);

            return respSerializer.serializeBulkString(replicationData);
        }

        return "";
    }

    public String repelconf(String[] command, Client client) {
        switch (command[1]){
            case "listening-port":
                connectionPool.removeClient(client);
                Slave s = new Slave(client);
                connectionPool.addSlave(s);
                return "+OK\r\n";
            case "GETACK":
                String[] replconfAck = new String[]{"REPLCONF", "ACK", redisConfig.getMasterReplOffset()+""};
                return respSerializer.respArray(replconfAck);
            case "ACK":
                int ackResponse = Integer.parseInt(command[2]);
                connectionPool.slaveAck(ackResponse);
                return "";
            case "capa":
                Slave slave = null;
                for(Slave ss : connectionPool.getSlaves()){
                    if(ss.connection.equals(client)){
                        slave = ss;
                        break;
                    }
                }
                for(int i = 0; i<command.length; i++){
                    if(command[i].equals("capa")){
                        slave.capabilities.add(command[i+1]);
                    }
                }
                return "+OK\r\n";
        }
        return "+OK\r\n";
    }

    public byte[] concatinate(byte[] a, byte[] b){
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result,0,a.length);
        System.arraycopy(b, 0, result,a.length,b.length);
        return result;
    }

    public ResponseDto psync(String[] command) {
        String replicationIdMaster = command[1];
        String replicationOffsetMaster = command[2];

        if(replicationIdMaster.equals("?") && replicationOffsetMaster.equals("-1")){
            String replicationId = redisConfig.getMasterReplId();
            long replicationOffset = redisConfig.getMasterReplOffset();
            String res = "+FULLRESYNC " + replicationId+" "+ replicationOffset+ "\r\n";

            byte[] rdbFileData = Base64.getDecoder().decode(emptyRdbFile);
            String length = rdbFileData.length+"";
            String fullResyncHeader = "$"+ length+ "\r\n";
            byte[] header = fullResyncHeader.getBytes();
            connectionPool.slavesThatAreCaughtUp++;
            return new ResponseDto(res, concatinate(header,rdbFileData));
        }else {
            return new ResponseDto("Options are not supported yet");
        }
    }

    public String wait(String[] command, Instant now) {
        String[] getAcckArr = new String[]{"REPLCONF", "GETACK", "*"};
        String getAck = respSerializer.respArray(getAcckArr);
        byte[] bytes = getAck.getBytes();
        int bufferSize  = bytes.length;
        int required = Integer.parseInt(command[1]); // number of slaves we are required to wait for
        int time = Integer.parseInt(command[2]);

        for(Slave slave: connectionPool.getSlaves()){
            CompletableFuture.runAsync(() -> {
                try {
                    slave.connection.send(bytes);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        int res = 0;
        while (true){
            if(Duration.between(now, Instant.now()).toMillis() >= time){
                break;
            }
            if(res>=required){
                break;
            }
            res = connectionPool.slavesThatAreCaughtUp;
        }
        connectionPool.bytesSentToSlaves+=bufferSize;
        if(res>required){
            return respSerializer.respInteger(required);
        }
        return respSerializer.respInteger(res);
    }

    public String incr(String[] command) {
        String key = command[1];
        String res = "";
         try {
             Value value = store.getValue(key);
             if(value == null){
                 store.set(key, "0");
                 value = store.getValue(key);
             }

             int val = Integer.parseInt(value.val);
             val++;
             value.val = val+"";

             res = respSerializer.respInteger(val);

         } catch (Exception e) {
             res = "-ERR value is not an integer or out of range\r\n";
         }

        return res;
    }

    public BiFunction<String[], Map<String, Value>, String > getTransactionalCommandCacheApplier(){
        final Store localStore = this.store;
        final RespSerializer localSerializer = this.respSerializer;

        return (String[] command, Map<String, Value> map) -> {
            String res = "";
            switch (command[0]){
                case "SET":
                    res = handleSetCommandTransactional(command, map, localSerializer, localStore);
                    break;
                case "GET":
                    res = handleGetCommandTransactional(command,map ,res, localSerializer, localStore);
                    break;
                case "INCR":
                    res = handleIncrCommandTransactional(command, map,res, localSerializer, localStore);
                    break;
                case "DEL":
                    String key = command[1];
                    Value val = localStore.getValue(key);
                    val.isDeletedinTransaction = true;
                    res = handleDelCommandTransactional(command, map,res, localSerializer, localStore);
                    break;
                default:
                    res = "-ERR unknown command "+ command[0]+"\r\n";
                    break;
            }
            return res;
        };
    }

    private String handleDelCommandTransactional(String[] command, Map<String, Value> map, String res, RespSerializer localSerializer, Store localStore) {
        String key = command[1];
        Value valuetouse ;
        Value cachedValue = map.getOrDefault(key, null);
        if(cachedValue == null){
            Value storeValue = localStore.getValue(key);
            if(storeValue == null){
                return "-ERR deleting an invalid key\r\n";
            }else {
                valuetouse = new Value(storeValue.val, storeValue.created, storeValue.expiry);

            }
        }else {
            valuetouse = cachedValue;
        }
        valuetouse.isDeletedinTransaction = true;
        map.put(key, valuetouse);
        return "+OK\r\n";
    }

    private String handleIncrCommandTransactional(String[] command, Map<String, Value> map,
                                                  String res,RespSerializer localSerializer,Store localStore) {

        try {
            String key = command[1];
            Value valuetouse;
            Value cachedValue = map.getOrDefault(key, null);
            if(cachedValue == null){
                Value storeValue = localStore.getValue(key);
                if(storeValue == null){
                    valuetouse = new Value("0", LocalDateTime.now(), LocalDateTime.MAX);
                }else {
                    valuetouse = new Value(storeValue.val, storeValue.created, storeValue.expiry);
                }
            }else {
                valuetouse = cachedValue;

            }
            int val = Integer.parseInt(valuetouse.val);
            val++;
            valuetouse.val = String.valueOf(val);
            map.put(key, valuetouse);

            return respSerializer.respInteger(val);

        } catch (Exception e) {
            return "-ERR value is not an integer or out of range\r\n";
        }

    }

    private String handleGetCommandTransactional(String[] command,Map<String, Value> map, String res,RespSerializer localSerializer,Store localStore) {
        String key = command[1];
        Value valuetouse ;
        Value cachedValue = map.getOrDefault(key, null);
        if(cachedValue == null){
            Value storeValue = localStore.getValue(key);
            if(storeValue == null){
                return localStore.get(key);
            }else {
                valuetouse = new Value(storeValue.val, storeValue.created, storeValue.expiry);
                map.put(key, valuetouse);
            }
        }else {
            valuetouse = cachedValue;

        }
        return respSerializer.serializeBulkString(valuetouse.val);
    }

    private String handleSetCommandTransactional(String[] command, Map<String, Value> map,RespSerializer localSerializer,Store localStore) {
        String key = command[1];
        Value newValue = new Value(command[2], LocalDateTime.now(), LocalDateTime.MAX);
        map.put(key, newValue);
        return "+OK\r\n";
    }
}
