package org.largecollections;

import static org.junit.Assert.*;
import static org.largecollections.TestUtils.*;

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.Test;

import utils.Utils;

public class ListDerializationTest {
    
    
    @Test
    public void test01List() throws Exception{    
        System.setProperty("java.io.tmpdir","c:/tmp");        
        TestUtils.max=1000;        
        WriteOnceReadManyArrayList<String> l = (WriteOnceReadManyArrayList<String>)Utils.deserialize(new File("c:/tmp/lst.ser"));
        Iterator<String> ls = l.iterator();
        while(ls.hasNext()){
            System.out.println(ls.next());
        }
        Utils.serialize(l,new File("c:/tmp/lst2.ser"));
        
    }  
}