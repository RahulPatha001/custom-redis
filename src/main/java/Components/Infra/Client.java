package Components.Infra;

import Components.Service.ResponseDto;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class Client {
    public Socket socket;
    public InputStream inputStream;
    public OutputStream outputStream;
    public int id;
    private boolean transactionalContext;
    public Queue<String[]> commandQueue;
    public List<String> transactionalResponse; // strings will be in resp format we have to make resp array out of it

    public boolean getTransactionalContext() {
        return transactionalContext;
    }

    public boolean beginTransaction(){
        if(transactionalContext){
            return false;
        }
        transactionalContext = true;
        transactionalResponse = new ArrayList<>();
        commandQueue = new LinkedList<>();
        return transactionalContext;
    }

    public void endTransaction(){
        commandQueue = null;
        transactionalContext = false;
    }

    public void setTransactionalContext(boolean transactionalContext) {
        this.transactionalContext = transactionalContext;
    }



    public Client(Socket socket, InputStream inputStream,OutputStream outputStream, int id){
        this.socket =  socket;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.id = id;
    }

    public void send(String res, byte[] data) throws IOException {
        if(res != null && !res.equals(""))
            outputStream.write(res.getBytes());
        if(data != null){
            outputStream.write(data);
        }
    }

    public void send(ResponseDto res) throws IOException {
        if(res.getResponse() != null && !res.getResponse().isEmpty())
            outputStream.write(res.getResponse().getBytes());
        if(res.getData() != null){
            outputStream.write(res.getData());
        }
    }

    public void send( byte[] data) throws IOException {
        if(data != null){
            outputStream.write(data);
        }
    }

    public void send(String data) throws IOException {
        if (data != null && !data.isEmpty()) {
            outputStream.write(data.getBytes());
        }
    }
}
