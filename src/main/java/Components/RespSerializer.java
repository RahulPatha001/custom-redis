package Components;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class RespSerializer {

    public int getParts(char []dataArr, int i, String[] subArray){
        int j=0;
        while(i< dataArr.length && j < subArray.length){
            if(dataArr[i] == '$'){
                //bulk String
                //$<length>\r\n<data>\r\n
                i++;
                String partLength = "";
                while(i < dataArr.length && Character.isDigit(dataArr[i])){
                    partLength += dataArr[i];
                    i++;
                }
                i+=2;
                String part = "";
                for(int k=0; k<Integer.parseInt(partLength);k++){
                    part+=dataArr[i++];
                }
                i+=2;
                subArray[j++]=part;
            }
        }
        return i;
    }

    public List<String[]> deserialize(byte[] command){
        String data = new String(command, StandardCharsets.UTF_8);
        char[] dataArr = data.toCharArray();

        List<String[]> list = new ArrayList<>();

        int i = 0;
        while (i<dataArr.length){
            char cur = dataArr[i];

            if(cur == '\u0000'){
                break;
            }

            if(cur == '*'){

                String arrLen = "";
                i++;
                while (i< dataArr.length && Character.isDigit(dataArr[i])){
                    arrLen += dataArr[i++];
                }
                i += 2;
                if(dataArr[i] == '*'){
                    for(int t = 0; t< Integer.parseInt(arrLen); t++){
                        String nextedLen = "";
                        i++;
                        while (i< dataArr.length && Character.isDigit(dataArr[i])){
                            nextedLen += dataArr[i++];
                        }
                        i+=2;
                        String[] subArr = new String[Integer.parseInt(nextedLen)];
                        i = getParts(dataArr, i, subArr);
                        list.add(subArr);

                    }
                }else {
                    String[] subArr = new String[Integer.parseInt(arrLen)];
                    i = getParts(dataArr, i, subArr);
                    list.add(subArr);
                }
            }
        }

        return list;
    }
}
