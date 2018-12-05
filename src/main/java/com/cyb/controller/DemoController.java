package com.cyb.controller;

import com.cyb.annotation.Autowired;
import com.cyb.annotation.Controller;
import com.cyb.annotation.RequestMapping;
import com.cyb.service.DemoService;

/**
 * @author cyb
 * @date 2018/12/2 - 21:24
 */
@Controller(value = "demoController")
public class DemoController {

    @Autowired(value = "demoService")
    private DemoService demoService;

    @RequestMapping(value = "/demo")
    public String hello(){
        System.out.println("demoController..");
        demoService.sayHello();
        return "invoke sucess";
    }
}
