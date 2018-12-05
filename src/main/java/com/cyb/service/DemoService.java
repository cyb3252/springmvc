package com.cyb.service;

import com.cyb.annotation.Service;

import java.net.URL;

/**
 * @author cyb
 * @date 2018/12/2 - 21:28
 */
@Service(value = "demoService")
public class DemoService {

    public void sayHello(){
        System.out.println("DemoService.....");
        String path = this.getClass().getClassLoader().getResource(".").getPath();
        System.out.println(path);
    }

    public static void main (String[] args) {
        new DemoService().sayHello();


    }
}
