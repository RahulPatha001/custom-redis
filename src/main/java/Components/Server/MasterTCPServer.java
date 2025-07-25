package Components.Server;

import Components.Infra.ConnectionPool;
import Components.Infra.Slave;
import Components.Repository.Store;
import Components.Repository.Value;
import Components.Service.CommandHandler;
import Components.Service.RespSerializer;
import Components.Infra.Client;
import Components.Service.ResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class MasterTCPServer {
    private static final Logger logger = Logger.getLogger(MasterTCPServer.class.getName());

    @Autowired
    private RespSerializer respSerializer;

    @Autowired
    private CommandHandler commandHandler;

    @Autowired
    private RedisConfig redisConfig;

    @Autowired
    private ConnectionPool connectionPool;

    @Autowired
    private Store store;

    public void startWorking(){

        ServerSocket serverSocket = null;
        Socket clientSocket = null;
        int port = redisConfig.getPort();
        try {
            serverSocket = new ServerSocket(port);
            // Since the tester restarts your program quite often, setting SO_REUSEADDR
            // ensures that we don't run into 'Address already in use' errors
            serverSocket.setReuseAddress(true);
            // Wait for connection from client.
            int id = 0;

            while (true){
                id++;
                clientSocket = serverSocket.accept();
                Socket finalClientSocket = clientSocket;
                InputStream inputStream = clientSocket.getInputStream();
                OutputStream outputStream = clientSocket.getOutputStream();

                Client client = new Client(finalClientSocket, inputStream, outputStream, id);
                CompletableFuture.runAsync(() ->{
                    try {
                        handleClient(client);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE,e.getMessage());
        } finally {
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE,e.getMessage());
            }
        }
    }

    public void handleClient(Client client) throws IOException {
        connectionPool.addClient(client);
        while(client.socket.isConnected()){
            byte[] buffer = new byte[client.socket.getReceiveBufferSize()];
            int bytesRead = client.inputStream.read(buffer);
            if(bytesRead > 0){
                // bytes parsing into string
                List<String[]>commands =  respSerializer.deserialize(buffer);
                for(String[] command: commands){
                    handleCommand(command, client);
                }
            }
        }
        connectionPool.removeClient(client);
        connectionPool.removeSlave(client);
    }

    public void handleCommand(String[] command, Client client) throws IOException{
        if(!client.getTransactionalContext()){
            ResponseDto responseDto = caseHandler(command, client);
            client.send(responseDto);
        }else if(!isTransactionalControlCommand(command[0])) {
            // we're in transactional context which means the command is a normal one
            addCommandToTransaction(command, client);
        }else {
            // wr're in transactional context which means the command is either discard or exec.
            transactionalController(command, client);
        }
    }

    private void transactionalController(String[] command, Client client) throws IOException {
        // control comes here when we're in transactional context
        switch (command[0]){
            case "EXEC":
                if(client.commandQueue == null ||client.commandQueue.isEmpty() ){
                    client.send("*0\r\n");
                    client.endTransaction();
                    return;
                }

                Queue<String []> commands = new LinkedList<>(client.commandQueue);

                BiFunction<String[], Map<String, Value>, String > transactionalCacheApplier = commandHandler.getTransactionalCommandCacheApplier();

                store.executeTransaction(client, transactionalCacheApplier);

                client.endTransaction();

                while (!commands.isEmpty()){
                    String [] commandToPropagate = commands.poll();
                    String commandResp = respSerializer.respArray(commandToPropagate);
                    byte[] toCount = commandResp.getBytes();
                    connectionPool.bytesSentToSlaves += toCount.length;
                    CompletableFuture.runAsync(()-> propagate(commandToPropagate));
                }

                String response = respSerializer.respArray(client.transactionalResponse);

                client.send(response);
                break;

            case "DISCARD":
                client.endTransaction();
                client.send("+OK\r\n");
                break;

        }
    }

    private void addCommandToTransaction(String[] command, Client client) throws IOException {
        client.commandQueue.offer(command);
        client.send("+QUEUED\r\n");
    }

    private boolean isTransactionalControlCommand(String command) {
        return switch (command){
            case "EXEC", "DISCARD" -> true;
            default -> false;
        };

    }

    public ResponseDto caseHandler(String[] command, Client client){

        // control comes here when client is not in transaction
        String res = "";
        byte[] data = null;

        switch (command[0]){
            case "PING":
                res = commandHandler.ping(command);
                break;
            case "INCR":
                res = commandHandler.incr(command);
                break;
            case "ECHO":
                res = commandHandler.echo(command);
                break;
            case "EXEC":
                res = "-ERR EXEC WITHOUT MULTI\r\n";
                break;
            case "DISCARD":
                res = "-ERR DISCARD WITHOUT MULTI\r\n";
                break;
            case "MULTI":
                client.beginTransaction();
                res = "+OK\r\n";
                break;
            case "SET":
                res = commandHandler.set(command);
                // trickle down to slave
                String respArr = respSerializer.respArray(command);
                byte[] bytes = respArr.getBytes();
                connectionPool.bytesSentToSlaves+= bytes.length;
                CompletableFuture.runAsync(() -> propagate(command));
                break;
            case "GET":
                res = commandHandler.get(command);
                break;
            case "INFO":
                res = commandHandler.info(command);
                break;
            case "REPLCONF":
                res = commandHandler.repelconf(command, client);
                break;
            case "PSYNC":
                ResponseDto resDto = commandHandler.psync(command);
                res = resDto.getResponse();
                data = resDto.getData();
                break;
            case "WAIT":
                if(connectionPool.bytesSentToSlaves == 0){
                    res = respSerializer.respInteger(connectionPool.slavesThatAreCaughtUp);
                    break;
                }
                Instant now = Instant.now();
                res = commandHandler.wait(command,now);
                connectionPool.slavesThatAreCaughtUp = 0;
                break;
        }
        return new ResponseDto(res, data);
    }

    private void propagate(String[] command) {
        String commandRespString = respSerializer.respArray(command);
        try {
            for(Slave s: connectionPool.getSlaves()){
                s.send(commandRespString.getBytes());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
