package com.cyb.servlet;

import com.cyb.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author cyb
 * @date 2018/12/2 - 20:07
 */
@WebServlet(name = "dispatcherServlet", urlPatterns = "/*", loadOnStartup = 1, initParams = {
        @WebInitParam(name = "base-package", value = "com.cyb")
})
public class DispatcherServlet extends HttpServlet {

    private int age=1;
    //扫描基类的包
    private String basePackage = "";
    //扫描的包下所有的带包路径的全限定名称
    private List<String> packageNames = new ArrayList<>();
    //实例存放的map
    private Map<String, Object> instanceMap = new HashMap<>();
    //bean的名称
    private Map<String, String> nameMap = new HashMap<>();
    //url所对应执行的方法
    private Map<String, Method> urlMethodMap = new HashMap<>();
    //key = method value=类全限定名
    private Map<Method, String> methodStringMap = new HashMap<>();

    @Override
    protected void doGet (HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost (HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String uri = req.getRequestURI();
        String contextPath = req.getContextPath();
        String path = uri.replace(contextPath, "");

        Method method = urlMethodMap.get(path);
        if (method!=null){
            String packageName = methodStringMap.get(method);
            String controllerName = nameMap.get(packageName);
            method.setAccessible(true);

            try {
                Object result = method.invoke(instanceMap.get(controllerName));
                resp.setContentType("utf-8");
                PrintWriter writer = resp.getWriter();
                writer.println(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void init (ServletConfig config) throws ServletException {
        //获取包路径
        basePackage = config.getInitParameter("base-package");
        System.out.println(basePackage);
        //扫描包路径下所有类的全限定名
        doScanBasePackage(basePackage);
        //处理类上有controller repository service
        doInstance(packageNames);
        //bean注入
        doAutowired();
        //获取controller，执行controller的某个方法
        doHandlerUrlMapping();
    }

    private void doHandlerUrlMapping () {
        if (packageNames.size() < 1) {
            return;
        }
        for (String packageName : packageNames) {
            try {
                Class<?> c = Class.forName(packageName);
                if (c.isAnnotationPresent(Controller.class)) {
                    //该类所有的方法
                    Method[] methods = c.getMethods();
                    StringBuilder baseUrl = new StringBuilder();
                    //在检测方法上是否标注了requestMapping
                    if (c.isAnnotationPresent(RequestMapping.class)){
                        RequestMapping requestMapping = c.getAnnotation(RequestMapping.class);
                        baseUrl.append(requestMapping.value());
                    }

                    //遍历该类的所有方法，如果方法上有requestmapping注解就拼装成一个url 最后把url和方法放在一个map中
                    for (Method method : methods) {
                        if (method.isAnnotationPresent(RequestMapping.class)){
                            RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                            baseUrl = baseUrl.append(requestMapping.value());

                            urlMethodMap.put(baseUrl.toString(),method);
                            methodStringMap.put(method,packageName);
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        System.out.println("===============methodStringMap===============");
        System.out.println(urlMethodMap);
        System.out.println("===============methodStringMap===============");
        System.out.println(methodStringMap);
    }

    /**
     * 注入
     */
    private void doAutowired () {
        for (Map.Entry<String, Object> entry : instanceMap.entrySet()) {
            //拿到标注了autowired的类
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    String autowiredName = field.getAnnotation(Autowired.class).value();
                    //设置反射可以为私有字段赋值
                    field.setAccessible(true);
                    try {
                        field.set(entry.getValue(), instanceMap.get(autowiredName));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    /**
     * 实例化
     *
     * @param packageNames 实例化的类全限定名的集合
     */
    private void doInstance (List<String> packageNames) {
        //没有需要实例化的类直接跳出
        if (packageNames.size() < 1) {
            return;
        }
        for (String packageName : packageNames) {
            try {
                Class<?> c = Class.forName(packageName);
                //类上注解controller
                if (c.isAnnotationPresent(Controller.class)) {
                    //获取注解
                    Controller controller = c.getAnnotation(Controller.class);
                    //获取@Controller 中value的值
                    String controllerName = controller.value();
                    if (controllerName != null && !controllerName.trim().equals("")) {
                        instanceMap.put(controllerName, c.newInstance());
                        nameMap.put(packageName, controllerName);
                        System.out.println("Controller" + controllerName + ",value" + controller.value() == null ? "" : controller.value());
                    }
                } else if (c.isAnnotationPresent(Service.class)) {
                    Service service = c.getAnnotation(Service.class);
                    String serviceName = service.value();
                    if (serviceName != null && !serviceName.trim().equals("")) {
                        instanceMap.put(serviceName, c.newInstance());
                        nameMap.put(packageName, serviceName);
                        System.out.println("Service=" + serviceName + ",value=" + service.value());
                    }
                } else if (c.isAnnotationPresent(Repository.class)) {
                    Repository repository = c.getAnnotation(Repository.class);
                    String repositoryName = repository.value();
                    if (repositoryName != null && !repositoryName.trim().equals("")) {
                        instanceMap.put(repositoryName, c.newInstance());
                        nameMap.put(packageName, repositoryName);
                        System.out.println("Repository" + repositoryName + ",value" + repository.value());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * 扫描包
     *
     * @param basePackage 包名
     */
    private void doScanBasePackage (String basePackage) {
        //由于在idea的情况下 这种方式并不能拿到当前项目的路径
        /*URL url = this.getClass().getClassLoader().getResource("/" + basePackage.replaceAll("\\.", "/"));
        String path = url.getPath();*/
        //模拟得到的路径
        String path = "D:\\IdeaProjects\\springmvc\\src\\main\\java\\"+basePackage.replaceAll("\\.","/");
        File basePackageFile = new File(path);
        File[] childFile = basePackageFile.listFiles();
        for (File file : childFile) {
            //如果得到的文件是目录的话
            if (file.isDirectory()) {
                //递归处理
                doScanBasePackage(basePackage + "." + file.getName());
            } else if (file.isFile()) {
                packageNames.add(basePackage + "." + file.getName().split("\\.")[0]);
            }
        }
    }
}
