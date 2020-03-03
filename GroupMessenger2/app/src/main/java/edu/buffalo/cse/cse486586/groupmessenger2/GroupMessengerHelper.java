package edu.buffalo.cse.cse486586.groupmessenger2;

import java.util.HashMap;

public class GroupMessengerHelper {

    public HashMap<Integer,Integer> fillHashMap(HashMap<Integer,Integer> hashMap,String[] remotePorts)
    {
        for(String ports:remotePorts)
        {
            hashMap.put(Integer.parseInt(ports),0);
        }
        return hashMap;
    }

    public int calculateMax(int firstNumber,int secondNumber)
    {
        return (firstNumber>secondNumber)?firstNumber:secondNumber;
    }
}
