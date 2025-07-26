import Components.Server.RedisConfig;
import Components.Server.MasterTCPServer;
import Components.Server.SlaveTCPServer;
import Config.AppConfig;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;


public class Main {
  public static void main(String[] args){
    // You can use print statements as follows for debugging, they'll be visible when running tests.
    System.out.println("Logs from your program will appear here!");
    AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(AppConfig.class);
    MasterTCPServer master = context.getBean(MasterTCPServer.class);
    SlaveTCPServer slave = context.getBean(SlaveTCPServer.class);
    RedisConfig redisConfig = context.getBean(RedisConfig.class);

    int port = 6379;
    redisConfig.setPort(port);
    redisConfig.setRole("master");
    for(int i = 0; i< args.length; i++){

      switch (args[i]){
        case "--port":
          port = Integer.parseInt(args[i+1]);
          redisConfig.setPort(port);
          break;

        case "--replicaof":
          redisConfig.setRole("slave");
//            --replicaof "<MASTER_HOST> <MASTER_PORT>"
          String masterHost = args[i+1].split(" ")[0];
          int masterPort = Integer.parseInt(args[i+1].split(" ")[1]);
          redisConfig.setMasterHost(masterHost);
          redisConfig.setMasterPort(masterPort);
          break;
        case "--dir": // Add this block
          redisConfig.setDir(args[i+1]);
          break;
        case "--dbfilename": // Add this block
          redisConfig.setDbfilename(args[i+1]);
          break;
      }

    }


    if(redisConfig.getRole().equals("slave")){
      slave.startWorking();
    }else {
      master.startWorking();
    }


  }

}